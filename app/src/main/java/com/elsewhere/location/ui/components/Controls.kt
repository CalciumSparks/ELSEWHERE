package com.elsewhere.location.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.LineStrong
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperDeep
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SystemSans

@Composable
fun SurveyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        Text(label.uppercase(), style = MonoLabel, color = Muted, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Faint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    singleLine = singleLine,
                    cursorBrush = SolidColor(Signal),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onImeAction?.invoke()
                        },
                        onSearch = {
                            focusManager.clearFocus()
                            onImeAction?.invoke()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
fun <T> SurveySegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(if (dark) Paper.copy(alpha = 0.08f) else PaperLow.copy(alpha = 0.7f))
            .border(1.dp, if (dark) Paper.copy(alpha = 0.12f) else Line.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val bg by animateColorAsState(
                if (active) {
                    if (dark) PaperHigh else Ink
                } else {
                    Color.Transparent
                },
                label = "segment",
            )
            val fg = when {
                active && dark -> Ink
                active -> PaperHigh
                dark -> Paper.copy(alpha = 0.66f)
                else -> Muted
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MonoTiny.copy(letterSpacing = 0.4.sp),
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SurveySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.height(32.dp),
        colors = SliderDefaults.colors(
            thumbColor = Signal,
            activeTrackColor = Ink,
            inactiveTrackColor = Line,
            disabledThumbColor = Faint,
            disabledActiveTrackColor = Faint.copy(alpha = 0.3f),
            disabledInactiveTrackColor = Line.copy(alpha = 0.4f),
        ),
        thumb = {
            Box(
                Modifier
                    .size(12.dp)
                    .background(Signal, RoundedCornerShape(1.dp))
                    .border(1.dp, Ink, RoundedCornerShape(1.dp)),
            )
        },
    )
}

@Composable
fun SpeedStepper(
    value: Float,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    display: String,
    enabled: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(EIcon.Minus, "减速", onDecrease, enabled)
        Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(display, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = Ink)
            Text("M/S", style = MonoTiny, color = Muted)
        }
        StepButton(EIcon.Plus, "加速", onIncrease, enabled)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, if (enabled) LineStrong else Line.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Ink else Faint, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    dark: Boolean = false,
    danger: Boolean = false,
) {
    val background = when {
        !enabled -> PaperDeep
        danger -> Signal
        dark -> PaperHigh
        else -> Ink
    }
    val foreground = when {
        !enabled -> Faint
        danger -> PaperHigh
        dark -> Ink
        else -> PaperHigh
    }
    val shape = CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
                color = foreground,
            )
            Spacer(Modifier.width(9.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = SystemSans,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                letterSpacing = 0.8.sp,
            ),
            color = foreground,
        )
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, if (enabled) LineStrong else Line.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) Ink else Faint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MonoTiny.copy(letterSpacing = 0.4.sp), color = if (enabled) Ink else Faint)
    }
}

@Composable
fun TinyActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    dark: Boolean = false,
) {
    val background = when {
        selected && dark -> PaperHigh
        selected -> Ink
        else -> Color.Transparent
    }
    val borderColor = when {
        dark -> PaperHigh.copy(alpha = if (selected) 0.9f else 0.24f)
        selected -> Ink
        else -> Line
    }
    val contentColor = when {
        dark && selected -> Ink
        dark -> PaperHigh.copy(alpha = 0.82f)
        selected -> PaperHigh
        else -> InkSoft
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MonoTiny.copy(letterSpacing = 0.3.sp),
            color = contentColor,
        )
    }
}

@Composable
fun ValueHeader(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
    unit: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label.uppercase(), style = MonoLabel, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = valueColor)
        if (unit != null) {
            Spacer(Modifier.width(3.dp))
            Text(unit, style = MonoTiny, color = Muted)
        }
    }
}

@Composable
fun KeyValueLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(value, style = MonoTiny.copy(letterSpacing = 0.2.sp), color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
fun NoticeBlock(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Warning,
    title: String? = null,
) {
    val accent = when (tone) {
        StatusTone.Ready -> Ink
        StatusTone.Warning -> Signal
        StatusTone.Idle -> LineStrong
        StatusTone.Danger -> Signal
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PaperLow.copy(alpha = 0.55f))
            .border(1.dp, Line.copy(alpha = 0.7f))
            .padding(12.dp),
    ) {
        Box(Modifier.width(2.dp).heightIn(min = 36.dp).background(accent))
        Spacer(Modifier.width(10.dp))
        Column {
            if (title != null) {
                Text(title, style = MonoTiny.copy(letterSpacing = 0.5.sp), color = accent)
                Spacer(Modifier.height(3.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
fun LoadingLine(
    modifier: Modifier = Modifier,
    color: Color = Signal,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Transparent, color, Color.Transparent),
                ),
            ),
    )
}
