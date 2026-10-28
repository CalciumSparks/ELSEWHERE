package com.elsewhere.location.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.elsewhere.location.MainActivity
import com.elsewhere.location.R
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.MockSessionState
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SessionStartConfig
import com.elsewhere.location.location.MockLocationProvider
import com.elsewhere.location.location.MotionEngine
import com.elsewhere.location.overlay.OverlayJoystickController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class MockLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var tickJob: Job? = null
    private var startJob: Job? = null
    private var engine: MotionEngine? = null
    private var provider: MockLocationProvider? = null
    private var overlayController: OverlayJoystickController? = null
    private var sessionConfig: SessionStartConfig? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastTickNanos = 0L
    private var lastNotificationNanos = 0L
    private var failureStreak = 0
    private var routeCompleteAnnounced = false
    private var lastPushedPoint = null as com.elsewhere.location.core.GeoPoint?

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val raw = intent.getStringExtra(EXTRA_CONFIG)
                if (raw.isNullOrBlank()) {
                    failBeforeForeground("缺少定位会话参数。")
                    return START_NOT_STICKY
                }
                val config = runCatching {
                    json.decodeFromString(SessionStartConfig.serializer(), raw)
                }.getOrElse {
                    failBeforeForeground("定位会话参数损坏。")
                    return START_NOT_STICKY
                }
                if (!startForegroundSafely()) return START_NOT_STICKY
                beginSession(config)
            }
            ACTION_STOP -> stopSession(clearError = true)
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_SET_MODE -> {
                val mode = intent.getStringExtra(EXTRA_MODE)?.let {
                    runCatching { MotionMode.valueOf(it) }.getOrNull()
                } ?: return START_NOT_STICKY
                val speed = intent.getFloatExtra(EXTRA_SPEED, Float.NaN)
                applyMode(mode, if (speed.isFinite()) speed else null)
            }
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, Float.NaN)
                if (speed.isFinite()) applyBaseSpeed(speed)
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            failBeforeForeground("尚未授予精确位置权限，无法启动前台定位服务。")
            return false
        }
        val notification = buildNotification(
            title = "正在准备定位通道",
            body = "初始化测试位置提供者…",
        )
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
            true
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    running = false,
                    starting = false,
                    error = "无法进入前台定位服务：${error.message ?: "系统拒绝"}",
                )
            }
            stopSelf()
            false
        }
    }

    private fun beginSession(config: SessionStartConfig) {
        startJob?.cancel()
        tickJob?.cancel()
        teardownProvider(keepForeground = true)
        sessionConfig = config
        routeCompleteAnnounced = false
        failureStreak = 0
        lastTickNanos = 0L
        lastPushedPoint = config.route?.points?.firstOrNull() ?: config.point
        _state.value = MockSessionState(
            running = false,
            starting = true,
            basePoint = lastPushedPoint,
            effectivePoint = lastPushedPoint,
            mode = config.mode,
            baseSpeedMps = config.baseSpeedMps,
            targetSpeedMps = config.baseSpeedMps,
            routeId = config.route?.id,
            routeName = config.route?.name,
            overlayVisible = false,
            notice = "正在连接定位服务…",
            driftActive = config.drift.enabled,
        )

        startJob = scope.launch {
            delay(80)
            val newProvider = MockLocationProvider(this@MockLocationService)
            val installAttempt = runCatching { newProvider.install() }
                .getOrElse { Result.failure(it) }
            val installed = installAttempt.getOrNull()
            if (installed != null) {
                provider = newProvider
                failureStreak = 0
                val newEngine = MotionEngine(
                    config = config,
                    seed = System.nanoTime(),
                )
                engine = newEngine
                acquireWakeLock()
                val initialPoint = newEngine.currentPoint()
                lastPushedPoint = initialPoint
                _state.update {
                    it.copy(
                        running = true,
                        starting = false,
                        basePoint = initialPoint,
                        effectivePoint = initialPoint,
                        notice = if (installed.failures.isEmpty()) null
                        else "部分定位通道不可用：${installed.failures.joinToString("；")}",
                        servedProviderCount = newProvider.providerCount(),
                        startedAtEpochMs = System.currentTimeMillis(),
                    )
                }
                updateNotification(force = true)
                lastTickNanos = SystemClock.elapsedRealtimeNanos()
                tickJob = scope.launch { runTickLoop(config) }
                if (config.overlayEnabled) {
                    showOverlay()
                }
            } else {
                failSession(
                    installAttempt.exceptionOrNull()?.message ?: "模拟定位服务启动失败。",
                )
            }
        }
    }

    private suspend fun runTickLoop(config: SessionStartConfig) {
        while (scope.isActive && engine != null) {
            val now = SystemClock.elapsedRealtimeNanos()
            val dt = if (lastTickNanos == 0L) 0.2
            else ((now - lastTickNanos).coerceAtLeast(0L) / 1_000_000_000.0).coerceIn(0.05, 1.0)
            lastTickNanos = now

            val currentEngine = engine ?: break
            val currentProvider = provider ?: break
            val frame = currentEngine.advance(dt)
            val pointToPush = frame.effectivePoint
            val pushResult = currentProvider.push(
                point = pointToPush,
                speedMps = frame.speedMps,
                bearingDeg = frame.bearingDeg,
                environment = config.environment,
                telemetry = frame.telemetry,
                modeLabel = frame.mode.name,
            )
            if (pushResult.isFailure) {
                failureStreak += 1
                if (failureStreak >= 3) {
                    failSession(
                        pushResult.exceptionOrNull()?.message
                            ?: "连续三次定位注入失败，已停止以保证系统真实定位恢复。",
                    )
                    break
                }
            } else {
                failureStreak = 0
                lastPushedPoint = pointToPush
            }

            if (frame.routeComplete && !routeCompleteAnnounced) {
                routeCompleteAnnounced = true
                _state.update {
                    it.copy(notice = "路线已完成，定位保持在终点。")
                }
            }

            _state.update {
                it.copy(
                    running = true,
                    starting = false,
                    basePoint = frame.basePoint,
                    effectivePoint = pointToPush,
                    mode = frame.mode,
                    speedMps = frame.speedMps,
                    targetSpeedMps = frame.targetSpeedMps,
                    baseSpeedMps = frame.baseSpeedMps,
                    bearingDeg = frame.bearingDeg,
                    elapsedSeconds = frame.elapsedSeconds,
                    distanceMeters = frame.distanceMeters,
                    steps = frame.steps,
                    cadenceSpm = frame.cadenceSpm,
                    routeProgress = frame.routeProgress,
                    routeComplete = frame.routeComplete,
                    telemetry = frame.telemetry,
                    track = currentEngine.trackSnapshot(),
                    servedProviderCount = currentProvider.providerCount(),
                    driftActive = config.drift.enabled,
                    error = null,
                )
            }
            overlayController?.render(_state.value)
            if (now - lastNotificationNanos > 4_000_000_000L) {
                updateNotification()
                lastNotificationNanos = now
            }
            delay(200)
        }
    }

    private fun applyMode(mode: MotionMode, requestedSpeed: Float? = null) {
        val config = sessionConfig ?: return
        val speed = requestedSpeed ?: config.speedSettings.forMode(mode)
        engine?.setMode(mode, speed)
        _state.update {
            it.copy(
                mode = mode,
                baseSpeedMps = speed.coerceIn(mode.speedRange),
                notice = null,
            )
        }
        overlayController?.render(_state.value)
        updateNotification(force = true)
    }

    private fun applyBaseSpeed(speed: Float) {
        val currentMode = _state.value.mode
        val clamped = speed.coerceIn(currentMode.speedRange)
        engine?.setBaseSpeed(clamped)
        _state.update { it.copy(baseSpeedMps = clamped, notice = null) }
        overlayController?.render(_state.value)
        updateNotification(force = true)
    }

    private fun showOverlay() {
        if (!_state.value.running) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            _state.update { it.copy(notice = "需要悬浮窗权限才能显示摇杆。") }
            return
        }
        if (overlayController != null) return
        overlayController = OverlayJoystickController(
            context = this,
            state = state,
            callbacks = object : OverlayJoystickController.Callbacks {
                override fun onJoystick(x: Float, y: Float) {
                    engine?.setJoystick(x, y)
                }

                override fun onJoystickRelease() {
                    engine?.releaseJoystick()
                }

                override fun onMode(mode: MotionMode) = applyMode(mode)

                override fun onSpeedDelta(delta: Float) {
                    applyBaseSpeed(_state.value.baseSpeedMps + delta)
                }

                override fun onStop() {
                    stopSession(clearError = true)
                }

                override fun onClose() {
                    hideOverlay()
                    _state.update {
                        it.copy(notice = "摇杆已收起，可从控制台再次打开。")
                    }
                }
            },
        ).also { controller ->
            runCatching { controller.show() }
                .onSuccess {
                    _state.update { it.copy(overlayVisible = true, notice = null) }
                }
                .onFailure { error ->
                    overlayController = null
                    _state.update {
                        it.copy(overlayVisible = false, notice = "悬浮摇杆显示失败：${error.message}")
                    }
                }
        }
    }

    private fun hideOverlay() {
        overlayController?.let { controller ->
            runCatching { controller.hide() }
        }
        overlayController = null
        _state.update { it.copy(overlayVisible = false) }
    }

    private fun stopSession(clearError: Boolean) {
        startJob?.cancel()
        tickJob?.cancel()
        startJob = null
        tickJob = null
        hideOverlay()
        teardownProvider(keepForeground = false)
        releaseWakeLock()
        val lastPoint = lastPushedPoint
        _state.update {
            it.copy(
                running = false,
                starting = false,
                overlayVisible = false,
                speedMps = 0f,
                targetSpeedMps = 0f,
                cadenceSpm = 0f,
                basePoint = lastPoint ?: it.basePoint,
                effectivePoint = lastPoint ?: it.effectivePoint,
                notice = if (clearError) "模拟已停止，系统定位已恢复。" else it.notice,
                error = if (clearError) null else it.error,
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failSession(message: String) {
        startJob?.cancel()
        tickJob?.cancel()
        startJob = null
        tickJob = null
        hideOverlay()
        teardownProvider(keepForeground = false)
        releaseWakeLock()
        _state.update {
            it.copy(
                running = false,
                starting = false,
                overlayVisible = false,
                speedMps = 0f,
                targetSpeedMps = 0f,
                cadenceSpm = 0f,
                error = message,
                notice = null,
                basePoint = lastPushedPoint ?: it.basePoint,
                effectivePoint = lastPushedPoint ?: it.effectivePoint,
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failBeforeForeground(message: String) {
        _state.update {
            it.copy(running = false, starting = false, error = message)
        }
        stopSelf()
    }

    private fun teardownProvider(keepForeground: Boolean) {
        runCatching { provider?.teardown() }
        provider = null
        engine = null
        if (!keepForeground) {
            // no-op: provider teardown is independent of the foreground notification
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Elsewhere::MockLocation",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            11,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags(),
        )
        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFEC5A2E.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun updateNotification(force: Boolean = false) {
        val current = _state.value
        if (!current.running && !current.starting) return
        val mode = current.mode.title
        val speed = "%.1f m/s".format(current.speedMps)
        val point = current.effectivePoint
        val body = if (point != null) {
            "$mode · $speed · ${Geo.formatDecimal(point.latitude, point.longitude, 5)}"
        } else {
            "正在建立虚拟定位会话…"
        }
        val title = when {
            current.routeComplete -> "路线已完成 · 定位保持在终点"
            current.starting -> "正在准备定位通道"
            else -> "他乡正在改写坐标"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, body))
        lastNotificationNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { overlayController?.hide() }
        overlayController = null
        teardownProvider(keepForeground = true)
        releaseWakeLock()
        startJob?.cancel()
        tickJob?.cancel()
        if (activeInstance === this) activeInstance = null
        _state.update {
            it.copy(running = false, starting = false, overlayVisible = false)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.elsewhere.location.action.START"
        const val ACTION_STOP = "com.elsewhere.location.action.STOP"
        const val ACTION_SHOW_OVERLAY = "com.elsewhere.location.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.elsewhere.location.action.HIDE_OVERLAY"
        const val ACTION_SET_MODE = "com.elsewhere.location.action.SET_MODE"
        const val ACTION_SET_SPEED = "com.elsewhere.location.action.SET_SPEED"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SPEED = "speed"
        private const val CHANNEL_ID = "elsewhere_mock_location"
        private const val NOTIFICATION_ID = 2917

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private val _state = MutableStateFlow(MockSessionState())
        val state: StateFlow<MockSessionState> = _state.asStateFlow()

        @Volatile
        private var activeInstance: MockLocationService? = null

        fun start(context: Context, config: SessionStartConfig) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, json.encodeToString(SessionStartConfig.serializer(), config))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun showOverlay(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_SHOW_OVERLAY)
            runCatching { context.startService(intent) }
        }

        fun hideOverlay(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_HIDE_OVERLAY)
            runCatching { context.startService(intent) }
        }

        fun setJoystick(x: Float, y: Float) {
            activeInstance?.engine?.setJoystick(x, y)
        }

        fun releaseJoystick() {
            activeInstance?.engine?.releaseJoystick()
        }

        fun setMode(context: Context, mode: MotionMode, speed: Float? = null) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyMode(mode, speed)
            } else {
                val intent = Intent(context, MockLocationService::class.java).apply {
                    action = ACTION_SET_MODE
                    putExtra(EXTRA_MODE, mode.name)
                    if (speed != null) putExtra(EXTRA_SPEED, speed)
                }
                runCatching { context.startService(intent) }
            }
        }

        fun setSpeed(context: Context, speed: Float) {
            val instance = activeInstance
            if (instance != null) {
                instance.applyBaseSpeed(speed)
            } else {
                val intent = Intent(context, MockLocationService::class.java).apply {
                    action = ACTION_SET_SPEED
                    putExtra(EXTRA_SPEED, speed)
                }
                runCatching { context.startService(intent) }
            }
        }
    }
}
