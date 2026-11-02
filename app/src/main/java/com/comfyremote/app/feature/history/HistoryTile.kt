package com.comfyremote.app.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.R
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.StatusChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grid tile for one result. Tap opens the detail (or toggles selection while
 * selecting); long-press or the overflow button opens the per-item menu.
 */
@Composable
internal fun HistoryTile(
    item: HistoryItem,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStartSelect: () -> Unit,
    onReuse: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val path = item.images.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape)
                    else Modifier
                )
                .combinedClickable(
                    onClick = { if (selecting) onToggle() else onOpen() },
                    onLongClick = { if (selecting) onToggle() else { menu = true } },
                ),
        ) {
            if (path != null) {
                MediaThumbnail(path, maxWidth)
            } else {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (path?.let(::mediaKind)) {
                    MediaKind.VIDEO -> StatusChip(stringResource(R.string.history_video), ChipTone.Accent)
                    MediaKind.AUDIO -> StatusChip(stringResource(R.string.history_audio), ChipTone.Accent)
                    else -> Unit
                }
                if (item.images.size > 1) StatusChip("×${item.images.size}")
            }
            when {
                selecting -> Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                        .size(24.dp),
                )
                item.favorite -> Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.history_favorite),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(4.dp).size(14.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.wfName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)),
                    style = IrisType.metaSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    TileMenu(
                        expanded = menu,
                        favorite = item.favorite,
                        onDismiss = { menu = false },
                        onFavorite = onFavorite,
                        onReuse = onReuse,
                        onStartSelect = onStartSelect,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileMenu(
    expanded: Boolean,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onReuse: () -> Unit,
    onStartSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (favorite) R.string.history_unfavorite else R.string.history_favorite)) },
            onClick = { onFavorite(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_reuse)) },
            onClick = { onReuse(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_select)) },
            onClick = { onStartSelect(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(); onDismiss() },
        )
    }
}

internal enum class MediaKind { IMAGE, VIDEO, AUDIO }

private val VideoExtensions = setOf("mp4", "webm", "mov", "m4v", "mkv", "avi", "3gp")
private val AudioExtensions = setOf("wav", "mp3", "m4a", "ogg", "flac", "aac")

/** Same extension rules as the generate feature's media helpers. */
internal fun mediaKind(path: String): MediaKind = when (path.substringAfterLast('.', "").lowercase()) {
    in VideoExtensions -> MediaKind.VIDEO
    in AudioExtensions -> MediaKind.AUDIO
    else -> MediaKind.IMAGE
}
