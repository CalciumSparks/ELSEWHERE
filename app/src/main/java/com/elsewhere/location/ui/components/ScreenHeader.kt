package com.elsewhere.location.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SystemSans

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    onSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    statusText: String? = null,
    statusTone: StatusTone = StatusTone.Idle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                EIconButton(
                    icon = EIcon.Back,
                    contentDescription = "返回",
                    onClick = onBack,
                    size = 36.dp,
                    border = Line,
                    background = androidx.compose.ui.graphics.Color.Transparent,
                )
                Spacer(Modifier.width(10.dp))
                BrandLockup(compact = true)
            } else {
                BrandLockup(compact = true)
            }
            Spacer(Modifier.weight(1f))
            if (statusText != null) {
                StatusPill(text = statusText, tone = statusTone)
                Spacer(Modifier.width(7.dp))
            }
            if (onSettings != null) {
                EIconButton(
                    icon = EIcon.Sliders,
                    contentDescription = "设置",
                    onClick = onSettings,
                    size = 36.dp,
                    border = Line,
                    background = androidx.compose.ui.graphics.Color.Transparent,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(6.dp)
                    .background(Signal, RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(eyebrow.uppercase(), style = MonoLabel.copy(letterSpacing = 1.4.sp), color = Signal)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = Ink,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(14.dp))
        Hairline(color = Line.copy(alpha = 0.8f))
    }
}

@Composable
fun ScreenBackdrop(modifier: Modifier = Modifier) {
    ContourField(
        modifier = modifier,
        color = Line.copy(alpha = 0.28f),
        accent = Signal.copy(alpha = 0.10f),
    )
}

@Composable
fun SectionRule(
    index: String,
    title: String,
    english: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Hairline(color = Line.copy(alpha = 0.65f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(index, style = MonoLabel.copy(fontSize = 9.sp), color = Signal)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            Spacer(Modifier.width(7.dp))
            Text(english.uppercase(), style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

@Composable
fun SmallStatusDot(active: Boolean, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SignalDot(if (active) com.elsewhere.location.ui.theme.Live else Faint, 5.dp)
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MonoTiny.copy(fontSize = 8.sp),
            color = if (active) InkSoft else Faint,
        )
    }
}
