package com.comfyremote.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.Iris

/**
 * Iridescent progress bar. `progress == null` renders an indeterminate sweep.
 */
@Composable
fun IrisProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val stops = Iris.colors.brandColors
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), label = "progress")
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val phase by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val brush = Brush.horizontalGradient(stops, startX = 0f, endX = size.width)
        if (progress == null) {
            val w = size.width * 0.4f
            val x = size.width * phase
            val start = x.coerceAtLeast(0f)
            val end = (x + w).coerceAtMost(size.width)
            if (end > start) drawRoundRect(brush, topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = r)
        } else if (animated > 0f) {
            drawRoundRect(brush, size = Size(size.width * animated, size.height), cornerRadius = r)
        }
    }
}
