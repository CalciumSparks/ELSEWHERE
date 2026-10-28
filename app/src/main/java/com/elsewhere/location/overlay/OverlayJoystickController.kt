package com.elsewhere.location.overlay

import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.MockSessionState
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.JoystickPad
import com.elsewhere.location.ui.components.TonalTag
import com.elsewhere.location.ui.theme.ElsewhereTheme
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.OverlayInk
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.Signal
import kotlinx.coroutines.flow.StateFlow

class OverlayJoystickController(
    private val context: Service,
    private val state: StateFlow<MockSessionState>,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onJoystick(x: Float, y: Float)
        fun onJoystickRelease()
        fun onMode(mode: MotionMode)
        fun onSpeedDelta(delta: Float)
        fun onStop()
        fun onClose()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayLifecycleOwner? = null

    fun show() {
        if (composeView != null) return
        if (!Settings.canDrawOverlays(context)) {
            throw IllegalStateException("悬浮窗权限未开启")
        }
        val overlayOwner = OverlayLifecycleOwner().apply { start() }
        val width = dp(248)
        val height = dp(328)
        val metrics = context.resources.displayMetrics
        val layoutParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - width - dp(12)).coerceAtLeast(dp(8))
            y = dp(140)
        }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            elevation = dp(14).toFloat()
            setContent {
                ElsewhereTheme {
                    OverlayPanel(
                        state = state,
                        onDrag = ::moveWindow,
                        onMode = callbacks::onMode,
                        onSpeedDelta = callbacks::onSpeedDelta,
                        onJoystick = callbacks::onJoystick,
                        onJoystickRelease = callbacks::onJoystickRelease,
                        onStop = callbacks::onStop,
                        onClose = callbacks::onClose,
                    )
                }
            }
        }
        windowManager.addView(view, layoutParams)
        composeView = view
        params = layoutParams
        owner = overlayOwner
    }

    fun render(state: MockSessionState) {
        // State is consumed by Compose directly; this method keeps the service
        // contract explicit and can be used for future haptics.
    }

    fun hide() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        owner?.destroy()
        owner = null
        composeView = null
        params = null
    }

    private fun moveWindow(dx: Float, dy: Float) {
        val layout = params ?: return
        val view = composeView ?: return
        val metrics = context.resources.displayMetrics
        layout.x = (layout.x + dx.toInt()).coerceIn(-dp(40), metrics.widthPixels - dp(120))
        layout.y = (layout.y + dy.toInt()).coerceIn(-dp(20), metrics.heightPixels - dp(100))
        runCatching { windowManager.updateViewLayout(view, layout) }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun start() {
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

@Composable
private fun OverlayPanel(
    state: StateFlow<MockSessionState>,
    onDrag: (Float, Float) -> Unit,
    onMode: (MotionMode) -> Unit,
    onSpeedDelta: (Float) -> Unit,
    onJoystick: (Float, Float) -> Unit,
    onJoystickRelease: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    val session by state.collectAsState()
    Box(Modifier.fillMaxSize().padding(6.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(CutCornerShape(topEnd = 20.dp, bottomStart = 20.dp))
                .background(OverlayInk)
                .border(
                    1.dp,
                    PaperWhite.copy(alpha = 0.16f),
                    CutCornerShape(topEnd = 20.dp, bottomStart = 20.dp),
                )
                .padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(Live, CutCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (session.routeId != null) "路线巡航" else "摇杆控制",
                            style = MonoTiny.copy(letterSpacing = 0.9.sp),
                            color = PaperHigh,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (session.routeId != null) "AUTO MODE" else "GRAB TO MOVE",
                            style = MonoTiny.copy(fontSize = 7.sp),
                            color = Paper.copy(alpha = 0.34f),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (session.routeId != null) "无需摇杆 · 两侧可调速" else "按住行进 / 松开缓停",
                        style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.4.sp),
                        color = Paper.copy(alpha = 0.55f),
                    )
                }
                OverlayIconButton(EIcon.Stop, "停止模拟", onStop, danger = true)
                Spacer(Modifier.width(7.dp))
                OverlayIconButton(EIcon.Close, "收起摇杆", onClose)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MotionMode.entries.forEach { mode ->
                    val selected = session.mode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(CutCornerShape(topEnd = 6.dp))
                            .background(if (selected) PaperHigh else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) PaperHigh else Paper.copy(alpha = 0.15f),
                                CutCornerShape(topEnd = 6.dp),
                            )
                            .clickable(role = Role.Button) { onMode(mode) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            mode.title,
                            style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                            color = if (selected) Ink else Paper.copy(alpha = 0.66f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%.1f".format(session.speedMps),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp, lineHeight = 24.sp),
                    color = PaperHigh,
                )
                Spacer(Modifier.width(4.dp))
                Text("m/s", style = MonoTiny, color = Paper.copy(alpha = 0.4f))
                Spacer(Modifier.weight(1f))
                OverlayStepButton(EIcon.Minus, "减速") { onSpeedDelta(-0.2f) }
                Spacer(Modifier.width(4.dp))
                OverlayStepButton(EIcon.Plus, "加速") { onSpeedDelta(0.2f) }
            }
            Spacer(Modifier.height(5.dp))
            if (session.routeId != null) {
                OverlayRouteProgress(
                    session = session,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                JoystickPad(
                    enabled = true,
                    mode = session.mode,
                    onVector = onJoystick,
                    onRelease = onJoystickRelease,
                    running = session.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(29.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (session.routeId != null) {
                        "航线 ${(session.routeProgress * 100).toInt()}% · ${Geo.formatDistance(session.distanceMeters)}"
                    } else {
                        "${session.mode.englishLabel} · 步频 ${session.cadenceSpm.toInt()} spm"
                    },
                    style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.25.sp),
                    color = Paper.copy(alpha = 0.46f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (session.routeId != null) {
                    TonalTag(
                        "AUTO",
                        fill = Live.copy(alpha = 0.16f),
                        color = Live,
                    )
                }
            }
        }
    }
}

private val PaperWhite = Color(0xFFFBF8EF)

@Composable
private fun OverlayRouteProgress(
    session: MockSessionState,
    modifier: Modifier = Modifier,
) {
    val progress = session.routeProgress.coerceIn(0f, 1f)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(158.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                drawArc(
                    color = PaperWhite.copy(alpha = 0.10f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
                drawArc(
                    color = if (session.routeComplete) Live else Signal,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 32.sp,
                        lineHeight = 34.sp,
                    ),
                    color = PaperHigh,
                )
                Text(
                    if (session.routeComplete) "ROUTE COMPLETE" else "AUTO CRUISE",
                    style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.7.sp),
                    color = if (session.routeComplete) Live else Paper.copy(alpha = 0.38f),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            session.routeName ?: "模拟路线",
            style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.2.sp),
            color = Paper.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${Geo.formatDistance(session.distanceMeters)} · 自动转向",
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Paper.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Box(
        Modifier
            .size(27.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .background(if (danger) Signal else Color.Transparent)
            .border(
                1.dp,
                if (danger) Signal else PaperWhite.copy(alpha = 0.22f),
                androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) PaperHigh else Paper.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun OverlayStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(25.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .border(1.dp, PaperWhite.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Paper.copy(alpha = 0.72f), modifier = Modifier.size(12.dp))
    }
}
