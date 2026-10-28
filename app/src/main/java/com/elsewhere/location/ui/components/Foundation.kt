package com.elsewhere.location.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.elsewhere.location.ui.theme.PaperDeep
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SystemSans
import kotlin.math.PI
import kotlin.math.sin

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
}

val PanelShape = CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp)
val TopSheetShape = CutCornerShape(topEnd = 26.dp, topStart = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

@Composable
fun ContourField(
    modifier: Modifier = Modifier,
    color: Color = Line.copy(alpha = 0.55f),
    accent: Color = Signal.copy(alpha = 0.20f),
    focus: Offset? = null,
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val step = height / 8f
        val lineStroke = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round)
        for (index in -1..9) {
            val path = Path()
            val baseY = step * index + 12.dp.toPx()
            path.moveTo(-24.dp.toPx(), baseY)
            var x = -24.dp.toPx()
            while (x < width + 24.dp.toPx()) {
                val progress = x / width.coerceAtLeast(1f)
                val y = baseY +
                    sin(progress * 3.9f * PI + index * 0.63f).toFloat() * step * 0.22f +
                    sin(progress * 1.7f * PI + index * 1.4f).toFloat() * step * 0.10f
                path.lineTo(x, y)
                x += 12.dp.toPx()
            }
            drawPath(path, color, style = lineStroke)
        }

        if (focus != null) {
            val center = Offset(width * focus.x, height * focus.y)
            repeat(3) { ring ->
                drawCircle(
                    color = accent.copy(alpha = accent.alpha / (ring + 1.6f)),
                    radius = (44 + ring * 34).dp.toPx(),
                    center = center,
                    style = Stroke(width = 0.9.dp.toPx()),
                )
            }
            drawCircle(color = Signal, radius = 3.dp.toPx(), center = center)
            drawCircle(
                color = Signal.copy(alpha = 0.25f),
                radius = 10.dp.toPx(),
                center = center,
                style = Stroke(width = 0.8.dp.toPx()),
            )
        }

        // Survey crosses sit on a loose grid, never in front of text.
        val crossColor = Ink.copy(alpha = 0.12f)
        val crossSize = 2.4.dp.toPx()
        for (cx in listOf(width * 0.13f, width * 0.39f, width * 0.82f)) {
            for (cy in listOf(height * 0.18f, height * 0.54f, height * 0.86f)) {
                drawLine(crossColor, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), 0.8.dp.toPx())
                drawLine(crossColor, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), 0.8.dp.toPx())
            }
        }
    }
}

@Composable
fun Graticule(
    modifier: Modifier = Modifier,
    color: Color = Line.copy(alpha = 0.32f),
    cells: Int = 6,
) {
    Canvas(modifier) {
        val stroke = 0.6.dp.toPx()
        for (i in 0..cells) {
            val x = size.width * i / cells
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (i in 0 until cells) {
            val y = size.height * i / cells
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = Line,
    dashed: Boolean = false,
) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0.5f),
            end = Offset(size.width, 0.5f),
            strokeWidth = 1f,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
            } else {
                null
            },
        )
    }
}

@Composable
fun InstrumentPanel(
    modifier: Modifier = Modifier,
    fill: Color = PaperHigh,
    border: Color = Line,
    shape: androidx.compose.ui.graphics.Shape = PanelShape,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = Ink,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = if (compact) 24.dp else 30.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "他乡",
                color = color,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SystemSans,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    fontSize = if (compact) 15.sp else 17.sp,
                    lineHeight = if (compact) 18.sp else 21.sp,
                ),
            )
            Text(
                text = "ELSEWHERE / 定位仪",
                color = color.copy(alpha = 0.56f),
                style = MonoTiny.copy(letterSpacing = 0.7.sp),
            )
        }
    }
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    tint: Color = Ink,
    dot: Color = Signal,
) {
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = 1.2.dp.toPx()
        drawCircle(tint, radius * 0.72f, center, style = Stroke(stroke))
        drawLine(tint, Offset(center.x, 0f), Offset(center.x, radius * 0.42f), stroke)
        drawLine(tint, Offset(center.x, this.size.height - radius * 0.42f), Offset(center.x, this.size.height), stroke)
        drawLine(tint, Offset(0f, center.y), Offset(radius * 0.42f, center.y), stroke)
        drawLine(tint, Offset(this.size.width - radius * 0.42f, center.y), Offset(this.size.width, center.y), stroke)
        drawCircle(dot, radius * 0.20f, center)
    }
}

