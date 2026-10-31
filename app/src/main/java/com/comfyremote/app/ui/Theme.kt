package com.comfyremote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE3B873),
    onPrimary = Color(0xFF171208),
    primaryContainer = Color(0xFF3B3222),
    onPrimaryContainer = Color(0xFFF3E3C3),
    background = Color(0xFF0E0E11),
    onBackground = Color(0xFFECECEF),
    surface = Color(0xFF151519),
    onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF1D1D23),
    onSurfaceVariant = Color(0xFF9B9BA5),
    outline = Color(0xFF2C2C34),
    outlineVariant = Color(0xFF2C2C34),
    error = Color(0xFFF28B82),
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFF6C7C2),
)

// Warm paper + bronze: the same accent family, tuned for light backgrounds.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A6C25),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2E5C5),
    onPrimaryContainer = Color(0xFF3E3110),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1B1915),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1915),
    surfaceVariant = Color(0xFFF1EDE4),
    onSurfaceVariant = Color(0xFF6F695C),
    outline = Color(0xFFDBD4C6),
    outlineVariant = Color(0xFFDBD4C6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** Technical metadata (ids, seeds, sizes, timestamps) render in monospace. */
val Mono: FontFamily = FontFamily.Monospace

@Composable
fun ComfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = Shapes,
        content = content,
    )
}
