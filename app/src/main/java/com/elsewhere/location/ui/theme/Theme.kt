package com.elsewhere.location.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ElsewhereColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = PaperHigh,
    primaryContainer = PaperLow,
    onPrimaryContainer = Ink,
    secondary = Signal,
    onSecondary = PaperHigh,
    secondaryContainer = SignalWash,
    onSecondaryContainer = SignalDeep,
    tertiary = Green,
    onTertiary = PaperHigh,
    tertiaryContainer = GreenWash,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = PaperHigh,
    onSurface = Ink,
    surfaceVariant = PaperLow,
    onSurfaceVariant = InkSoft,
    outline = LineStrong,
    outlineVariant = Line,
    error = Danger,
    onError = PaperHigh,
    errorContainer = DangerWash,
    onErrorContainer = Danger,
    scrim = Ink,
    inverseSurface = Ink,
    inverseOnSurface = PaperHigh,
    inversePrimary = Live,
)

private val ElsewhereShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp),
    extraLarge = CutCornerShape(topEnd = 26.dp, bottomStart = 26.dp),
)

@Composable
fun ElsewhereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElsewhereColorScheme,
        typography = ElsewhereTypography,
        shapes = ElsewhereShapes,
        content = content,
    )
}
