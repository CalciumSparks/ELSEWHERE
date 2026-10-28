package com.elsewhere.location.ui.map

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.GeocodeResult
import com.elsewhere.location.core.MapLayer
import com.elsewhere.location.core.MockSessionState
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.SavedPlace
import com.elsewhere.location.service.MockLocationService
import com.elsewhere.location.core.AppSettings
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.MapCommand
import com.elsewhere.location.ui.MapUiState
import com.elsewhere.location.ui.PermissionUiState
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.EIconButton
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.PrimaryAction
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.Spacing
import com.elsewhere.location.ui.components.StatusPill
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.SurveySegmentedControl
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.components.TopSheetShape
import com.elsewhere.location.ui.components.BrandLockup
import com.elsewhere.location.ui.theme.Amber
import com.elsewhere.location.ui.theme.Danger
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Green
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.LineStrong
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperDeep
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.SignalWash
import com.elsewhere.location.ui.theme.SystemSans
import com.elsewhere.location.ui.theme.White
import kotlinx.coroutines.delay
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max
import kotlin.math.min

@Composable
fun MapScreen(
    viewModel: ElsewhereViewModel,
    onOpenSettings: () -> Unit,
) {
    val ui by viewModel.mapUi.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    var layerMenuOpen by remember { mutableStateOf(false) }
    val routeEditingState = rememberUpdatedState(ui.routeEditing)
    val layerState = rememberUpdatedState(settings.mapLayer)
    val viewModelState = rememberUpdatedState(viewModel)
    val routeOverlays = remember { mutableListOf<Overlay>() }
    val trackOverlays = remember { mutableListOf<Overlay>() }

    LaunchedEffect(session.running) {
        expanded = !session.running
    }

    BackHandler(enabled = ui.searchOpen) { viewModel.closeSearch() }
    BackHandler(enabled = ui.routeEditing && !ui.searchOpen) { viewModel.cancelRouteEditing() }

    val initialLayer = settings.mapLayer
    val mapView = remember {
        MapView(context).apply {
            setBackgroundColor(AndroidColor.rgb(242, 238, 227))
            val source = mapTileSource(initialLayer)
            setTileSource(source)
            android.util.Log.d("ElsewhereMap", "initial source=${source.name()} layer=$initialLayer")
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(15.8)
            controller.setCenter(ui.center.forLayer(initialLayer))
            tag = initialLayer.name
            addMapListener(
                DelayedMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            reportCenter(this@apply, viewModelState.value, layerState.value)
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            reportCenter(this@apply, viewModelState.value, layerState.value)
                            return false
                        }
                    },
                    220,
                ),
            )
            overlays.add(
                MapEventsOverlay(
                    context,
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                            val editing = routeEditingState.value
                            if (editing) {
                                viewModelState.value.addRoutePoint(p.fromLayer(layerState.value))
                                return true
                            }
                            return false
                        }

                        override fun longPressHelper(p: OsmGeoPoint): Boolean = false
                    },
                ),
            )
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            if (view.tag != settings.mapLayer.name) {
                view.setTileSource(mapTileSource(settings.mapLayer))
                view.tag = settings.mapLayer.name
            }
        },
    )

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapView, settings.mapLayer) {
        if (mapView.tag != settings.mapLayer.name) {
            mapView.setTileSource(mapTileSource(settings.mapLayer))
            mapView.tag = settings.mapLayer.name
        }
        mapView.controller.setCenter(ui.center.forLayer(settings.mapLayer))
        mapView.invalidate()
    }

    LaunchedEffect(mapView) {
        viewModel.mapCommands.collect { command ->
            when (command) {
                is MapCommand.MoveTo -> {
                    val target = command.point.forLayer(layerState.value)
                    command.zoom?.let { mapView.controller.setZoom(it) }
                    if (command.animate) mapView.controller.animateTo(target)
                    else mapView.controller.setCenter(target)
                }
                is MapCommand.FitRoute -> {
                    if (command.points.size >= 2) {
                        val displayed = command.points.map { it.forLayer(layerState.value) }
                        val box = BoundingBox.fromGeoPoints(displayed)
                        mapView.zoomToBoundingBox(box, true, 160)
                        mapView.scrollBy(0, mapView.height / 10)
                    }
                }
            }
        }
    }

    LaunchedEffect(mapView, ui.routeEditing, ui.routeDraft, settings.mapLayer) {
        val view = mapView
        routeOverlays.forEach { view.overlays.remove(it) }
        routeOverlays.clear()
        if (ui.routeEditing && ui.routeDraft.isNotEmpty()) {
            val displayPoints = ui.routeDraft.map { it.forLayer(settings.mapLayer) }
            if (displayPoints.size >= 2) {
                val linePoints = if (ui.routeLoop) {
                    displayPoints + displayPoints.first()
                } else {
                    displayPoints
                }
                val shadow = Polyline(view).apply {
                    setPoints(linePoints)
                    outlinePaint.color = AndroidColor.argb(140, 251, 248, 239)
                    outlinePaint.strokeWidth = 9f * context.resources.displayMetrics.density
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setGeodesic(true)
                }
                val routeLine = Polyline(view).apply {
                    setPoints(linePoints)
                    outlinePaint.color = AndroidColor.parseColor("#ED5A2D")
                    outlinePaint.strokeWidth = 4.2f * context.resources.displayMetrics.density
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    setGeodesic(true)
                }
                routeOverlays += shadow
                routeOverlays += routeLine
            }
            displayPoints.forEachIndexed { index, point ->
                val marker = Marker(view).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = WaypointDrawable(
                        context = context,
                        number = index + 1,
                        isStart = index == 0,
                        isEnd = index == displayPoints.lastIndex,
                    )
                    title = "航点 ${index + 1}"
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                }
                routeOverlays += marker
            }
            routeOverlays.forEach { view.overlays.add(it) }
            view.invalidate()
        }
    }

    LaunchedEffect(mapView, session.running, session.track, settings.mapLayer) {
        val view = mapView
        trackOverlays.forEach { view.overlays.remove(it) }
        trackOverlays.clear()
        if (session.running && session.track.size >= 2) {
            val displayed = session.track.map { it.forLayer(settings.mapLayer) }
            val shadow = Polyline(view).apply {
                setPoints(displayed)
                outlinePaint.color = AndroidColor.argb(120, 251, 248, 239)
                outlinePaint.strokeWidth = 9f * context.resources.displayMetrics.density
                setGeodesic(true)
            }
            val liveLine = Polyline(view).apply {
                setPoints(displayed)
                outlinePaint.color = AndroidColor.parseColor("#ED5A2D")
                outlinePaint.strokeWidth = 4.5f * context.resources.displayMetrics.density
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                setGeodesic(true)
            }
            trackOverlays += shadow
            trackOverlays += liveLine
            trackOverlays.forEach { view.overlays.add(it) }
            view.invalidate()
        }
    }

    var beaconMarker by remember { mutableStateOf<Marker?>(null) }
    LaunchedEffect(mapView, session.running, settings.mapLayer) {
        val view = mapView
        beaconMarker?.let { view.overlays.remove(it) }
        beaconMarker = null
        val point = session.currentPoint
        if (session.running && point != null) {
            val marker = Marker(view).apply {
                position = point.forLayer(settings.mapLayer)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MockBeaconDrawable(context)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
            }
            view.overlays.add(marker)
            beaconMarker = marker
            view.invalidate()
        }
    }
    LaunchedEffect(session.currentPoint, session.bearingDeg, mapView, settings.mapLayer) {
        val view = mapView
        val marker = beaconMarker ?: return@LaunchedEffect
        val point = session.currentPoint ?: return@LaunchedEffect
        marker.position = point.forLayer(settings.mapLayer)
        marker.rotation = session.bearingDeg
        view.invalidate()
    }


    Box(Modifier.fillMaxSize()) {
        // Map is drawn by AndroidView above. This overlay is purely atmospheric
        // and never intercepts pointer input.
        Box(
            Modifier
                .fillMaxWidth()
                .height(215.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Paper.copy(alpha = 0.985f),
                        0.38f to Paper.copy(alpha = 0.86f),
                        0.72f to Paper.copy(alpha = 0.34f),
                        1f to Paper.copy(alpha = 0.0f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Paper.copy(alpha = 0.07f)),
        )

        CrosshairOverlay(
            modifier = Modifier.align(Alignment.Center),
            active = session.running,
            locating = ui.locatingReal,
            routeEditing = ui.routeEditing,
        )

        MapTopHud(
            ui = ui,
            permissions = permissions,
            shizukuReady = shizuku.permissionGranted,
            session = session,
            onSearch = viewModel::openSearch,
            onStatusClick = onOpenSettings,
            onSettings = onOpenSettings,
        )

        MapToolStack(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            locating = ui.locatingReal,
            routeEditing = ui.routeEditing,
            layer = settings.mapLayer,
            onLocate = viewModel::locateRealPosition,
            onRoute = {
                if (ui.routeEditing) viewModel.cancelRouteEditing() else viewModel.beginRouteDraft()
            },
            onLayer = { layerMenuOpen = true },
            layerMenuOpen = layerMenuOpen,
            onLayerDismiss = { layerMenuOpen = false },
            onLayerSelected = {
                viewModel.setMapLayer(it)
                layerMenuOpen = false
            },
            running = session.running,
            onFollow = {
                session.currentPoint?.let { point ->
                    mapView.controller.animateTo(point.forLayer(settings.mapLayer))
                }
            },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            ui.pendingRealPoint?.let { point ->
                RealFixCard(
                    ui = ui,
                    point = point,
                    onApply = {
                        if (settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        viewModel.applyPendingRealPoint()
                    },
                    onCancel = viewModel::cancelPendingRealPoint,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (settings.mapLayer) {
                        MapLayer.GAODE -> "GCJ-02 显示 / WGS-84 注入"
                        MapLayer.OSM -> "WGS-84 显示 / WGS-84 注入"
                    },
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                Text(settings.mapLayer.attribution, style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
            }

            MapControlDeck(
                ui = ui,
                settings = settings,
                session = session,
                permissions = permissions,
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                onMode = viewModel::setMotionMode,
                onSpeed = viewModel::setModeSpeed,
                onStartStop = {
                    if (settings.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (session.isActive) viewModel.stopSession() else viewModel.requestStart()
                },
                onOverlay = {
                    if (session.overlayVisible) viewModel.hideOverlayNow() else viewModel.showOverlayNow()
                },
                onSetup = onOpenSettings,
                onAddRoutePoint = viewModel::addCurrentCenterAsRoutePoint,
                onUndoRoutePoint = viewModel::removeLastRoutePoint,
                onRouteName = viewModel::updateRouteName,
                onRouteLoop = viewModel::setRouteLoop,
                onRouteMode = viewModel::setRouteMode,
                onSaveRoute = { viewModel.saveRoute(startImmediately = false) },
                onSaveAndStartRoute = { viewModel.saveRoute(startImmediately = true) },
                onCancelRoute = viewModel::cancelRouteEditing,
            )
        }

        AnimatedVisibility(
            visible = ui.searchOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it / 5 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 8 },
        ) {
            MapSearchOverlay(
                ui = ui,
                onClose = viewModel::closeSearch,
                onQuery = viewModel::setSearchQuery,
                onSubmit = viewModel::submitSearch,
                onResult = viewModel::selectSearchResult,
                onRecent = viewModel::selectRecentPlace,
                onClearRecent = viewModel::clearRecentPlaces,
            )
        }
    }
}

private fun reportCenter(
    view: MapView,
    viewModel: ElsewhereViewModel,
    layer: MapLayer,
) {
    val center = view.mapCenter ?: return
    val point = center.fromLayer(layer)
    viewModel.onMapCenterChanged(point)
}

@Composable
private fun CrosshairOverlay(
    modifier: Modifier,
    active: Boolean,
    locating: Boolean,
    routeEditing: Boolean,
) {
    val infinite = rememberInfiniteTransition(label = "crosshair")
    val pulse by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crosshairPulse",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(84.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val hair = 1.dp.toPx()
            val ring = 13.dp.toPx()
            drawLine(
                color = PaperHigh.copy(alpha = 0.93f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, center.y - ring - 4.dp.toPx()),
                strokeWidth = hair * 1.4f,
            )
            drawLine(
                color = PaperHigh.copy(alpha = 0.93f),
                start = Offset(center.x, center.y + ring + 4.dp.toPx()),
                end = Offset(center.x, size.height),
                strokeWidth = hair * 1.4f,
            )
            drawLine(
                color = PaperHigh.copy(alpha = 0.93f),
                start = Offset(0f, center.y),
                end = Offset(center.x - ring - 4.dp.toPx(), center.y),
                strokeWidth = hair * 1.4f,
            )
            drawLine(
                color = PaperHigh.copy(alpha = 0.93f),
                start = Offset(center.x + ring + 4.dp.toPx(), center.y),
                end = Offset(size.width, center.y),
                strokeWidth = hair * 1.4f,
            )
            drawCircle(
                color = Ink.copy(alpha = 0.72f),
                radius = ring,
                center = center,
                style = Stroke(width = 1.15.dp.toPx()),
            )
            if (active || locating) {
                drawCircle(
                    color = Signal.copy(alpha = 0.24f),
                    radius = ring * pulse,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawCircle(
                color = Ink,
                radius = 6.2.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = Signal,
                radius = 4.1.dp.toPx(),
                center = center,
            )
            drawLine(
                color = PaperHigh,
                start = Offset(center.x - 2.5.dp.toPx(), center.y),
                end = Offset(center.x + 2.5.dp.toPx(), center.y),
                strokeWidth = 0.9.dp.toPx(),
            )
            drawLine(
                color = PaperHigh,
                start = Offset(center.x, center.y - 2.5.dp.toPx()),
                end = Offset(center.x, center.y + 2.5.dp.toPx()),
                strokeWidth = 0.9.dp.toPx(),
            )
        }
        Text(
            text = when {
                locating -> "LOCKING GPS"
                routeEditing -> "TAP TO ADD WAYPOINT"
                active -> "LIVE MOCK POSITION"
                else -> "CROSSHAIR / 准星"
            },
            style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
            color = if (active) Signal else InkSoft,
            modifier = Modifier
                .offset(y = (-4).dp)
                .background(PaperHigh.copy(alpha = 0.86f), RoundedCornerShape(1.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MapTopHud(
    ui: MapUiState,
    permissions: PermissionUiState,
    shizukuReady: Boolean,
    session: MockSessionState,
    onSearch: () -> Unit,
    onStatusClick: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLockup(compact = true)
            Spacer(Modifier.weight(1f))
            val statusText = when {
                session.running -> "MOCKING"
                permissions.mockLocation -> "READY"
                !permissions.fineLocation -> "PERMISSION"
                else -> "SETUP"
            }
            val tone = when {
                session.running || permissions.mockLocation -> StatusTone.Ready
                else -> StatusTone.Warning
            }
            Box(Modifier.clickable(role = Role.Button, onClick = onStatusClick)) {
                StatusPill(text = statusText, tone = tone)
            }
            Spacer(Modifier.width(7.dp))
            EIconButton(
                icon = EIcon.Sliders,
                contentDescription = "设置与系统诊断",
                onClick = onSettings,
                size = 38.dp,
                border = LineStrong,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))
                .background(PaperHigh.copy(alpha = 0.97f))
                .border(
                    1.dp,
                    LineStrong.copy(alpha = 0.8f),
                    CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
                )
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EIcon.Search, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (ui.searchOpen) ui.searchQuery else "搜索地址、地铁站或纬度, 经度",
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.searchQuery.isBlank()) Muted else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("SEARCH", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
        }
        if (!permissions.mockLocation || !permissions.fineLocation) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(PaperHigh.copy(alpha = 0.96f))
                    .border(1.dp, Signal.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                    .clickable(role = Role.Button, onClick = onStatusClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalDot(Signal, 5.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (!permissions.fineLocation) {
                        "需要精确位置权限 · 点击完成设置"
                    } else if (!shizukuReady) {
                        "尚未允许模拟位置 · 可在开发者选项中选择他乡"
                    } else {
                        "注入通道未完全就绪 · 点击检查"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(EIcon.ChevronRight, contentDescription = null, tint = Signal, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun MapToolStack(
    modifier: Modifier = Modifier,
    locating: Boolean,
    routeEditing: Boolean,
    layer: MapLayer,
    onLocate: () -> Unit,
    onRoute: () -> Unit,
    onLayer: () -> Unit,
    layerMenuOpen: Boolean,
    onLayerDismiss: () -> Unit,
    onLayerSelected: (MapLayer) -> Unit,
    running: Boolean,
    onFollow: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.End,
    ) {
        MapToolButton(
            icon = EIcon.Locate,
            label = if (locating) "锁定中" else "真实",
            active = locating,
            onClick = onLocate,
        )
        MapToolButton(
            icon = EIcon.Route,
            label = if (routeEditing) "取消" else "路线",
            active = routeEditing,
            onClick = onRoute,
        )
        Box {
            MapToolButton(
                icon = EIcon.Layers,
                label = "图层",
                active = layerMenuOpen,
                onClick = onLayer,
            )
            DropdownMenu(
                expanded = layerMenuOpen,
                onDismissRequest = onLayerDismiss,
                containerColor = PaperHigh,
                shape = RoundedCornerShape(2.dp),
            ) {
                MapLayer.entries.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                Text(
                                    if (item == MapLayer.GAODE) "中文底图 · 自动进行 GCJ-02 显示纠偏" else "OSM 底图 · 可能需要网络代理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                )
                            }
                        },
                        trailingIcon = {
                            if (layer == item) Icon(EIcon.Check, contentDescription = null, tint = Signal, modifier = Modifier.size(16.dp))
                        },
                        onClick = { onLayerSelected(item) },
                    )
                }
            }
        }
        if (running) {
            MapToolButton(icon = EIcon.Cursor, label = "跟随", onClick = onFollow)
        }
    }
}

@Composable
private fun MapToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Column(
        Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) Ink else PaperHigh.copy(alpha = 0.96f))
            .border(1.dp, if (active) Ink else LineStrong, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) PaperHigh else Ink,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.1.sp),
            color = if (active) Paper.copy(alpha = 0.65f) else Muted,
        )
    }
}

@Composable
private fun RealFixCard(
    ui: MapUiState,
    point: GeoPoint,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
            .background(PaperHigh)
            .border(1.dp, LineStrong, CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalDot(Signal, 6.dp)
            Spacer(Modifier.width(7.dp))
            Text("REAL GPS FIX / 真实位置", style = MonoLabel, color = Signal)
            Spacer(Modifier.weight(1f))
            Text(Geo.formatDecimal(point.latitude, point.longitude, 5), style = MonoTiny, color = Muted)
        }
        Spacer(Modifier.height(5.dp))
        val ageText = when {
            ui.pendingRealAgeSeconds < 60 -> "刚刚"
            ui.pendingRealAgeSeconds < 3_600 -> "${ui.pendingRealAgeSeconds / 60} 分钟前"
            else -> "${ui.pendingRealAgeSeconds / 3_600} 小时前"
        }
        val fixMeta = when {
            ui.pendingRealIsCached -> "缓存定位 · $ageText"
            ui.pendingRealAccuracyMeters != null -> "实时 GPS · 精度 ±${"%.1f".format(ui.pendingRealAccuracyMeters)} m"
            else -> "实时 GPS"
        }
        Text(
            fixMeta,
            style = MonoTiny.copy(fontSize = 8.sp),
            color = if (ui.pendingRealIsCached) Amber else Green,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (ui.pendingRealTitleLoading) "正在解析地址…" else ui.pendingRealTitle.ifBlank { "当前真实位置" },
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ui.pendingRealSubtitle.ifBlank { "确认后再手动应用此坐标。" },
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyActionChip("应用此点", onApply, selected = true, modifier = Modifier.weight(1f))
            TinyActionChip("取消", onCancel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MapControlDeck(
    ui: MapUiState,
    settings: AppSettings,
    session: MockSessionState,
    permissions: PermissionUiState,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onMode: (MotionMode) -> Unit,
    onSpeed: (MotionMode, Float) -> Unit,
    onStartStop: () -> Unit,
    onOverlay: () -> Unit,
    onSetup: () -> Unit,
    onAddRoutePoint: () -> Unit,
    onUndoRoutePoint: () -> Unit,
    onRouteName: (String) -> Unit,
    onRouteLoop: (Boolean) -> Unit,
    onRouteMode: (MotionMode) -> Unit,
    onSaveRoute: () -> Unit,
    onSaveAndStartRoute: () -> Unit,
    onCancelRoute: () -> Unit,
) {
    val activeMode = if (session.running) session.mode else settings.motionMode
    val activeSpeed = if (session.running) session.baseSpeedMps else settings.speeds.forMode(activeMode)
    val displayedPoint = if (session.running) (session.effectivePoint ?: ui.center) else ui.center
    val busy = session.starting || ui.preparingStart
    val statusText = when {
        session.running -> "RUNNING"
        ui.routeEditing -> "PLANNING"
        else -> "STANDBY"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(TopSheetShape)
            .background(PaperHigh)
            .border(1.dp, Line, TopSheetShape),
    ) {
        if (ui.routeEditing) {
            RouteEditorDeck(
                ui = ui,
                activeMode = ui.routeMode,
                onExpandedClose = onCancelRoute,
                onAddRoutePoint = onAddRoutePoint,
                onUndoRoutePoint = onUndoRoutePoint,
                onRouteName = onRouteName,
                onRouteLoop = onRouteLoop,
                onRouteMode = onRouteMode,
                onSaveRoute = onSaveRoute,
                onSaveAndStartRoute = onSaveAndStartRoute,
                onCancelRoute = onCancelRoute,
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onExpandedChange)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(30.dp)
                    .height(3.dp)
                    .background(LineStrong, RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(statusText, style = MonoLabel.copy(fontSize = 9.sp), color = if (session.running) Signal else Muted)
            Spacer(Modifier.weight(1f))
            if (session.running) {
                Text(
                    "%.1f m/s".format(session.speedMps),
                    style = MonoTiny,
                    color = Ink,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (expanded) EIcon.ChevronDown else EIcon.ChevronUp,
                contentDescription = if (expanded) "收起控制台" else "展开控制台",
                tint = Muted,
                modifier = Modifier.size(17.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    CoordinateBlock("N / 纬度", Geo.formatLatitude(displayedPoint.latitude), Modifier.weight(1f))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(38.dp)
                            .background(Line)
                            .align(Alignment.CenterVertically),
                    )
                    Spacer(Modifier.width(14.dp))
                    CoordinateBlock("E / 经度", Geo.formatLongitude(displayedPoint.longitude), Modifier.weight(1f))
                    Column(Modifier.align(Alignment.Top)) {
                        Text("WGS-84", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                        if (ui.reverseLoading) {
                            Spacer(Modifier.height(5.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.dp,
                                color = Signal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ui.addressTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ui.addressSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (session.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        session.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (session.notice != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        session.notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MOTION / 运动节奏", style = MonoLabel.copy(fontSize = 9.sp), color = Muted)
                        Spacer(Modifier.height(7.dp))
                        SurveySegmentedControl(
                            options = MotionMode.entries.map { it to it.title },
                            selected = activeMode,
                            onSelect = onMode,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SPEED / 速度", style = MonoLabel.copy(fontSize = 9.sp), color = Muted)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DeckStepButton(EIcon.Minus, "减速") {
                                val step = speedStep(activeMode)
                                onSpeed(activeMode, (activeSpeed - step).coerceIn(activeMode.speedRange))
                            }
                            Text(
                                "%.1f".format(activeSpeed),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                color = Ink,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            DeckStepButton(EIcon.Plus, "加速") {
                                val step = speedStep(activeMode)
                                onSpeed(activeMode, (activeSpeed + step).coerceIn(activeMode.speedRange))
                            }
                        }
                        Text("M/S", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                    }
                }
                if (session.running) {
                    Spacer(Modifier.height(11.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(PaperLow.copy(alpha = 0.44f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckMetric("TIME", Geo.formatDuration(session.elapsedSeconds), Modifier.weight(1f))
                        DeckMetric("DIST", Geo.formatDistance(session.distanceMeters), Modifier.weight(1f))
                        DeckMetric("STEPS", session.steps.toString(), Modifier.weight(0.7f))
                        DeckMetric("SPM", session.cadenceSpm.toInt().toString(), Modifier.weight(0.7f))
                    }
                    if (session.routeId != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (session.routeComplete) "路线完成" else "航线进度",
                                style = MonoTiny.copy(fontSize = 8.sp),
                                color = if (session.routeComplete) Signal else Muted,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(Line),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(session.routeProgress.coerceIn(0f, 1f))
                                        .background(if (session.routeComplete) Live else Signal),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryAction(
                        text = when {
                            busy -> "启动中…"
                            session.running -> "停止模拟"
                            else -> "开始模拟"
                        },
                        onClick = onStartStop,
                        modifier = Modifier.weight(1f),
                        icon = if (session.running) EIcon.Stop else EIcon.Play,
                        loading = busy,
                        danger = session.running,
                    )
                    if (session.running) {
                        Spacer(Modifier.width(8.dp))
                        Column(
                            Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (session.overlayVisible) Ink else PaperLow)
                                .border(
                                    1.dp,
                                    if (session.overlayVisible) Ink else LineStrong,
                                    RoundedCornerShape(2.dp),
                                )
                                .clickable(role = Role.Button, onClick = onOverlay),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                EIcon.Joystick,
                                contentDescription = if (session.overlayVisible) "收起悬浮摇杆" else "打开悬浮摇杆",
                                tint = if (session.overlayVisible) Live else Ink,
                                modifier = Modifier.size(19.dp),
                            )
                            Text(
                                if (session.overlayVisible) "收起" else "摇杆",
                                style = MonoTiny.copy(fontSize = 8.sp),
                                color = if (session.overlayVisible) Paper.copy(alpha = 0.7f) else Muted,
                            )
                        }
                    }
                }
                if (!session.running) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                !permissions.mockLocation -> "还需允许模拟位置"
                                !permissions.overlay -> "悬浮摇杆尚未授权"
                                else -> "测试定位提供者 · WGS-84 · 前台服务"
                            },
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = Muted,
                            modifier = Modifier.weight(1f),
                        )
                        TextActionMini("设置", onSetup)
                    }
                } else {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "模拟运行中 · 系统真实定位将被覆盖",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Faint,
                    )
                }
            }
        }

        if (!expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${"%.5f".format(displayedPoint.latitude)}, ${"%.5f".format(displayedPoint.longitude)}",
                    style = MonoTiny,
                    color = InkSoft,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (session.running) {
                    Text(
                        "${Geo.formatDuration(session.elapsedSeconds)} · ${Geo.formatDistance(session.distanceMeters)}",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .size(width = 72.dp, height = 34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (session.running) Signal else Ink)
                        .clickable(role = Role.Button) { onStartStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (session.running) EIcon.Stop else EIcon.Play,
                            contentDescription = null,
                            tint = PaperHigh,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (busy) "…" else if (session.running) "停止" else "开始",
                            style = MonoTiny.copy(fontSize = 9.sp),
                            color = PaperHigh,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinateBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.5.sp), color = Muted)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = com.elsewhere.location.ui.theme.JetBrainsMono,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
            ),
            color = Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun DeckMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MonoTiny.copy(fontSize = 7.sp), color = Muted)
        Text(value, style = MonoTiny.copy(letterSpacing = 0.sp), color = Ink)
    }
}

@Composable
private fun DeckStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(25.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, LineStrong, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun TextActionMini(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MonoTiny.copy(fontSize = 8.sp),
        color = Signal,
        modifier = Modifier
            .clip(RoundedCornerShape(1.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(SignalWash)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

private fun speedStep(mode: MotionMode): Float = when (mode) {
    MotionMode.WALK -> 0.10f
    MotionMode.RUN -> 0.20f
    MotionMode.CYCLE -> 0.40f
}

@Composable
private fun RouteEditorDeck(
    ui: MapUiState,
    activeMode: MotionMode,
    onExpandedClose: () -> Unit,
    onAddRoutePoint: () -> Unit,
    onUndoRoutePoint: () -> Unit,
    onRouteName: (String) -> Unit,
    onRouteLoop: (Boolean) -> Unit,
    onRouteMode: (MotionMode) -> Unit,
    onSaveRoute: () -> Unit,
    onSaveAndStartRoute: () -> Unit,
    onCancelRoute: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Signal))
            Spacer(Modifier.width(7.dp))
            Text("ROUTE PLAN / 模拟路线", style = MonoLabel, color = Signal)
            Spacer(Modifier.weight(1f))
            EIconButton(
                icon = EIcon.Close,
                contentDescription = "取消路线规划",
                onClick = onCancelRoute,
                size = 28.dp,
                border = Line,
                background = Color.Transparent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (ui.routeDraft.isEmpty()) "拖动地图，用准星选点；点击地图添加航点。" else "已添加 ${ui.routeDraft.size} 个航点 · ${Geo.formatDistance(ui.routeDistanceMeters)}",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.routeName,
                onValueChange = onRouteName,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                singleLine = true,
                cursorBrush = SolidColor(Signal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TinyActionChip("加准星", onAddRoutePoint)
            Spacer(Modifier.width(6.dp))
            TinyActionChip("撤销", onUndoRoutePoint, enabled = ui.routeDraft.isNotEmpty())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SurveySegmentedControl(
                options = MotionMode.entries.map { it to it.title },
                selected = activeMode,
                onSelect = onRouteMode,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("环线", style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
                Spacer(Modifier.width(6.dp))
                SwitchLike(
                    checked = ui.routeLoop,
                    onCheckedChange = onRouteLoop,
                )
            }
        }
        if (ui.routeError != null) {
            Spacer(Modifier.height(6.dp))
            Text(ui.routeError, style = MaterialTheme.typography.bodySmall, color = Danger)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyActionChip(
                "保存路线",
                onSaveRoute,
                modifier = Modifier.weight(1f),
            )
            PrimaryAction(
                text = "保存并巡航",
                onClick = onSaveAndStartRoute,
                modifier = Modifier.weight(1.3f),
                icon = EIcon.Play,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "路线按航点直线巡航；可开启环线反复经过。",
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Faint,
        )
    }
}

@Composable
private fun SwitchLike(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) Ink else PaperDeep)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { contentDescription = "环线巡航" },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(16.dp)
                .background(if (checked) Live else PaperHigh, CircleShape),
        )
    }
}

@Composable
private fun MapSearchOverlay(
    ui: MapUiState,
    onClose: () -> Unit,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onResult: (GeocodeResult) -> Unit,
    onRecent: (SavedPlace) -> Unit,
    onClearRecent: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(ui.searchOpen) {
        delay(180)
        focusRequester.requestFocus()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMarkSmall()
            Spacer(Modifier.width(8.dp))
            Text("SEARCH / 地址", style = MonoLabel, color = Ink)
            Spacer(Modifier.weight(1f))
            EIconButton(
                icon = EIcon.Close,
                contentDescription = "关闭搜索",
                onClick = onClose,
                size = 34.dp,
                border = Line,
                background = Color.Transparent,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(PaperHigh)
                .border(1.dp, LineStrong, RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EIcon.Search, contentDescription = null, tint = Signal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = ui.searchQuery,
                onValueChange = onQuery,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                singleLine = true,
                cursorBrush = SolidColor(Signal),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmit()
                    },
                ),
                decorationBox = { inner ->
                    Box {
                        if (ui.searchQuery.isEmpty()) {
                            Text(
                                "输入地址、地点或 纬度, 经度",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Faint,
                            )
                        }
                        inner()
                    }
                },
            )
            if (ui.searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.2.dp,
                    color = Signal,
                )
            } else if (ui.searchQuery.isNotEmpty()) {
                Box(
                    Modifier
                        .clickable { onQuery("") }
                        .padding(3.dp),
                ) {
                    Icon(EIcon.Close, contentDescription = "清空", tint = Faint, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            ui.searchError != null -> {
                Text(
                    ui.searchError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            ui.searchResults.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("RESULTS · ${ui.searchResults.size}", style = MonoLabel, color = Muted)
                            Spacer(Modifier.weight(1f))
                            Text("Photon / AMap", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                        }
                    }
                    items(ui.searchResults) { result ->
                        SearchResultRow(
                            result = result,
                            onClick = {
                                keyboard?.hide()
                                onResult(result)
                            },
                        )
                    }
                }
            }
            ui.searchQuery.isBlank() -> {
                RecentSearchPanel(
                    recentPlaces = ui.recentPlaces,
                    onRecent = onRecent,
                    onClear = onClearRecent,
                )
            }
            else -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text("按回车搜索「${ui.searchQuery}」", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "支持“城市 街道/地标”，或直接输入“纬度, 经度”。默认 WGS-84 坐标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandMarkSmall() {
    Canvas(Modifier.size(20.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Ink, size.minDimension * 0.34f, center, style = Stroke(1.1.dp.toPx()))
        drawLine(Ink, Offset(center.x, 0f), Offset(center.x, size.height * 0.24f), 1.dp.toPx())
        drawLine(Ink, Offset(center.x, size.height * 0.76f), Offset(center.x, size.height), 1.dp.toPx())
        drawLine(Ink, Offset(0f, center.y), Offset(size.width * 0.24f, center.y), 1.dp.toPx())
        drawLine(Ink, Offset(size.width * 0.76f, center.y), Offset(size.width, center.y), 1.dp.toPx())
        drawCircle(Signal, size.minDimension * 0.10f, center)
    }
}

@Composable
private fun SearchResultRow(result: GeocodeResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.dp, LineStrong, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            SignalDot(Signal, 4.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(result.source, style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
        }
        Spacer(Modifier.width(8.dp))
        Icon(EIcon.ChevronRight, contentDescription = null, tint = Faint, modifier = Modifier.size(14.dp))
    }
    Hairline(Modifier.padding(horizontal = 16.dp), color = Line.copy(alpha = 0.6f))
}

@Composable
private fun RecentSearchPanel(
    recentPlaces: List<SavedPlace>,
    onRecent: (SavedPlace) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RECENT / 最近", style = MonoLabel, color = Muted)
            Spacer(Modifier.weight(1f))
            if (recentPlaces.isNotEmpty()) {
                TextActionMini("清空", onClear)
            }
        }
        if (recentPlaces.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 26.dp)) {
                Text(
                    "还没有最近地点。",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkSoft,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "搜索一个地址，或用准星选点后按下开始模拟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(PaperHigh)
                        .border(1.dp, Line, RoundedCornerShape(2.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EIcon.Search, contentDescription = null, tint = Signal, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("坐标输入示例", style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
                        Text("31.2304, 121.4737", style = MonoTiny, color = Ink)
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(recentPlaces) { place ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onRecent(place) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(place.label, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                            Text(place.detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
                        }
                        Text(
                            "${"%.4f".format(place.latitude)}, ${"%.4f".format(place.longitude)}",
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = Faint,
                        )
                    }
                    Hairline(Modifier.padding(horizontal = 16.dp), color = Line.copy(alpha = 0.5f))
                }
            }
        }
    }
}
