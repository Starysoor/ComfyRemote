package com.comfyremote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Iris design language — "ink + iris".
 *
 * Near-black ink surfaces let generated images carry the colour. The brand is a
 * single iridescent sweep (violet → cyan → rose) used sparingly: the primary
 * action, progress, and the selected state of navigation. Everything else is
 * neutral so the accent stays meaningful.
 */

object IrisPalette {
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22C3EE)
    val Rose = Color(0xFFF062A8)

    // Dark (primary experience)
    val Ink = Color(0xFF0B0B10)
    val Ink1 = Color(0xFF121218)
    val Ink2 = Color(0xFF191921)
    val Ink3 = Color(0xFF21212B)
    val Ink4 = Color(0xFF2A2A36)
    val Line = Color(0xFF2A2A36)
    val LineSoft = Color(0xFF1E1E27)
    val TextHi = Color(0xFFEDEDF3)
    val TextLo = Color(0xFF9A9AAE)

    // Light
    val Paper = Color(0xFFF6F6F9)
    val Paper1 = Color(0xFFFFFFFF)
    val Paper2 = Color(0xFFEFEFF4)
    val Paper3 = Color(0xFFE7E7EE)
    val Paper4 = Color(0xFFDDDDE6)
    val LineLight = Color(0xFFDCDCE6)
    val LineLightSoft = Color(0xFFE9E9F0)
    val TextHiLight = Color(0xFF14141B)
    val TextLoLight = Color(0xFF5F5F73)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4A0FF),
    onPrimary = Color(0xFF1A0E45),
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF6FDDF5),
    onSecondary = Color(0xFF00313C),
    secondaryContainer = Color(0xFF113845),
    onSecondaryContainer = Color(0xFFC8F3FC),
    tertiary = Color(0xFFF79CC8),
    onTertiary = Color(0xFF4A0F2E),
    tertiaryContainer = Color(0xFF45203A),
    onTertiaryContainer = Color(0xFFFFD9EA),
    background = IrisPalette.Ink,
    onBackground = IrisPalette.TextHi,
    surface = IrisPalette.Ink,
    onSurface = IrisPalette.TextHi,
    surfaceVariant = IrisPalette.Ink2,
    onSurfaceVariant = IrisPalette.TextLo,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF07070B),
    surfaceContainerLow = IrisPalette.Ink1,
    surfaceContainer = IrisPalette.Ink2,
    surfaceContainerHigh = IrisPalette.Ink3,
    surfaceContainerHighest = IrisPalette.Ink4,
    surfaceBright = IrisPalette.Ink4,
    surfaceDim = IrisPalette.Ink,
    inverseSurface = IrisPalette.TextHi,
    inverseOnSurface = IrisPalette.Ink,
    inversePrimary = Color(0xFF6D4AE0),
    outline = IrisPalette.Line,
    outlineVariant = IrisPalette.LineSoft,
    scrim = Color(0xCC000000),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF4A0B0B),
    errorContainer = Color(0xFF3A1D22),
    onErrorContainer = Color(0xFFFFD2D2),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D4AE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FF),
    onPrimaryContainer = Color(0xFF23104F),
    secondary = Color(0xFF0B8BA3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F4FB),
    onSecondaryContainer = Color(0xFF003642),
    tertiary = Color(0xFFC93A80),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0EE),
    onTertiaryContainer = Color(0xFF4A0F2E),
    background = IrisPalette.Paper,
    onBackground = IrisPalette.TextHiLight,
    surface = IrisPalette.Paper,
    onSurface = IrisPalette.TextHiLight,
    surfaceVariant = IrisPalette.Paper2,
    onSurfaceVariant = IrisPalette.TextLoLight,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = IrisPalette.Paper1,
    surfaceContainer = IrisPalette.Paper1,
    surfaceContainerHigh = IrisPalette.Paper2,
    surfaceContainerHighest = IrisPalette.Paper3,
    surfaceBright = Color.White,
    surfaceDim = IrisPalette.Paper3,
    inverseSurface = Color(0xFF1C1C24),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFFB4A0FF),
    outline = IrisPalette.LineLight,
    outlineVariant = IrisPalette.LineLightSoft,
    scrim = Color(0x99000000),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E3),
    onErrorContainer = Color(0xFF5A0B0B),
)

/** Semantic colours Material 3 does not model: status tones and the brand sweep. */
@Immutable
data class IrisColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val brand: Brush,
    val brandColors: List<Color>,
    val onBrand: Color,
    val isDark: Boolean,
)

private val BrandStops = listOf(IrisPalette.Violet, IrisPalette.Cyan, IrisPalette.Rose)

private val DarkIris = IrisColors(
    success = Color(0xFF5EEAD4),
    successContainer = Color(0xFF0F3330),
    onSuccessContainer = Color(0xFFC6F7EE),
    warning = Color(0xFFFBC765),
    warningContainer = Color(0xFF3A2C10),
    onWarningContainer = Color(0xFFFFE9BD),
    brand = Brush.linearGradient(BrandStops),
    brandColors = BrandStops,
    onBrand = Color.White,
    isDark = true,
)

private val LightIris = IrisColors(
    success = Color(0xFF0F8F7E),
    successContainer = Color(0xFFD7F5EF),
    onSuccessContainer = Color(0xFF053A33),
    warning = Color(0xFFB7791F),
    warningContainer = Color(0xFFFFF0D2),
    onWarningContainer = Color(0xFF452C05),
    brand = Brush.linearGradient(BrandStops),
    brandColors = BrandStops,
    onBrand = Color.White,
    isDark = false,
)

val LocalIrisColors = staticCompositionLocalOf { DarkIris }

/** Technical metadata (ids, seeds, sizes, timestamps) render in monospace. */
val Mono: FontFamily = FontFamily.Monospace

private val IrisTypography = Typography(
    // Screen titles on top-level destinations.
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val IrisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Extra text styles that sit outside the Material scale. */
object IrisType {
    /** Monospace metadata: seeds, sizes, timestamps, queue counters. */
    val meta = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp)
    val metaSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, lineHeight = 13.sp)
    /** Small uppercase overline used above section content. */
    val overline = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

object Iris {
    val colors: IrisColors
        @Composable @ReadOnlyComposable get() = LocalIrisColors.current
    val scheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}

@Composable
fun isIrisDark(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun IrisTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = isIrisDark(themeMode)
    CompositionLocalProvider(LocalIrisColors provides if (dark) DarkIris else LightIris) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = IrisTypography,
            shapes = IrisShapes,
            content = content,
        )
    }
}
