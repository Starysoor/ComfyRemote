package com.comfyremote.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.Iris
import com.comfyremote.app.ui.IrisType

/**
 * Filter / toggle chip. Selected and unselected share the same shape and size;
 * only colour changes, so a row of chips never jumps when selection moves.
 */
@Composable
fun IrisChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    count: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainer, label = "chipBg")
    val fg = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant),
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(16.dp)) }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            count?.let { Text(it.toString(), style = IrisType.metaSmall, color = fg.copy(alpha = 0.75f)) }
        }
    }
}

/**
 * Two-to-four option segmented control (Local / Cloud, theme mode…).
 * The selected segment lifts onto a raised surface.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(if (selected) scheme.surfaceContainerHighest else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .semantics { this.selected = selected }
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class ChipTone { Neutral, Accent, Success, Warning, Error }

/** Small status pill (DONE, RUNNING, VIDEO, ×4). Text is shown as given. */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone = ChipTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val iris = Iris.colors
    val (bg, fg) = when (tone) {
        ChipTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        ChipTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipTone.Success -> iris.successContainer to iris.onSuccessContainer
        ChipTone.Warning -> iris.warningContainer to iris.onWarningContainer
        ChipTone.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = bg, modifier = modifier) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = IrisType.metaSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
        )
    }
}

/** Back-compat overload for the original boolean API. Prefer the [ChipTone] variant. */
@Composable
fun StatusChip(text: String, active: Boolean, error: Boolean = false) {
    StatusChip(text, tone = when {
        error -> ChipTone.Error
        active -> ChipTone.Accent
        else -> ChipTone.Neutral
    })
}

/** Online / offline / busy indicator dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
