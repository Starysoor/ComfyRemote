package com.comfyremote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.ui.Iris
import com.comfyremote.app.ui.Sizes
import com.comfyremote.app.ui.Spacing

/**
 * The one iridescent button per screen: Generate, Connect, Run shot.
 * Shows a spinner instead of the label while [loading].
 */
@Composable
fun IrisPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = Sizes.primaryButton,
) {
    val iris = Iris.colors
    val active = enabled && !loading
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(
                if (enabled) iris.brand
                else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh))
            )
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = iris.onBrand, strokeWidth = 2.dp)
        } else {
            val fg = if (enabled) iris.onBrand else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)) }
                Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}

/** Neutral secondary action, same footprint as the primary button. */
@Composable
fun IrisSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    height: Dp = Sizes.primaryButton,
) {
    val fg = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.sm)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small tonal pill used for inline actions (Pick audio, Save, Share). */
@Composable
fun IrisTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(17.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Icon + caption action, laid out vertically. Used for result action rows
 * (Share / Save / Make video / Re-run) so they read as a toolbar.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Pinned footer for a screen's main action. Draws a soft fade from the
 * background so list content scrolls underneath cleanly, and pads for the
 * navigation bar.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.35f to bg.copy(alpha = 0.92f), 1f to bg))
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.lg, bottom = Spacing.md),
    ) { content() }
}
