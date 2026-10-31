package com.comfyremote.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Graphite + warm sand: a pro-tool palette, deliberately not "AI purple".
private val Bg = Color(0xFF0E0E11)
private val Surface = Color(0xFF151519)
private val SurfaceHi = Color(0xFF1D1D23)
private val Outline = Color(0xFF2C2C34)
private val TextHi = Color(0xFFECECEF)
private val TextLo = Color(0xFF9B9BA5)
private val Accent = Color(0xFFE3B873)
private val AccentDim = Color(0xFF3B3222)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF171208),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFF3E3C3),
    secondary = Accent,
    background = Bg,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = TextLo,
    outline = Outline,
    outlineVariant = Outline,
    error = Color(0xFFF28B82),
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFF6C7C2),
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
    MaterialTheme(colorScheme = Scheme, shapes = Shapes, content = content)
}