@Composable
fun SurveySectionHeader(
    index: String,
    english: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(Signal),
        )
        Spacer(Modifier.width(9.dp))
        Text(index, style = MonoLabel, color = Signal)
        Spacer(Modifier.width(9.dp))
        Text(english.uppercase(), style = MonoLabel, color = Muted, letterSpacing = 1.4.sp)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
        }
    }
}

enum class StatusTone { Ready, Warning, Idle, Danger }

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val bg = when (tone) {
        StatusTone.Ready -> if (dark) Ink else Ink
        StatusTone.Warning -> Amber.copy(alpha = if (dark) 0.96f else 0.12f)
        StatusTone.Idle -> if (dark) Paper.copy(alpha = 0.10f) else PaperLow
        StatusTone.Danger -> Signal
    }
    val fg = when (tone) {
        StatusTone.Ready -> if (dark) Live else PaperHigh
        StatusTone.Warning -> if (dark) PaperHigh else Amber
        StatusTone.Idle -> if (dark) Paper.copy(alpha = 0.80f) else Muted
        StatusTone.Danger -> PaperHigh
    }
    val dotColor = when (tone) {
        StatusTone.Ready -> if (dark) Live else Live
        StatusTone.Warning -> Amber
        StatusTone.Idle -> Faint
        StatusTone.Danger -> PaperHigh
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(dotColor, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MonoTiny.copy(letterSpacing = 0.7.sp),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    background: Color = PaperHigh.copy(alpha = 0.96f),
    border: Color = Line,
    tint: Color = Ink,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) background else PaperLow)
            .border(1.dp, if (enabled) border else Line.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Faint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MonoTiny.copy(letterSpacing = 0.5.sp),
            color = if (enabled) color else Faint,
        )
    }
}

@Composable
fun SurveySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(180),
        label = "switchThumb",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) Ink else PaperDeep,
        animationSpec = tween(180),
        label = "switchTrack",
    )
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) trackColor else PaperDeep.copy(alpha = 0.55f))
            .border(
                1.dp,
                if (checked) Ink else LineStrong,
                RoundedCornerShape(50),
            )
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                stateDescription = if (checked) "开启" else "关闭"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 0.dp)
                .offset(x = thumbOffset)
                .size(16.dp)
                .background(
                    if (enabled) {
                        if (checked) Live else PaperHigh
                    } else {
                        Faint.copy(alpha = 0.7f)
                    },
                    RoundedCornerShape(50),
                ),
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing?.invoke()
    }
}

@Composable
fun SurveyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        SurveySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = title,
        )
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .size(width = 38.dp, height = 3.dp)
                .background(LineStrong, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
fun SignalDot(color: Color = Signal, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(color, RoundedCornerShape(50)),
    )
}

@Composable
fun TonalTag(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = PaperLow,
    color: Color = InkSoft,
) {
    Text(
        text = text,
        style = MonoTiny.copy(letterSpacing = 0.3.sp),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(fill)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
fun RotatedCompassTile(
    modifier: Modifier = Modifier,
    degrees: Float,
    size: Dp = 32.dp,
) {
    Canvas(modifier.size(size).rotate(degrees)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f
        drawCircle(Ink.copy(alpha = 0.14f), radius, center, style = Stroke(0.8.dp.toPx()))
        val needle = Path().apply {
            moveTo(center.x, center.y - radius * 0.82f)
            lineTo(center.x + radius * 0.20f, center.y + radius * 0.25f)
            lineTo(center.x, center.y + radius * 0.06f)
            lineTo(center.x - radius * 0.20f, center.y + radius * 0.25f)
            close()
        }
        drawPath(needle, Signal)
    }
}

@Composable
fun TinyDashedProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Signal,
) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(Line.copy(alpha = 0.65f), size = size)
        drawRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
        )
    }
}
