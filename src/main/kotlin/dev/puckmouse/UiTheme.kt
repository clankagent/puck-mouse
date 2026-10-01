package dev.puckmouse

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Flat dark neutrals; warm white is the only "brand" colour, green/amber carry state. */
object PuckColors {
    val Background = Color(0xFF18191B)
    val Surface = Color(0xFF202125)
    val Raised = Color(0xFF292A2F)
    val Selected = Color(0xFF2E2F35)
    val Line = Color(0xFF33343A)
    val Control = Color(0xFF858894)
    val Foreground = Color(0xFFEEEEF2)
    val Secondary = Color(0xFFAEB0BA)
    val Warm = Color(0xFFF3EFE6)
    val Track = Color(0xFF3A3B42)
    val Active = Color(0xFF8FD3A6)
    val Paused = Color(0xFFE8B866)
    val PausedContainer = Color(0xFF332A1C)
    val Error = Color(0xFFF2A59C)
    val ErrorContainer = Color(0xFF3A2523)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val colors = darkColorScheme(
    primary = PuckColors.Warm,
    onPrimary = PuckColors.Background,
    primaryContainer = PuckColors.Selected,
    onPrimaryContainer = PuckColors.Foreground,
    inversePrimary = PuckColors.Background,
    secondary = PuckColors.Secondary,
    onSecondary = PuckColors.Background,
    secondaryContainer = PuckColors.Selected,
    onSecondaryContainer = PuckColors.Foreground,
    tertiary = PuckColors.Active,
    onTertiary = PuckColors.Background,
    tertiaryContainer = PuckColors.Selected,
    onTertiaryContainer = PuckColors.Foreground,
    background = PuckColors.Background,
    onBackground = PuckColors.Foreground,
    surface = PuckColors.Surface,
    onSurface = PuckColors.Foreground,
    surfaceVariant = PuckColors.Raised,
    onSurfaceVariant = PuckColors.Secondary,
    surfaceTint = Color.Transparent,
    inverseSurface = PuckColors.Foreground,
    inverseOnSurface = PuckColors.Background,
    error = PuckColors.Error,
    onError = PuckColors.Background,
    errorContainer = PuckColors.ErrorContainer,
    onErrorContainer = PuckColors.Foreground,
    outline = PuckColors.Control,
    outlineVariant = PuckColors.Line,
    scrim = Color(0xCC000000),
    surfaceBright = PuckColors.Selected,
    surfaceDim = PuckColors.Background,
    surfaceContainerLowest = PuckColors.Background,
    surfaceContainerLow = Color(0xFF1C1D20),
    surfaceContainer = PuckColors.Surface,
    surfaceContainerHigh = PuckColors.Raised,
    surfaceContainerHighest = PuckColors.Selected,
)

// FontFamily.Default resolves to Segoe UI on Windows.
private val sans = FontFamily.Default
private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = sans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val typography = Typography(
    headlineSmall = style(22, 28, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(15, 22),
    bodyMedium = style(14, 20),
    bodySmall = style(13, 18),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium, 0.2),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun PuckTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)

@Composable
fun puckSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = PuckColors.Background,
    checkedTrackColor = PuckColors.Warm,
    checkedBorderColor = PuckColors.Warm,
    uncheckedThumbColor = PuckColors.Control,
    uncheckedTrackColor = PuckColors.Background,
    uncheckedBorderColor = PuckColors.Control,
)

@Composable
fun puckSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = PuckColors.Warm,
    activeTrackColor = PuckColors.Warm,
    inactiveTrackColor = PuckColors.Track,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)
