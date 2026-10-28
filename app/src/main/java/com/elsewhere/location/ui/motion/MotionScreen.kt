package com.elsewhere.location.ui.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.RoutePlan
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.components.ContourField
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.EIconButton
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.JoystickPad
import com.elsewhere.location.ui.components.NoticeBlock
import com.elsewhere.location.ui.components.PrimaryAction
import com.elsewhere.location.ui.components.RhythmWave
import com.elsewhere.location.ui.components.RouteSketch
import com.elsewhere.location.ui.components.ScreenBackdrop
import com.elsewhere.location.ui.components.ScreenHeader
import com.elsewhere.location.ui.components.SecondaryAction
import com.elsewhere.location.ui.components.SectionRule
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.SpeedGauge
import com.elsewhere.location.ui.components.Spacing
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.SurveySlider
import com.elsewhere.location.ui.components.SurveySwitch
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.components.TonalTag
import com.elsewhere.location.ui.theme.Amber
import com.elsewhere.location.ui.theme.Danger
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.LineStrong
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.PaperDeep
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SignalWash
import com.elsewhere.location.ui.theme.SystemSans
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MotionScreen(
    viewModel: ElsewhereViewModel,
    onOpenSettings: () -> Unit,
    onPlanRoute: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val activeMode = if (session.running) session.mode else settings.motionMode
    val currentSpeed = if (session.running) session.baseSpeedMps else settings.speeds.forMode(activeMode)
    val busy = session.starting
    val speedHistory = remember { mutableStateListOf<Float>() }
    var deleteTarget by remember { mutableStateOf<RoutePlan?>(null) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentSpeed) }

    LaunchedEffect(currentSpeed) {
        if (!sliderDragging) sliderValue = currentSpeed
    }
    LaunchedEffect(session.running, session.speedMps) {
        if (session.running) {
            if (speedHistory.size >= 60) speedHistory.removeAt(0)
            speedHistory.add(session.speedMps)
        } else if (speedHistory.isNotEmpty()) {
            speedHistory.clear()
        }
    }

    val overlayChecked = if (session.running) session.overlayVisible else settings.overlayEnabled

    Box(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        ScreenBackdrop(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
                .align(Alignment.TopCenter),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            ScreenHeader(
                eyebrow = "MOTION / 03",
                title = "节奏与位移",
                subtitle = "选择一种移动方式，让输出速度拥有呼吸。",
                onSettings = onOpenSettings,
                statusText = when {
                    session.running -> "RUNNING"
                    busy -> "ARMING"
                    else -> "STANDBY"
                },
                statusTone = when {
                    session.running -> StatusTone.Ready
                    busy -> StatusTone.Warning
                    else -> StatusTone.Idle
                },
            )

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SESSION / 模拟会话", style = MonoLabel.copy(fontSize = 9.sp), color = Muted)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalDot(if (session.running) Live else Faint, 5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                session.running -> "正在向系统输出坐标"
                                busy -> "正在建立定位通道"
                                else -> "尚未开始模拟"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (session.running) Ink else Muted,
                        )
                    }
                }
                SecondaryAction(
                    text = if (session.running) "停止模拟" else if (busy) "启动中…" else "开始模拟",
                    onClick = {
                        if (settings.hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (session.isActive) viewModel.stopSession() else viewModel.requestStart()
                    },
                    icon = if (session.running) EIcon.Stop else EIcon.Play,
                    enabled = !busy,
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionRule(
                index = "01",
                title = "驱动模式",
                english = "drive mode",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text(
                        "WGS-84 · 实时",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Faint,
                    )
                },
            )
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                MotionMode.entries.forEach { mode ->
                    MotionModeCard(
                        mode = mode,
                        selected = activeMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setMotionMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(CutCornerShape(topEnd = 24.dp, bottomStart = 24.dp))
                    .background(PaperHigh)
                    .border(1.dp, Line, CutCornerShape(topEnd = 24.dp, bottomStart = 24.dp))
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("LIVE SPEED / 实时速度", style = MonoLabel.copy(fontSize = 9.sp), color = Muted)
                    Spacer(Modifier.weight(1f))
                    if (session.routeId != null) {
                        TonalTag(
                            if (session.routeComplete) "ROUTE COMPLETE" else "ROUTE CRUISE",
                            fill = if (session.routeComplete) SignalWash else Ink,
                            color = if (session.routeComplete) Signal else Live,
                        )
                    } else {
                        TonalTag(
                            if (session.running) "MOCK ACTIVE" else "PREVIEW",
                            fill = if (session.running) Ink else PaperLow,
                            color = if (session.running) Live else Muted,
                        )
                    }
                }
                SpeedGauge(
                    speedMps = session.speedMps,
                    targetSpeedMps = session.targetSpeedMps,
                    mode = activeMode,
                    running = session.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(262.dp),
                )
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SPEED SET",
                            style = MonoLabel.copy(fontSize = 8.sp),
                            color = Muted,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "%.1f m/s".format(currentSpeed),
                            style = MonoTiny.copy(letterSpacing = 0.sp),
                            color = Ink,
                        )
                    }
                    SurveySlider(
                        value = sliderValue,
                        onValueChange = {
                            sliderDragging = true
                            sliderValue = it
                        },
                        valueRange = activeMode.speedRange,
                        steps = if (activeMode == MotionMode.WALK) 17 else 20,
                        onValueChangeFinished = {
                            sliderDragging = false
                            viewModel.setModeSpeed(activeMode, sliderValue)
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "%.1f".format(activeMode.speedRange.start),
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = Faint,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "%.1f".format(activeMode.speedRange.endInclusive),
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = Faint,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RHYTHM / 节奏", style = MonoLabel.copy(fontSize = 8.sp), color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (session.running) "步频 ${session.cadenceSpm.toInt()} spm" else "步态预览",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = if (session.running) Signal else Faint,
                    )
                }
                Spacer(Modifier.height(4.dp))
                RhythmWave(
                    speeds = speedHistory,
                    mode = activeMode,
                    running = session.running,
                )
            }

            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("OVERLAY / 悬浮摇杆", style = MonoLabel.copy(fontSize = 9.sp), color = Muted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            session.running && session.overlayVisible -> "正在其他应用上方显示"
                            !permissions.overlay -> "需要“显示在其他应用上层”权限"
                            session.running -> "模拟运行时可随时打开"
                            else -> "设置将在下一次模拟时沿用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                SurveySwitch(
                    checked = overlayChecked,
                    onCheckedChange = { enabled ->
                        if (session.running) {
                            if (enabled) viewModel.showOverlayNow() else viewModel.hideOverlayNow()
                        } else {
                            viewModel.setOverlayPreference(enabled)
                        }
                    },
                    contentDescription = "悬浮摇杆",
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "02",
                title = "方向",
                english = "steering",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text(
                        when {
                            session.routeId != null -> "AUTO STEER"
                            session.running -> "HOLD TO MOVE"
                            else -> "WAITING"
                        },
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = if (session.running) Signal else Faint,
                    )
                },
            )
            if (session.routeId != null) {
                RouteCruisePanel(
                    name = session.routeName ?: "模拟路线",
                    progress = session.routeProgress,
                    complete = session.routeComplete,
                    distance = session.distanceMeters,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    JoystickPad(
                        enabled = session.running,
                        mode = activeMode,
                        onVector = viewModel::setJoystick,
                        onRelease = viewModel::releaseJoystick,
                        running = session.running,
                        modifier = Modifier.size(226.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (session.running) "按住拖动 · 松开缓缓停下" else "开始模拟后，可直接在这里或悬浮窗控制方向",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "03",
                title = "运动传感器",
                english = "telemetry",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text("APP-ONLY", style = MonoTiny.copy(fontSize = 8.sp), color = Signal)
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                NoticeBlock(
                    text = "这些通道只驱动他乡内部的运动推演与回放，不会把传感器流注入其他应用。Shizuku 没有系统传感器写权限。",
                    title = "应用内模拟 / 非系统注入",
                    tone = StatusTone.Warning,
                )
                Spacer(Modifier.height(9.dp))
                val telemetry = session.telemetry
                val sensors = settings.sensors
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorTile(
                        title = "加速度",
                        code = "ACCEL",
                        enabled = sensors.accelerometer,
                        value = if (session.running) {
                            "x %.2f\n y %.2f\n z %.2f".format(telemetry.accelX, telemetry.accelY, telemetry.accelZ)
                        } else "—",
                        unit = "m/s²",
                        onToggle = {
                            viewModel.setSensors(sensors.copy(accelerometer = !sensors.accelerometer))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SensorTile(
                        title = "陀螺仪",
                        code = "GYRO",
                        enabled = sensors.gyroscope,
                        value = if (session.running) {
                            "x %.2f\n y %.2f\n z %.2f".format(telemetry.gyroX, telemetry.gyroY, telemetry.gyroZ)
                        } else "—",
                        unit = "rad/s",
                        onToggle = {
                            viewModel.setSensors(sensors.copy(gyroscope = !sensors.gyroscope))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorTile(
                        title = "重力",
                        code = "GRAVITY",
                        enabled = sensors.gravity,
                        value = if (session.running) {
                            "z %.2f".format(telemetry.gravityZ)
                        } else "—",
                        unit = "m/s²",
                        onToggle = {
                            viewModel.setSensors(sensors.copy(gravity = !sensors.gravity))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SensorTile(
                        title = "线性加速度",
                        code = "LINEAR",
                        enabled = sensors.linearAcceleration,
                        value = if (session.running) {
                            "x %.2f\n z %.2f".format(telemetry.linearX, telemetry.linearZ)
                        } else "—",
                        unit = "m/s²",
                        onToggle = {
                            viewModel.setSensors(sensors.copy(linearAcceleration = !sensors.linearAcceleration))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorTile(
                        title = "步数",
                        code = "STEP COUNTER",
                        enabled = sensors.stepCounter,
                        value = if (session.running) session.steps.toString() else "—",
                        unit = "steps",
                        onToggle = {
                            viewModel.setSensors(sensors.copy(stepCounter = !sensors.stepCounter))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SensorTile(
                        title = "步伐检测",
                        code = "STEP DETECTOR",
                        enabled = sensors.stepDetector,
                        value = when {
                            !session.running -> "—"
                            session.telemetry.stepDetected -> "● STEP"
                            else -> "WAIT"
                        },
                        unit = if (session.telemetry.stepDetected) "detected" else "listening",
                        highlight = session.running && session.telemetry.stepDetected,
                        onToggle = {
                            viewModel.setSensors(sensors.copy(stepDetector = !sensors.stepDetector))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "04",
                title = "模拟路线",
                english = "route plans",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    TinyActionChip("新建路线", onClick = onPlanRoute, icon = EIcon.Plus)
                },
            )
            if (settings.routes.isEmpty()) {
                EmptyRoutes(onPlanRoute)
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    settings.routes.asReversed().forEach { route ->
                        RouteRow(
                            route = route,
                            busy = busy,
                            onStart = { viewModel.startSavedRoute(route) },
                            onEdit = { viewModel.editSavedRoute(route) },
                            onDelete = { deleteTarget = route },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = PaperHigh,
            titleContentColor = Ink,
            textContentColor = InkSoft,
            title = {
                Text("删除路线？", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text("「${deleteTarget?.name}」将从本机删除，无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { viewModel.deleteRoute(it) }
                    deleteTarget = null
                }) {
                    Text("删除", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("保留", color = Muted)
                }
            },
        )
    }
}

@Composable
private fun MotionModeCard(
    mode: MotionMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(CutCornerShape(topEnd = 10.dp))
            .background(if (selected) Ink else PaperHigh)
            .border(
                1.dp,
                if (selected) Ink else Line,
                CutCornerShape(topEnd = 10.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                SignalDot(Live, 5.dp)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                mode.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) PaperHigh else Ink,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            mode.englishLabel,
            style = MonoTiny.copy(letterSpacing = 0.6.sp, fontSize = 8.sp),
            color = if (selected) Paper.copy(alpha = 0.54f) else Faint,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "%.1f–%.1f".format(mode.speedRange.start, mode.speedRange.endInclusive),
            style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.sp),
            color = if (selected) Live else Muted,
        )
    }
}

@Composable
private fun SensorTile(
    title: String,
    code: String,
    enabled: Boolean,
    value: String,
    unit: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val infinite = rememberInfiniteTransition(label = "sensorPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) PaperHigh else PaperLow.copy(alpha = 0.44f))
            .border(1.dp, if (highlight) Signal.copy(alpha = 0.55f) else Line, RoundedCornerShape(2.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Ink else Muted)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(17.dp)
                    .clickable(role = Role.Checkbox, onClick = onToggle)
                    .semantics { contentDescription = "启用$title" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .border(1.dp, if (enabled) Ink else LineStrong, RoundedCornerShape(1.dp))
                        .background(if (enabled) Ink else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (enabled) {
                        Icon(EIcon.Check, contentDescription = null, tint = Live, modifier = Modifier.size(9.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(code, style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.6.sp), color = Faint)
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (highlight && enabled) {
                Box(
                    Modifier
                        .padding(end = 5.dp, bottom = 3.dp)
                        .size(5.dp * pulse)
                        .background(Signal, CircleShape),
                )
            }
            Text(
                if (enabled) value else "OFF",
                style = MonoTiny.copy(
                    fontSize = if (enabled) 10.sp else 8.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.sp,
                ),
                color = when {
                    !enabled -> Faint
                    highlight -> Signal
                    else -> InkSoft
                },
                maxLines = 3,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(unit, style = MonoTiny.copy(fontSize = 7.sp), color = Faint)
            Spacer(Modifier.weight(1f))
            if (highlight && enabled) {
                Text("LIVE", style = MonoTiny.copy(fontSize = 7.sp), color = Signal)
            }
        }
    }
}

@Composable
private fun RouteCruisePanel(
    name: String,
    progress: Float,
    complete: Boolean,
    distance: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
            .background(PaperHigh)
            .border(1.dp, Line, CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalDot(if (complete) Live else Signal, 6.dp)
            Spacer(Modifier.width(7.dp))
            Text(if (complete) "路线完成" else "自动巡航中", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(name, style = MonoTiny.copy(fontSize = 8.sp), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Line)) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(if (complete) Live else Signal),
            )
        }
        Spacer(Modifier.height(7.dp))
        Row {
            Text("${(progress * 100).toInt()}%", style = MonoTiny, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(Geo.formatDistance(distance), style = MonoTiny, color = Muted)
            Spacer(Modifier.width(12.dp))
            Text("自动转向", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
        }
    }
}

@Composable
private fun EmptyRoutes(onPlanRoute: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, Line, CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
            .clickable(role = Role.Button, onClick = onPlanRoute)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EIcon.Route, contentDescription = null, tint = Signal, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("还没有模拟路线", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    "在地图上添加航点，设置一条会自己走完的路线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            Icon(EIcon.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun RouteRow(
    route: RoutePlan,
    busy: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteSketch(
                points = route.points,
                modifier = Modifier.width(96.dp).height(58.dp),
                loop = route.loop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        route.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (route.loop) {
                        Spacer(Modifier.width(5.dp))
                        TonalTag("环线", fill = PaperLow, color = Muted)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${route.points.size} 航点 · ${Geo.formatDistance(route.distanceMeters)} · ${route.mode.title}",
                    style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                    color = Muted,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(route.createdAtEpochMs)),
                    style = MonoTiny.copy(fontSize = 7.sp),
                    color = Faint,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinyActionChip("巡航", onStart, selected = true, icon = EIcon.Play)
            Spacer(Modifier.width(7.dp))
            TinyActionChip("编辑", onEdit, icon = EIcon.Route)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "删除${route.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(EIcon.Trash, contentDescription = null, tint = Faint, modifier = Modifier.size(14.dp))
            }
        }
        Hairline(Modifier.padding(top = 11.dp), color = Line.copy(alpha = 0.6f))
    }
}
