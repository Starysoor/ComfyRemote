package com.comfyremote.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.Iris
import com.comfyremote.app.ui.Spacing

/**
 * Section title with an optional trailing slot (usually a [TextAction]).
 * Titles are neutral; the accent colour is reserved for actions.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Compact accent-coloured text button used in headers and card footers. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
            Spacer(Modifier.size(Spacing.xs))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.let { Spacer(Modifier.size(Spacing.xs)); it() }
    }
}

enum class BannerTone { Info, Warning, Error, Success }

/**
 * Inline notice inside a screen: an icon, a message and an optional action.
 * Use for recoverable states ("token missing", "server offline").
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val iris = Iris.colors
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, icon) = when (tone) {
        BannerTone.Info -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Outlined.Info)
        BannerTone.Warning -> Triple(iris.warningContainer, iris.onWarningContainer, Icons.Outlined.WarningAmber)
        BannerTone.Error -> Triple(scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.ErrorOutline)
        BannerTone.Success -> Triple(iris.successContainer, iris.onSuccessContainer, Icons.Outlined.Info)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = if (actionLabel != null) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = fg) }
                Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

/** Error block with a title and optional detail text (stack/message). */
@Composable
fun ErrorCard(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    InfoBanner(
        text = detail ?: "",
        title = title,
        tone = BannerTone.Error,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
