package com.elsewhere.location.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elsewhere.location.AppContainer
import com.elsewhere.location.BuildConfig
import com.elsewhere.location.ElsewhereApplication
import com.elsewhere.location.core.AppSettings
import com.elsewhere.location.core.CacheBackupRecord
import com.elsewhere.location.core.DriftConfig
import com.elsewhere.location.core.EnvironmentProfile
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.GeocodeResult
import com.elsewhere.location.core.MapLayer
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.RoutePlan
import com.elsewhere.location.core.RouteRecorder
import com.elsewhere.location.core.SavedPlace
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.core.SessionStartConfig
import com.elsewhere.location.core.SpeedSettings
import com.elsewhere.location.data.TargetAppInfo
import com.elsewhere.location.data.TargetAppRepository
import com.elsewhere.location.service.MockLocationService
import com.elsewhere.location.shizuku.ShizukuManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID

class ElsewhereViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val container: AppContainer =
        (application as ElsewhereApplication).container
    private val settingsRepository = container.settingsRepository
    private val shizuku = container.shizukuManager
    private val geocoder = container.geocodingRepository
    private val realLocation = container.realLocationProbe
    private val updateRepository = container.updateRepository
    private val targetApps = container.targetAppRepository
    private val cacheRepository = container.cacheBackupRepository
    private val diagnosticsRepository = container.diagnosticsRepository
    private val fridaRepository = container.fridaSensorRepository

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(updateRepo = BuildConfig.UPDATE_REPO),
    )
    val shizukuStatus = shizuku.status
    val session = MockLocationService.state

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private val _mapUi = MutableStateFlow(MapUiState())
    val mapUi: StateFlow<MapUiState> = _mapUi.asStateFlow()

    private val _routeRecorderUi = MutableStateFlow(RouteRecorderUiState())
    val routeRecorderUi: StateFlow<RouteRecorderUiState> = _routeRecorderUi.asStateFlow()
    private var activeRouteRecorder: RouteRecorder? = null
    private var routeRecordingJob: Job? = null

    private val _permissions = MutableStateFlow(PermissionUiState())
    val permissions: StateFlow<PermissionUiState> = _permissions.asStateFlow()

    private val _appsUi = MutableStateFlow(AppsUiState())
    val appsUi: StateFlow<AppsUiState> = _appsUi.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsUiState())
    val diagnostics: StateFlow<DiagnosticsUiState> = _diagnostics.asStateFlow()

    private val _updateUi = MutableStateFlow(UpdateUiState())
    val updateUi: StateFlow<UpdateUiState> = _updateUi.asStateFlow()

    private val _fridaUi = MutableStateFlow(FridaUiState())
    val fridaUi: StateFlow<FridaUiState> = _fridaUi.asStateFlow()

    private val _mapCommands = MutableSharedFlow<MapCommand>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val mapCommands = _mapCommands.asSharedFlow()

    private val _events = MutableSharedFlow<UiEvent>(
        extraBufferCapacity = 24,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    private val reverseCache = object : LinkedHashMap<String, com.elsewhere.location.core.ReverseGeocodeResult>(
        32,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, com.elsewhere.location.core.ReverseGeocodeResult>?,
        ): Boolean = size > 48
    }

    private var reverseJob: Job? = null
    private var searchJob: Job? = null
    private var appsLoadJob: Job? = null
    private var appsScanJob: Job? = null
    private var cacheSizeJob: Job? = null

    private var hydrated = false
    private var pendingAutoConfig = false
    private var pendingOverlayRequest = false
    private var pendingStart: StartRequest? = null
    private var pendingAction: PendingAfterLocation? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                _settingsLoaded.value = true
                _mapUi.update { it.copy(recentPlaces = current.recentPlaces) }
                if (!hydrated) {
                    hydrated = true
                    _mapUi.update {
                        it.copy(
                            center = current.lastPoint,
                            addressTitle = current.lastPointLabel.ifBlank { "上次使用的位置" },
                            addressSubtitle = "当前准星坐标",
                            routeMode = current.motionMode,
                        )
                    }
                    _mapCommands.tryEmit(
                        MapCommand.MoveTo(current.lastPoint, zoom = null, animate = false),
                    )
                    _fridaUi.update {
                        if (it.targetPackage.isBlank()) {
                            it.copy(targetPackage = current.fridaTargetPackage)
                        } else {
                            it
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            shizuku.status.collect { status ->
                if (pendingAutoConfig && status.permissionGranted) {
                    pendingAutoConfig = false
                    grantCorePermissionsViaShizuku()
                }
            }
        }
        refreshPermissions()
    }

    fun requestRuntimePermissions() {
        _events.tryEmit(UiEvent.RequestRuntimePermissions)
    }

    fun requestLocationPermission() {
        _events.tryEmit(UiEvent.RequestLocationPermission)
    }

    fun requestNotificationPermission() {
        _events.tryEmit(UiEvent.RequestNotificationPermission)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        refreshPermissions()
        if (!granted) {
            pendingStart = null
            pendingAction = null
            notify("需要精确位置权限才能读取真实 GPS 或保持前台模拟服务。")
            return
        }
        val action = pendingAction
        pendingAction = null
        val start = pendingStart
        pendingStart = null
        when (action) {
            PendingAfterLocation.LocateReal -> locateRealInternal()
            PendingAfterLocation.Start -> start?.let { launchStart(it) }
            null -> start?.let { launchStart(it) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        refreshPermissions()
        if (!granted) {
            notify("通知权限被关闭；模拟仍可运行，但常驻通知可能不会显示。")
        }
    }

    @SuppressLint("NewApi")
    fun refreshPermissions() {
        val context = app
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _permissions.value = PermissionUiState(
            fineLocation = fine,
            notifications = notifications,
            overlay = shizuku.canDrawOverlays(),
            mockLocation = shizuku.isMockLocationAllowed(),
        )
    }

    fun onActivityResume() {
        shizuku.refresh()
        refreshPermissions()
        if (pendingOverlayRequest && Settings.canDrawOverlays(app)) {
            pendingOverlayRequest = false
            if (session.value.running) {
                MockLocationService.showOverlay(app)
            }
        }
    }

    fun onActivityPause() {
        val ui = _mapUi.value
        if (ui.center.isValid && !ui.routeEditing) {
            mutateSettings {
                it.copy(
                    lastPoint = ui.center,
                    lastPointLabel = ui.addressTitle.takeIf { title ->
                        title.isNotBlank() && title != "正在获取地址…"
                    } ?: it.lastPointLabel,
                )
            }
        }
    }

    fun requestShizukuAuthorization() {
        shizuku.requestPermission()
        notify("正在向 Shizuku 请求授权…")
    }

    fun openShizukuApp() {
        _events.tryEmit(UiEvent.OpenShizukuApp)
    }

    fun autoConfigureWithShizuku() {
        shizuku.refresh()
        val status = shizukuStatus.value
        if (!status.binderAlive) {
            _events.tryEmit(UiEvent.OpenUrl(ShizukuManager.SHIZUKU_RELEASES_URL))
            notify("请先安装并启动 Shizuku 服务，再回来授权。")
            return
        }
        if (!status.permissionGranted) {
            pendingAutoConfig = true
            shizuku.requestPermission()
            notify("授权完成后将自动配置模拟位置与悬浮窗权限。")
            return
        }
        viewModelScope.launch { grantCorePermissionsViaShizuku() }
    }

    private suspend fun grantCorePermissionsViaShizuku() {
        val mock = shizuku.grantMockLocation()
        val overlay = shizuku.grantOverlay()
        refreshPermissions()
        val summary = buildString {
            append(mock.message)
            if (overlay.message.isNotBlank()) append("\n").append(overlay.message)
        }
        _events.emit(UiEvent.Message(summary))
    }

    fun grantMockLocationPermission() {
        viewModelScope.launch {
            if (!shizuku.status.value.ready) {
                notify("请先完成 Shizuku 授权，或手动到开发者选项中选择模拟位置应用。")
                _events.emit(UiEvent.OpenDeveloperOptions)
                return@launch
            }
            val result = shizuku.grantMockLocation()
            refreshPermissions()
            notify(result.message)
            if (!result.success) {
                _events.emit(UiEvent.OpenDeveloperOptions)
            }
        }
    }

    fun requestOverlayPermission() {
        pendingOverlayRequest = true
        if (_permissions.value.overlay) {
            if (session.value.running) MockLocationService.showOverlay(app)
            pendingOverlayRequest = false
            return
        }
        if (shizuku.status.value.ready) {
            viewModelScope.launch {
                val result = shizuku.grantOverlay()
                refreshPermissions()
                if (result.success) {
                    if (session.value.running) MockLocationService.showOverlay(app)
                    pendingOverlayRequest = false
                    notify("悬浮窗权限已开启。")
                } else {
                    notify(result.message)
                    _events.emit(UiEvent.OpenOverlaySettings)
                }
            }
        } else {
            _events.tryEmit(UiEvent.OpenOverlaySettings)
        }
    }

    fun showOverlayNow() {
        if (!session.value.running) {
            notify("先开始模拟，再打开悬浮摇杆。")
            return
        }
        if (!_permissions.value.overlay) {
            requestOverlayPermission()
            return
        }
        mutateSettings { it.copy(overlayEnabled = true) }
        MockLocationService.showOverlay(app)
    }

    fun hideOverlayNow() {
        mutateSettings { it.copy(overlayEnabled = false) }
        MockLocationService.hideOverlay(app)
        if (session.value.running) notify("悬浮摇杆已收起。")
    }

    fun setOverlayPreference(enabled: Boolean) {
        mutateSettings { it.copy(overlayEnabled = enabled) }
        if (!enabled) {
            pendingOverlayRequest = false
            MockLocationService.hideOverlay(app)
        } else {
            requestOverlayPermission()
        }
    }

    // ---------------------------------------------------------------- map state

    fun onMapCenterChanged(point: GeoPoint) {
        if (!point.isValid) return
        val previous = _mapUi.value.center
        if (Geo.distanceMeters(previous, point) < 0.35) return
        _mapUi.update {
            it.copy(
                center = point,
                reverseLoading = !it.routeEditing,
            )
        }
        scheduleReverse(point, immediate = false)
    }

    fun openSearch() {
        _mapUi.update {
            it.copy(
                searchOpen = true,
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null,
            )
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _mapUi.update { it.copy(searchOpen = false, searching = false) }
    }

    fun setSearchQuery(query: String) {
        _mapUi.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _mapUi.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(360)
            performSearch(query)
        }
    }

    fun submitSearch() {
        val query = _mapUi.value.searchQuery
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        _mapUi.update { it.copy(searching = true, searchError = null) }
        val key = settings.value.amapWebKey
        val result = geocoder.search(query, amapKey = key)
        if (_mapUi.value.searchQuery != query) return
        result.onSuccess { places ->
            _mapUi.update {
                it.copy(
                    searching = false,
                    searchResults = places,
                    searchError = if (places.isEmpty()) "没有找到匹配的位置。" else null,
                )
            }
        }.onFailure { error ->
            _mapUi.update {
                it.copy(
                    searching = false,
                    searchResults = emptyList(),
                    searchError = error.message ?: "地址搜索失败。",
                )
            }
        }
    }

    fun selectSearchResult(result: GeocodeResult) {
        searchJob?.cancel()
        _mapUi.update {
            it.copy(
                searchOpen = false,
                searchQuery = result.title,
                searchResults = emptyList(),
                searchError = null,
                center = result.point,
                addressTitle = result.title,
                addressSubtitle = result.subtitle,
                pendingRealPoint = null,
            )
        }
        _mapCommands.tryEmit(MapCommand.MoveTo(result.point, zoom = 16.5))
        addRecentPlace(
            SavedPlace(
                label = result.title,
                detail = result.subtitle,
                latitude = result.point.latitude,
                longitude = result.point.longitude,
            ),
        )
        scheduleReverse(result.point, immediate = true)
    }

    fun selectRecentPlace(place: SavedPlace) {
        _mapUi.update {
            it.copy(
                searchOpen = false,
                center = place.point,
                addressTitle = place.label,
                addressSubtitle = place.detail,
                pendingRealPoint = null,
            )
        }
        _mapCommands.tryEmit(MapCommand.MoveTo(place.point, zoom = 16.5))
        scheduleReverse(place.point, immediate = true)
    }

    fun clearRecentPlaces() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(recentPlaces = emptyList()) }
        }
    }

    private fun addRecentPlace(place: SavedPlace) {
        viewModelScope.launch {
            settingsRepository.update { current ->
                val deduped = current.recentPlaces.filterNot {
                    it.label == place.label ||
                        (Geo.distanceMeters(it.point, place.point) < 8.0 && it.label == place.label)
                }
                current.copy(recentPlaces = (listOf(place) + deduped).take(8))
            }
        }
    }

    fun locateRealPosition() {
        if (session.value.running) {
            notify("模拟定位正在运行。请先停止模拟，再读取真实 GPS。")
            return
        }
        if (!_permissions.value.fineLocation) {
            pendingStart = null
            pendingAction = PendingAfterLocation.LocateReal
            requestLocationPermission()
            return
        }
        locateRealInternal()
    }

    private fun locateRealInternal() {
        if (_mapUi.value.locatingReal) return
        _mapUi.update { it.copy(locatingReal = true) }
        viewModelScope.launch {
            val result = realLocation.getCurrentLocation()
            _mapUi.update { it.copy(locatingReal = false) }
            result.onSuccess { fix ->
                val point = fix.point
                _mapUi.update {
                    it.copy(
                        center = point,
                        pendingRealPoint = point,
                        pendingRealTitle = if (fix.isCached) "最近一次 GPS 定位" else "真实 GPS 坐标",
                        pendingRealSubtitle = if (fix.isCached) {
                            "实时 GPS 暂不可用，已使用系统缓存位置；确认后再应用。"
                        } else {
                            "已把准星移动到当前真实位置，确认后再应用。"
                        },
                        pendingRealTitleLoading = true,
                        pendingRealIsCached = fix.isCached,
                        pendingRealAccuracyMeters = fix.accuracyMeters,
                        pendingRealAgeSeconds = fix.ageMillis / 1_000L,
                    )
                }
                _mapCommands.tryEmit(MapCommand.MoveTo(point, zoom = 17.0))
                scheduleReverse(point, immediate = true)
            }.onFailure { error ->
                notify(error.message ?: "读取真实 GPS 失败。")
            }
        }
    }

    fun applyPendingRealPoint() {
        val point = _mapUi.value.pendingRealPoint ?: return
        val title = _mapUi.value.pendingRealTitle.ifBlank { Geo.formatDecimal(point.latitude, point.longitude, 5) }
        viewModelScope.launch {
            settingsRepository.update {
                it.copy(
                    lastPoint = point,
                    lastPointLabel = title,
                )
            }
            _mapUi.update {
                it.copy(
                    center = point,
                    addressTitle = title,
                    addressSubtitle = _mapUi.value.pendingRealSubtitle,
                    pendingRealPoint = null,
                )
            }
            notify("已应用真实位置坐标。")
        }
    }

    fun cancelPendingRealPoint() {
        _mapUi.update {
            it.copy(
                pendingRealPoint = null,
                pendingRealTitle = "",
                pendingRealSubtitle = "",
                pendingRealTitleLoading = false,
                pendingRealIsCached = false,
                pendingRealAccuracyMeters = null,
                pendingRealAgeSeconds = 0L,
            )
        }
    }

    private fun scheduleReverse(
        point: GeoPoint,
        immediate: Boolean = false,
    ) {
        reverseJob?.cancel()
        val key = "%.5f,%.5f".format(point.latitude, point.longitude)
        _mapUi.update { it.copy(reverseLoading = true) }
        reverseJob = viewModelScope.launch {
            if (!immediate) delay(600)
            val cached = reverseCache[key]
            val result = cached ?: geocoder.reverse(point, settings.value.amapWebKey).also {
                reverseCache[key] = it
            }
            val ui = _mapUi.value
            if (Geo.distanceMeters(ui.center, point) > 120.0) {
                _mapUi.update { it.copy(reverseLoading = false) }
                return@launch
            }
            _mapUi.update {
                val matchesPendingReal = it.pendingRealPoint?.let { pending ->
                    Geo.distanceMeters(pending, point) < 12.0
                } == true
                it.copy(
                    reverseLoading = false,
                    addressTitle = result.title,
                    addressSubtitle = result.subtitle,
                    pendingRealTitle = if (matchesPendingReal) result.title else it.pendingRealTitle,
                    pendingRealSubtitle = if (matchesPendingReal) result.subtitle else it.pendingRealSubtitle,
                    pendingRealTitleLoading = if (matchesPendingReal) false else it.pendingRealTitleLoading,
                )
            }
        }
    }

    // -------------------------------------------------------------- permissions

    private suspend fun ensureMockLocationPermission(): Boolean {
        refreshPermissions()
        if (_permissions.value.mockLocation) return true
        if (!shizuku.status.value.ready) {
            _events.emit(UiEvent.OpenDeveloperOptions)
            _events.emit(
                UiEvent.Message(
                    "尚未允许模拟位置。请在开发者选项 → 选择模拟位置信息应用中选择“他乡”。",
                ),
            )
            return false
        }
        val result = shizuku.grantMockLocation()
        refreshPermissions()
        if (!result.success) {
            _events.emit(UiEvent.Message(result.message))
            _events.emit(UiEvent.OpenDeveloperOptions)
            return false
        }
        return true
    }

    // ------------------------------------------------------------------ session

    fun requestStart(route: RoutePlan? = null) {
        if (session.value.isActive) {
            notify("模拟已经在运行。")
            return
        }
        val point = route?.points?.firstOrNull() ?: _mapUi.value.center
        if (!point.isValid) {
            notify("当前准星坐标无效。")
            return
        }
        if (route != null && route.points.size < 2) {
            notify("路线至少需要两个航点。")
            return
        }
        val request = StartRequest(point = point, route = route)
        if (!_permissions.value.fineLocation) {
            pendingStart = request
            pendingAction = PendingAfterLocation.Start
            _events.tryEmit(UiEvent.RequestLocationPermission)
            return
        }
        launchStart(request)
    }

    private fun launchStart(request: StartRequest) {
        if (_mapUi.value.preparingStart) return
        _mapUi.update { it.copy(preparingStart = true) }
        viewModelScope.launch {
            try {
                if (!ensureMockLocationPermission()) return@launch
                val current = settingsRepository.current()
                val mode = request.route?.mode ?: current.motionMode
                val speed = current.speeds.forMode(mode)
                val point = request.route?.points?.firstOrNull() ?: request.point
                val canOverlay = Settings.canDrawOverlays(app)
                val config = SessionStartConfig(
                    point = point,
                    mode = mode,
                    baseSpeedMps = speed,
                    speedSettings = current.speeds,
                    overlayEnabled = current.overlayEnabled && canOverlay,
                    drift = current.drift,
                    environment = current.environment,
                    sensors = current.sensors,
                    route = request.route,
                )
                val label = request.route?.let { "路线 · ${it.name}" }
                    ?: _mapUi.value.addressTitle.ifBlank { Geo.formatDecimal(point.latitude, point.longitude, 4) }
                settingsRepository.update {
                    it.copy(
                        lastPoint = point,
                        lastPointLabel = label,
                        motionMode = mode,
                    )
                }
                _mapUi.update {
                    it.copy(
                        pendingRealPoint = null,
                        center = point,
                    )
                }
                _mapCommands.tryEmit(MapCommand.MoveTo(point, zoom = null, animate = true))
                MockLocationService.start(app, config)
                if (current.overlayEnabled && !canOverlay) {
                    notify("模拟已启动。悬浮摇杆还需要开启“显示在其他应用上层”权限。")
                }
            } finally {
                _mapUi.update { it.copy(preparingStart = false) }
            }
        }
    }

    fun stopSession() {
        MockLocationService.stop(app)
    }

    fun setJoystick(x: Float, y: Float) {
        MockLocationService.setJoystick(x, y)
    }

    fun releaseJoystick() {
        MockLocationService.releaseJoystick()
    }

    fun setMotionMode(mode: MotionMode) {
        val current = settings.value
        val speed = current.speeds.forMode(mode)
        mutateSettings { it.copy(motionMode = mode) }
        if (session.value.running) {
            MockLocationService.setMode(app, mode, speed)
        }
    }

    fun setSpeedForCurrentMode(speed: Float) {
        val mode = settings.value.motionMode
        mutateSettings { it.copy(speeds = it.speeds.withModeSpeed(mode, speed)) }
        if (session.value.running && session.value.mode == mode) {
            MockLocationService.setSpeed(app, speed)
        }
    }

    fun adjustSpeed(delta: Float) {
        val current = settings.value
        val mode = current.motionMode
        val speed = (current.speeds.forMode(mode) + delta).coerceIn(mode.speedRange)
        setSpeedForCurrentMode(speed)
    }

    fun setModeSpeed(mode: MotionMode, speed: Float) {
        mutateSettings { it.copy(speeds = it.speeds.withModeSpeed(mode, speed)) }
        if (session.value.running && session.value.mode == mode) {
            MockLocationService.setSpeed(app, speed)
        }
    }

    // --------------------------------------------------------------- route plan

    fun beginRouteDraft(route: RoutePlan? = null) {
        val name = route?.name ?: "路线 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}"
        _mapUi.update {
            it.copy(
                routeEditing = true,
                routeDraft = route?.points.orEmpty(),
                routeName = name,
                routeLoop = route?.loop ?: false,
                routeMode = route?.mode ?: it.routeMode,
                routeError = null,
                searchOpen = false,
                pendingRealPoint = null,
            )
        }
        route?.points?.let { points ->
            if (points.size >= 2) _mapCommands.tryEmit(MapCommand.FitRoute(points))
        }
    }

    fun updateRouteName(name: String) {
        _mapUi.update { it.copy(routeName = name, routeError = null) }
    }

    fun setRouteMode(mode: MotionMode) {
        _mapUi.update { it.copy(routeMode = mode) }
    }

    fun setRouteLoop(loop: Boolean) {
        _mapUi.update { it.copy(routeLoop = loop) }
    }

    fun addRoutePoint(point: GeoPoint) {
        if (!point.isValid) return
        val ui = _mapUi.value
        if (!ui.routeEditing) return
        val last = ui.routeDraft.lastOrNull()
        if (last != null && Geo.distanceMeters(last, point) < 3.0) {
            _mapUi.update { it.copy(routeError = "航点距离太近，请拖动准星后再添加。") }
            return
        }
        if (ui.routeDraft.size >= 64) {
            _mapUi.update { it.copy(routeError = "单条路线最多 64 个航点。") }
            return
        }
        val updated = ui.routeDraft + point
        _mapUi.update { it.copy(routeDraft = updated, routeError = null) }
        if (updated.size >= 2) _mapCommands.tryEmit(MapCommand.FitRoute(updated))
    }

    fun addCurrentCenterAsRoutePoint() {
        addRoutePoint(_mapUi.value.center)
    }

    fun removeLastRoutePoint() {
        val ui = _mapUi.value
        if (ui.routeDraft.isEmpty()) return
        val updated = ui.routeDraft.dropLast(1)
        _mapUi.update { it.copy(routeDraft = updated) }
        if (updated.size >= 2) _mapCommands.tryEmit(MapCommand.FitRoute(updated))
    }

    fun removeRoutePoint(index: Int) {
        val ui = _mapUi.value
        if (index !in ui.routeDraft.indices) return
        val updated = ui.routeDraft.toMutableList().also { it.removeAt(index) }
        _mapUi.update { it.copy(routeDraft = updated) }
        if (updated.size >= 2) _mapCommands.tryEmit(MapCommand.FitRoute(updated))
    }

    fun cancelRouteEditing() {
        _mapUi.update {
            it.copy(
                routeEditing = false,
                routeDraft = emptyList(),
                routeName = "",
                routeError = null,
            )
        }
    }

    fun saveRoute(startImmediately: Boolean = false) {
        val ui = _mapUi.value
        if (ui.routeDraft.size < 2) {
            _mapUi.update { it.copy(routeError = "至少添加两个航点才能保存路线。") }
            return
        }
        viewModelScope.launch {
            val route = RoutePlan(
                id = UUID.randomUUID().toString(),
                name = ui.routeName.trim().ifBlank { "未命名路线" },
                points = ui.routeDraft,
                loop = ui.routeLoop,
                mode = ui.routeMode,
            )
            settingsRepository.update {
                it.copy(
                    routes = (it.routes + route).takeLast(30),
                    lastPoint = route.points.first(),
                    lastPointLabel = "路线 · ${route.name}",
                )
            }
            _mapUi.update {
                it.copy(
                    routeEditing = false,
                    routeDraft = emptyList(),
                    routeName = "",
                    routeLoop = false,
                    routeError = null,
                    center = route.points.first(),
                    addressTitle = "路线 · ${route.name}",
                    addressSubtitle = "${route.points.size} 个航点 / ${Geo.formatDistance(route.distanceMeters)}",
                )
            }
            _mapCommands.tryEmit(MapCommand.MoveTo(route.points.first(), zoom = 15.5))
            _events.emit(UiEvent.Message("路线「${route.name}」已保存。"))
            if (startImmediately) {
                requestStart(route)
            }
        }
    }

    fun startSavedRoute(route: RoutePlan) {
        _mapUi.update {
            it.copy(
                center = route.points.firstOrNull() ?: it.center,
                addressTitle = "路线 · ${route.name}",
                addressSubtitle = "${route.points.size} 个航点 / ${Geo.formatDistance(route.distanceMeters)}",
                routeMode = route.mode,
            )
        }
        _mapCommands.tryEmit(MapCommand.MoveTo(route.points.first(), zoom = 15.5))
        _events.tryEmit(UiEvent.NavigateToMap)
        requestStart(route)
    }

    fun editSavedRoute(route: RoutePlan) {
        beginRouteDraft(route)
        _events.tryEmit(UiEvent.NavigateToMap)
    }

    fun startRouteRecording() {
        if (_routeRecorderUi.value.recording) return
        val current = session.value
        val point = current.currentPoint
        if (!current.running || point == null) {
            _routeRecorderUi.value = RouteRecorderUiState(error = "先开始模拟，再录制移动轨迹。")
            return
        }
        val recorder = RouteRecorder(minPointDistanceMeters = 1.5)
        recorder.start(point)
        activeRouteRecorder = recorder
        _routeRecorderUi.value = RouteRecorderUiState(
            recording = true,
            pointCount = 1,
            elapsedSeconds = 0L,
            distanceMeters = 0.0,
        )
        routeRecordingJob?.cancel()
        routeRecordingJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val active = activeRouteRecorder
                if (active !== recorder) break
                if (!session.value.running) {
                    activeRouteRecorder = null
                    routeRecordingJob = null
                    if (active.canSave()) {
                        persistRecordedRoute(active, auto = true)
                    } else {
                        active.stop()
                        _routeRecorderUi.value = RouteRecorderUiState(
                            error = "模拟已停止；本次录制不足 5 m，未保存。",
                        )
                    }
                    break
                }
                session.value.currentPoint?.let { recorder.add(it) }
                val snapshot = recorder.snapshot()
                _routeRecorderUi.update {
                    it.copy(
                        recording = true,
                        pointCount = snapshot.pointCount,
                        elapsedSeconds = snapshot.elapsedSeconds,
                        distanceMeters = snapshot.distanceMeters,
                        error = null,
                    )
                }
            }
        }
    }

    fun stopRouteRecording(save: Boolean = true) {
        val recorder = activeRouteRecorder ?: return
        routeRecordingJob?.cancel()
        routeRecordingJob = null
        activeRouteRecorder = null
        recorder.stop()
        if (save && recorder.canSave()) {
            viewModelScope.launch {
                persistRecordedRoute(recorder, auto = false)
            }
        } else {
            _routeRecorderUi.value = RouteRecorderUiState(
                error = if (save) "至少移动 5 m 才能保存路线。" else "已放弃本次录制。",
            )
        }
    }

    private suspend fun persistRecordedRoute(recorder: RouteRecorder, auto: Boolean) {
        val snapshot = recorder.snapshot()
        val points = snapshot.points
        if (points.size < 2 || snapshot.distanceMeters < 5.0) {
            _routeRecorderUi.value = RouteRecorderUiState(error = "本次录制不足 5 m，未保存。")
            return
        }
        val mode = if (session.value.running) session.value.mode else settings.value.motionMode
        val route = RoutePlan(
            id = UUID.randomUUID().toString(),
            name = "录制 ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())}",
            points = points,
            loop = false,
            mode = mode,
        )
        settingsRepository.update {
            it.copy(
                routes = (it.routes + route).takeLast(30),
                lastPoint = route.points.first(),
                lastPointLabel = "路线 · ${route.name}",
            )
        }
        _routeRecorderUi.value = RouteRecorderUiState()
        _events.emit(
            UiEvent.Message(
                if (auto) {
                    "模拟已停止，录制路线「${route.name}」已保存（${Geo.formatDistance(route.distanceMeters)}）。"
                } else {
                    "录制路线「${route.name}」已保存（${Geo.formatDistance(route.distanceMeters)}）。"
                },
            ),
        )
    }

    fun deleteRoute(route: RoutePlan) {
        viewModelScope.launch {
            settingsRepository.update { current ->
                current.copy(routes = current.routes.filterNot { it.id == route.id })
            }
            notify("已删除路线「${route.name}」。")
        }
    }

    // --------------------------------------------------------------- environment

    fun setDrift(config: DriftConfig) {
        mutateSettings { it.copy(drift = config) }
    }

    fun setEnvironment(environment: EnvironmentProfile) {
        mutateSettings { it.copy(environment = environment.copy(preset = "custom")) }
    }

    fun setSensors(sensors: SensorConfig) {
        mutateSettings { it.copy(sensors = sensors) }
        _fridaUi.update {
            it.copy(
                artifact = null,
                notice = "传感器开关已变化；重新生成 Frida 脚本后生效。",
                error = null,
            )
        }
    }

    fun setFridaTarget(packageName: String) {
        val clean = packageName.trim()
        mutateSettings { it.copy(fridaTargetPackage = clean) }
        _fridaUi.update {
            it.copy(
                targetPackage = clean,
                artifact = null,
                notice = null,
                error = null,
            )
        }
    }

    fun refreshFridaEnvironment() {
        if (_fridaUi.value.checkingEnvironment) return
        _fridaUi.update { it.copy(checkingEnvironment = true, error = null) }
        viewModelScope.launch {
            fridaRepository.inspectEnvironment()
                .onSuccess { environment ->
                    _fridaUi.update {
                        it.copy(
                            checkingEnvironment = false,
                            environment = environment,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _fridaUi.update {
                        it.copy(
                            checkingEnvironment = false,
                            error = error.message ?: "无法读取 Frida 环境。",
                        )
                    }
                }
        }
    }

    fun generateFridaHook(targetOverride: String? = null) {
        if (_fridaUi.value.generating) return
        val target = (targetOverride ?: _fridaUi.value.targetPackage).trim()
        if (!shizuku.isSafePackageName(target)) {
            _fridaUi.update {
                it.copy(
                    targetPackage = target,
                    error = "目标包名格式无效，例如 com.example.app。",
                    notice = null,
                )
            }
            return
        }
        mutateSettings { it.copy(fridaTargetPackage = target) }
        _fridaUi.update {
            it.copy(
                targetPackage = target,
                generating = true,
                artifact = null,
                notice = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val current = settingsRepository.current()
            val mode = current.motionMode
            val speed = current.speeds.forMode(mode)
            fridaRepository.generate(
                targetPackage = target,
                mode = mode,
                speedMps = speed,
                sensors = current.sensors,
            ).onSuccess { artifact ->
                _fridaUi.update {
                    it.copy(
                        generating = false,
                        artifact = artifact,
                        notice = when {
                            artifact.deployedPath != null ->
                                "脚本已部署到设备的 /data/local/tmp。将脚本拉到电脑后，在外部运行 Frida。"
                            artifact.note.isNotBlank() -> artifact.note
                            else -> "脚本已生成。将脚本拉到电脑后，在外部运行 Frida。"
                        },
                    )
                }
                _events.tryEmit(UiEvent.Message("Frida Hook 脚本已生成。"))
            }.onFailure { error ->
                _fridaUi.update {
                    it.copy(
                        generating = false,
                        error = error.message ?: "生成 Frida Hook 脚本失败。",
                    )
                }
            }
        }
    }

    fun applyEnvironmentPreset(preset: String) {
        val base = settings.value.environment
        val updated = when (preset) {
            "cafe" -> base.copy(
                preset = preset,
                wifi = base.wifi.copy(
                    enabled = true,
                    ssid = "SEVEN-ELEVEN-5G",
                    bssid = "A4:2B:8C:19:7D:02",
                    rssi = -57,
                    channel = 149,
                ),
                bluetooth = base.bluetooth.copy(
                    enabled = true,
                    name = "AirPods Pro",
                    address = "3C:2A:4F:81:9B:20",
                    rssi = -62,
                ),
                cell = base.cell.copy(enabled = true, signalDbm = -91),
            )
            "metro" -> base.copy(
                preset = preset,
                wifi = base.wifi.copy(enabled = false),
                bluetooth = base.bluetooth.copy(enabled = false),
                cell = base.cell.copy(
                    enabled = true,
                    carrier = "中国移动",
                    mcc = "460",
                    mnc = "00",
                    areaCode = 21987,
                    cellId = 3735921,
                    networkType = "LTE",
                    signalDbm = -103,
                ),
            )
            "office" -> base.copy(
                preset = preset,
                wifi = base.wifi.copy(
                    enabled = true,
                    ssid = "OFFICE-5G",
                    bssid = "7C:11:BE:32:90:11",
                    rssi = -46,
                    channel = 44,
                ),
                bluetooth = base.bluetooth.copy(enabled = true, name = "Meeting Room TV", rssi = -71),
                cell = base.cell.copy(enabled = true, signalDbm = -78),
            )
            else -> base.copy(
                preset = "clear",
                wifi = base.wifi.copy(enabled = false),
                bluetooth = base.bluetooth.copy(enabled = false),
                cell = base.cell.copy(enabled = false),
            )
        }
        mutateSettings { it.copy(environment = updated) }
    }

    fun setMapLayer(layer: MapLayer) {
        mutateSettings { it.copy(mapLayer = layer) }
    }

    fun setAmapWebKey(key: String) {
        mutateSettings { it.copy(amapWebKey = key) }
    }

    fun setHaptics(enabled: Boolean) {
        mutateSettings { it.copy(hapticsEnabled = enabled) }
    }

    fun dismissSensorNotice() {
        mutateSettings { it.copy(showSensorNotice = false) }
    }

    // --------------------------------------------------------------------- apps

    fun ensureAppsLoaded() {
        if (appsLoadJob?.isActive == true || appsScanJob?.isActive == true) return
        if (_appsUi.value.apps.isNotEmpty()) return
        refreshApps()
    }

    fun refreshApps() {
        appsScanJob?.cancel()
        appsLoadJob?.cancel()
        _appsUi.update {
            it.copy(
                loading = true,
                scanning = false,
                scannedCount = 0,
                scanTotal = 0,
                selectedPackage = null,
                cacheBytes = null,
                error = null,
            )
        }
        appsLoadJob = viewModelScope.launch {
            val result = runCatching { targetApps.loadInstalledApps() }
            result.onSuccess { list ->
                _appsUi.update {
                    it.copy(
                        loading = false,
                        apps = list,
                        scanTotal = list.size,
                        scanning = list.isNotEmpty(),
                        scannedCount = 0,
                    )
                }
                startSdkScan(list)
            }.onFailure { error ->
                _appsUi.update {
                    it.copy(
                        loading = false,
                        error = "读取应用列表失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    private fun startSdkScan(list: List<TargetAppInfo>) {
        appsScanJob?.cancel()
        appsScanJob = viewModelScope.launch {
            var scanned = 0
            for (app in list) {
                if (!isActive) break
                val result = targetApps.scanLocationSdk(app.packageName)
                scanned += 1
                _appsUi.update { state ->
                    state.copy(
                        apps = state.apps.map {
                            if (it.packageName == app.packageName) it.copy(sdkScan = result) else it
                        },
                        scannedCount = scanned,
                        scanTotal = list.size,
                    )
                }
            }
            _appsUi.update { it.copy(scanning = false) }
        }
    }

    fun setAppsQuery(query: String) {
        _appsUi.update { it.copy(query = query) }
    }

    fun setAppsFilter(filter: AppFilter) {
        _appsUi.update { it.copy(filter = filter) }
    }

    fun dismissAppsError() {
        _appsUi.update { it.copy(error = null) }
    }

    fun selectApp(packageName: String?) {
        cacheSizeJob?.cancel()
        _appsUi.update {
            it.copy(
                selectedPackage = packageName,
                cacheBytes = null,
                cacheError = null,
                operationMessage = null,
                error = null,
            )
        }
        if (packageName != null) {
            readCacheSize(packageName, askForUsageAccess = false)
        }
    }

    fun readSelectedCacheSize() {
        val packageName = _appsUi.value.selectedPackage ?: return
        readCacheSize(packageName, askForUsageAccess = true)
    }

    private fun readCacheSize(packageName: String, askForUsageAccess: Boolean) {
        cacheSizeJob?.cancel()
        cacheSizeJob = viewModelScope.launch {
            _appsUi.update { it.copy(cacheReading = true) }
            var size = targetApps.loadCacheBytes(packageName)
            if (size == null && askForUsageAccess && shizuku.status.value.ready) {
                val grant = shizuku.grantUsageStats()
                if (grant.success) {
                    size = targetApps.loadCacheBytes(packageName)
                }
            }
            _appsUi.update { state ->
                if (state.selectedPackage != packageName) {
                    state.copy(cacheReading = false)
                } else {
                    state.copy(
                        cacheReading = false,
                        cacheBytes = size,
                        cacheError = if (size == null) "无法读取该应用的缓存大小。" else null,
                        apps = state.apps.map {
                            if (it.packageName == packageName) it.copy(cacheBytes = size) else it
                        },
                    )
                }
            }
            if (size == null && askForUsageAccess) {
                notify(
                    if (shizuku.status.value.ready) {
                        "无法读取该应用的缓存大小；手动清理不需要这个权限。"
                    } else {
                        "读取缓存大小需要先授权 Shizuku。"
                    },
                )
            }
        }
    }

    fun prepareManualCacheCleanup(backupFirst: Boolean) {
        val app = _appsUi.value.selectedApp ?: return
        if (_appsUi.value.clearing) return
        _appsUi.update { it.copy(clearing = true, operationMessage = null, error = null) }
        viewModelScope.launch {
            val shizukuReady = shizuku.status.value.ready
            var message = "正在打开系统应用信息。请进入“存储和缓存”并点击“清除缓存”。"
            if (backupFirst && shizukuReady) {
                val backup = runCatching { cacheRepository.backupExternalCache(app) }.getOrNull()
                message = if (backup != null) {
                    "已备份可访问的外部缓存（${TargetAppRepository.formatCacheSize(backup.sizeBytes)}）。现在请在系统页清除缓存。"
                } else {
                    "未能生成外部缓存备份（目标可能没有公开缓存）。现在请在系统页清除缓存。"
                }
            } else if (backupFirst) {
                message = "Shizuku 未就绪，未备份外部缓存。现在请在系统页清除缓存。"
            }
            _appsUi.update { it.copy(clearing = false, operationMessage = message) }
            _events.emit(UiEvent.OpenAppDetails(app.packageName))
        }
    }

    fun restoreCacheBackup(record: CacheBackupRecord) {
        if (_appsUi.value.restoring) return
        _appsUi.update { it.copy(restoring = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { cacheRepository.restore(record) }
                .getOrElse { Result.failure(it) }
            _appsUi.update { it.copy(restoring = false) }
            result.onSuccess {
                _appsUi.update {
                    it.copy(
                        operationMessage = "已写回「${record.appLabel}」的外部缓存快照。" +
                            "受系统文件所有权限制，目标应用不保证能读取。",
                    )
                }
                readCacheSize(record.packageName, askForUsageAccess = false)
            }.onFailure { error ->
                _appsUi.update {
                    it.copy(operationMessage = "恢复失败：${error.message ?: "未知错误"}")
                }
            }
        }
    }


    fun dismissOperationMessage() {
        _appsUi.update { it.copy(operationMessage = null) }
    }

    fun latestBackup(packageName: String, backups: List<CacheBackupRecord>): CacheBackupRecord? =
        backups.filter { it.packageName == packageName }.maxByOrNull { it.createdAtEpochMs }

    // --------------------------------------------------------------- diagnostics

    fun refreshDiagnostics() {
        if (_diagnostics.value.loading) return
        _diagnostics.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { diagnosticsRepository.buildReport() }
            result.onSuccess { report ->
                _diagnostics.value = DiagnosticsUiState(loading = false, report = report)
            }.onFailure { error ->
                _diagnostics.value = DiagnosticsUiState(
                    loading = false,
                    error = error.message ?: "诊断失败。",
                )
            }
        }
    }

    fun requestBatteryOptimizationSettings() {
        _events.tryEmit(UiEvent.OpenBatteryOptimizationSettings)
    }

    fun openDeveloperOptions() {
        _events.tryEmit(UiEvent.OpenDeveloperOptions)
    }

    fun openOverlaySettingsPage() {
        _events.tryEmit(UiEvent.OpenOverlaySettings)
    }

    fun openLocationSettingsPage() {
        _events.tryEmit(UiEvent.OpenLocationSettings)
    }

    fun refreshSystemStatus() {
        shizuku.refresh()
        refreshPermissions()
        notify("已刷新系统连接状态。")
    }

    fun requestNotificationSettings() {
        _events.tryEmit(UiEvent.OpenNotificationSettings)
    }

    // ------------------------------------------------------------------- update

    fun checkForUpdates(repoOverride: String? = null) {
        if (_updateUi.value.checking) return
        val repo = (repoOverride ?: settings.value.updateRepo).trim().ifBlank { BuildConfig.UPDATE_REPO }
        if (repoOverride != null) {
            mutateSettings { it.copy(updateRepo = repo) }
        }
        if (repo.isBlank()) {
            _updateUi.update {
                it.copy(
                    checking = false,
                    checked = true,
                    error = "尚未配置更新仓库。请先填写 GitHub 的 owner/repo。",
                )
            }
            return
        }
        _updateUi.update { it.copy(checking = true, error = null, info = null, checked = false) }
        viewModelScope.launch {
            val result = updateRepository.checkForUpdate(repo, BuildConfig.VERSION_NAME)
            result.onSuccess { info ->
                _updateUi.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        info = info,
                        error = if (info == null) "该仓库还没有发布 Release。" else null,
                    )
                }
                if (info != null && !info.isNewer) {
                    notify("已经是最新版本：${BuildConfig.VERSION_NAME.removeSuffix("-dev")}。")
                }
            }.onFailure { error ->
                _updateUi.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        error = error.message ?: "检查更新失败。",
                    )
                }
            }
        }
    }

    fun setUpdateRepo(repo: String) {
        mutateSettings { it.copy(updateRepo = repo.trim()) }
        _updateUi.value = UpdateUiState()
    }

    fun clearUpdateResult() {
        _updateUi.value = UpdateUiState()
    }

    fun openUrl(url: String) {
        _events.tryEmit(UiEvent.OpenUrl(url))
    }

    // ---------------------------------------------------------------- onboarding

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(onboarded = true) }
            refreshPermissions()
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(onboarded = false) }
        }
    }

    fun markOnboardingCompletedWithoutShizuku() {
        completeOnboarding()
    }

    fun notify(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    private fun mutateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    private data class StartRequest(
        val point: GeoPoint,
        val route: RoutePlan?,
    )

    private enum class PendingAfterLocation { Start, LocateReal }
}
