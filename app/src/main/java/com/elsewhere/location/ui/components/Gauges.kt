package com.elsewhere.location.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.ui.theme.Amber
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
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SignalWash
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SpeedGauge(
    speedMps: Float,
    targetSpeedMps: Float,
    mode: MotionMode,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedSpeed by animateFloatAsState(speedMps, label = "speedNeedle")
    val maxSpeed = mode.speedRange.endInclusive.coerceAtLeast(1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            val center = Offset(size.width / 2f, size.height * 0.53f)
            val radius = min(size.width, size.height) * 0.41f
            val startAngle = 140f
            val sweep = 260f
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = Line,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Butt),
            )
            drawArc(
                color = Ink.copy(alpha = 0.08f),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.91f, center.y - radius * 0.91f),
                size = Size(radius * 1.82f, radius * 1.82f),
                style = Stroke(width = 0.7.dp.toPx()),
            )

            val tickCount = 40
            repeat(tickCount + 1) { index ->
                val fraction = index / tickCount.toFloat()
                val angle = Math.toRadians((startAngle + sweep * fraction).toDouble())
                val major = index % 5 == 0
                val inner = radius - if (major) 11.dp.toPx() else 6.dp.toPx()
                val outer = radius - 1.dp.toPx()
                val start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner,
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer,
                )
                drawLine(
                    color = if (major) Ink.copy(alpha = 0.55f) else LineStrong.copy(alpha = 0.55f),
                    start = start,
                    end = end,
                    strokeWidth = if (major) 1.3.dp.toPx() else 0.8.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }

            val normalized = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
            val needleAngle = Math.toRadians((startAngle + sweep * normalized).toDouble())
            val needleStart = Offset(
                center.x + cos(needleAngle).toFloat() * radius * 0.18f,
                center.y + sin(needleAngle).toFloat() * radius * 0.18f,
            )
            val needleEnd = Offset(
                center.x + cos(needleAngle).toFloat() * radius * 0.78f,
                center.y + sin(needleAngle).toFloat() * radius * 0.78f,
            )
            drawLine(
                color = Signal.copy(alpha = 0.18f),
                start = needleStart,
                end = needleEnd,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Signal,
                start = needleStart,
                end = needleEnd,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(Ink, radius = 4.dp.toPx(), center = center)
            drawCircle(PaperHigh, radius = 1.6.dp.toPx(), center = center)
            if (running) {
                val liveAngle = Math.toRadians((startAngle + sweep).toDouble())
                val livePoint = Offset(
                    center.x + cos(liveAngle).toFloat() * radius,
                    center.y + sin(liveAngle).toFloat() * radius,
                )
                drawCircle(Live, radius = 2.7.dp.toPx(), center = livePoint)
            }
            // Baseline ruler beneath the gauge.
            drawLine(
                color = Line.copy(alpha = 0.8f),
                start = Offset(size.width * 0.12f, size.height * 0.91f),
                end = Offset(size.width * 0.88f, size.height * 0.91f),
                strokeWidth = stroke,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 26.dp),
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.1f", animatedSpeed),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp, lineHeight = 58.sp),
                color = Ink,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("M/S", style = MonoTiny.copy(letterSpacing = 0.8.sp), color = Muted)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "目标 %.1f".format(targetSpeedMps) else "待机",
                    style = MonoTiny,
                    color = if (running) Signal else Faint,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = mode.englishLabel,
                style = MonoLabel.copy(letterSpacing = 2.2.sp),
                color = InkSoft,
                modifier = Modifier
                    .clip(RoundedCornerShape(1.dp))
                    .background(PaperLow.copy(alpha = 0.7f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun RhythmWave(
    speeds: List<Float>,
    mode: MotionMode,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val preview = remember(mode) { syntheticRhythm(mode) }
    val values = if (speeds.size >= 3) speeds else preview
    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(1.dp)),
    ) {
        drawRect(PaperHigh.copy(alpha = 0.35f))
        val columns = 12
        for (i in 0..columns) {
            val x = size.width * i / columns
            drawLine(Line.copy(alpha = 0.38f), Offset(x, 0f), Offset(x, size.height), 0.6.dp.toPx())
        }
        val rows = 3
        for (i in 0..rows) {
            val y = size.height * i / rows
            drawLine(Line.copy(alpha = 0.30f), Offset(0f, y), Offset(size.width, y), 0.6.dp.toPx())
        }
        val maxValue = (values.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
        val minValue = (values.minOrNull() ?: 0f).coerceAtMost(maxValue - 0.1f)
        val range = (maxValue - minValue).coerceAtLeast(0.1f)
        val path = Path()
        val fillPath = Path()
        val points = values.takeLast(64).mapIndexed { index, value ->
            val x = if (values.size <= 1) size.width / 2f else size.width * index / (values.size - 1f)
            val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
            val y = size.height * (0.88f - normalized * 0.72f)
            Offset(x, y)
        }
        points.forEachIndexed { index, offset ->
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
                fillPath.moveTo(offset.x, size.height)
                fillPath.lineTo(offset.x, offset.y)
            } else {
                path.lineTo(offset.x, offset.y)
                fillPath.lineTo(offset.x, offset.y)
            }
        }
        if (points.isNotEmpty()) {
            val last = points.last()
            fillPath.lineTo(last.x, size.height)
            fillPath.close()
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    listOf(
                        (if (running) Signal else LineStrong).copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                ),
            )
            drawPath(
                path,
                color = if (running) Signal else InkSoft.copy(alpha = 0.65f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = if (running) Signal else Faint,
                radius = 3.dp.toPx(),
                center = last,
            )
            drawLine(
                color = if (running) Signal.copy(alpha = 0.28f) else Line,
                start = Offset(last.x, 0f),
                end = Offset(last.x, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
        }
    }
}

private fun syntheticRhythm(mode: MotionMode): List<Float> {
    val base = when (mode) {
        MotionMode.WALK -> 1.35f
        MotionMode.RUN -> 3.2f
        MotionMode.CYCLE -> 5.8f
    }
    val amplitude = when (mode) {
        MotionMode.WALK -> 0.12f
        MotionMode.RUN -> 0.18f
        MotionMode.CYCLE -> 0.045f
    }
    val frequency = when (mode) {
        MotionMode.WALK -> 7.0f
        MotionMode.RUN -> 10.0f
        MotionMode.CYCLE -> 4.2f
    }
    return (0 until 64).map { index ->
        val phase = index / 64f * 2f * PI.toFloat() * frequency
        base * (1f + amplitude * sin(phase) + 0.028f * sin(phase * 2.1f))
    }
}

@Composable
fun JoystickPad(
    enabled: Boolean,
    mode: MotionMode,
    onVector: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
) {
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    fun updatePosition(position: Offset) {
        if (!enabled || padSize == IntSize.Zero) return
        val center = Offset(padSize.width / 2f, padSize.height / 2f)
        val maxRadius = min(padSize.width, padSize.height) * 0.34f
        val dx = position.x - center.x
        val dy = position.y - center.y
        val distance = hypot(dx, dy)
        val limited = if (distance > maxRadius) maxRadius / distance else 1f
        val knob = Offset(dx * limited, dy * limited)
        knobOffset = knob
        active = true
        if (maxRadius > 0f) onVector(knob.x / maxRadius, -knob.y / maxRadius)
    }

    fun finish() {
        if (active) onRelease()
        active = false
        knobOffset = Offset.Zero
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = "方向摇杆，按住拖动控制行走方向" }
            .onSizeChanged { padSize = it }
            .pointerInput(enabled, padSize) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { updatePosition(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        updatePosition(change.position)
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.44f
            drawCircle(
                color = PaperHigh.copy(alpha = 0.88f),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = if (active) Signal.copy(alpha = 0.50f) else LineStrong.copy(alpha = 0.66f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = Line.copy(alpha = 0.55f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 0.8.dp.toPx()),
            )
            drawCircle(
                color = Line.copy(alpha = 0.36f),
                radius = radius * 0.44f,
                center = center,
                style = Stroke(width = 0.7.dp.toPx()),
            )
            repeat(24) { i ->
                val angle = Math.toRadians((i * 15.0) - 90.0)
                val major = i % 6 == 0
                val startRadius = radius - if (major) 10.dp.toPx() else 6.dp.toPx()
                val start = Offset(
                    center.x + cos(angle).toFloat() * startRadius,
                    center.y + sin(angle).toFloat() * startRadius,
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * (radius - 1.dp.toPx()),
                    center.y + sin(angle).toFloat() * (radius - 1.dp.toPx()),
                )
                drawLine(
                    color = if (major) Ink.copy(alpha = 0.55f) else LineStrong.copy(alpha = 0.6f),
                    start = start,
                    end = end,
                    strokeWidth = if (major) 1.2.dp.toPx() else 0.7.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
            drawLine(
                color = Signal.copy(alpha = 0.30f),
                start = Offset(center.x, center.y - radius * 0.78f),
                end = Offset(center.x, center.y - radius * 0.62f),
                strokeWidth = 1.5.dp.toPx(),
            )
            val knobCenter = center + knobOffset
            drawCircle(
                color = if (running) Ink else Ink.copy(alpha = 0.28f),
                radius = 23.dp.toPx(),
                center = knobCenter,
            )
            drawCircle(
                color = if (running) Live else PaperLow,
                radius = 4.5.dp.toPx(),
                center = knobCenter,
            )
            drawCircle(
                color = PaperHigh.copy(alpha = 0.22f),
                radius = 17.dp.toPx(),
                center = knobCenter,
                style = Stroke(width = 0.7.dp.toPx()),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 60.dp),
        ) {
            Text(
                text = if (!enabled) "${mode.title} · 待机" else if (active) "行进中" else "按住 · 拖动",
                style = MonoTiny.copy(letterSpacing = 0.7.sp),
                color = if (active) Signal else Muted,
            )
        }
    }
}

@Composable
fun DriftPlot(
    amplitudeMeters: Float,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(86.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) * 0.42f
        repeat(4) { index ->
            drawCircle(
                color = Line.copy(alpha = 0.45f),
                radius = maxRadius * (index + 1) / 4f,
                center = center,
                style = Stroke(width = 0.7.dp.toPx()),
            )
        }
        drawLine(Line.copy(alpha = 0.4f), Offset(center.x, 0f), Offset(center.x, size.height), 0.6.dp.toPx())
        drawLine(Line.copy(alpha = 0.4f), Offset(0f, center.y), Offset(size.width, center.y), 0.6.dp.toPx())
        val scale = (amplitudeMeters / 15f).coerceIn(0.03f, 1f)
        val points = (0..54).map { index ->
            val t = index / 54f * 2f * PI.toFloat() + phase
            val radius = maxRadius * scale * (0.55f + 0.35f * sin(t * 2.3f))
            Offset(
                center.x + cos(t).toFloat() * radius,
                center.y + sin(t * 1.4f).toFloat() * radius,
            )
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, Signal.copy(alpha = 0.65f), style = Stroke(width = 1.dp.toPx()))
        drawCircle(Signal, radius = 3.5.dp.toPx(), center = points.lastOrNull() ?: center)
        drawCircle(Ink, radius = 1.4.dp.toPx(), center = center)
    }
}

@Composable
fun RouteSketch(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Signal,
    loop: Boolean = false,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(PaperLow.copy(alpha = 0.5f)),
    ) {
        if (points.size < 2) {
            drawLine(
                LineStrong.copy(alpha = 0.55f),
                Offset(0f, size.height / 2f),
                Offset(size.width, size.height / 2f),
                0.8.dp.toPx(),
            )
            return@Canvas
        }
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latRange = (maxLat - minLat).coerceAtLeast(0.00001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.00001)
        val padding = 12.dp.toPx()
        val mapped = points.map { point ->
            val x = padding + ((point.longitude - minLon) / lonRange).toFloat() * (size.width - padding * 2)
            val y = padding + ((maxLat - point.latitude) / latRange).toFloat() * (size.height - padding * 2)
            Offset(x, y)
        }
        val path = Path()
        mapped.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        if (loop) {
            path.lineTo(mapped.first().x, mapped.first().y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        mapped.forEachIndexed { index, point ->
            val firstOrLast = index == 0 || index == mapped.lastIndex
            drawCircle(
                color = if (firstOrLast) Signal else Ink,
                radius = if (firstOrLast) 4.dp.toPx() else 2.5.dp.toPx(),
                center = point,
            )
        }
    }
}

@Composable
fun SensorWave(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        drawLine(
            Line.copy(alpha = 0.5f),
            Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f),
            0.6.dp.toPx(),
        )
        if (values.size < 2) return@Canvas
        val minValue = values.minOrNull() ?: 0f
        val maxValue = values.maxOrNull() ?: 1f
        val range = (maxValue - minValue).coerceAtLeast(0.001f)
        val path = Path()
        values.takeLast(48).forEachIndexed { index, value ->
            val x = size.width * index / (values.takeLast(48).size - 1).coerceAtLeast(1)
            val y = size.height * (0.88f - (value - minValue) / range * 0.76f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun CompassBadge(degrees: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(PaperHigh.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            withTransform({ rotate(degrees, center) }) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width * 0.75f, size.height)
                    lineTo(size.width / 2f, size.height * 0.72f)
                    lineTo(size.width * 0.25f, size.height)
                    close()
                }
                drawPath(path, Signal)
            }
        }
        Spacer(Modifier.width(5.dp))
        Text("%03d°".format(degrees), style = MonoTiny, color = InkSoft)
    }
}
