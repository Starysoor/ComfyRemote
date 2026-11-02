package com.comfyremote.app.feature.generate

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.ui.LocalAppSettings
import com.comfyremote.app.ui.Sizes
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisTonalButton

/**
 * One reference-image input: image slot + camera tile, upload error, and the
 * "AI vision rewrite" action once an image is set.
 */
@Composable
internal fun ReferenceImageField(
    label: String,
    value: String,
    upload: ImageUploadStatus?,
    visionEnabled: Boolean,
    visionBusy: Boolean,
    loadBitmap: suspend (String) -> Bitmap?,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onClear: () -> Unit,
    onVision: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageSlotTile(
                name = value,
                uploading = upload?.phase == ImageUploadPhase.UPLOADING,
                loadBitmap = loadBitmap,
                onPick = onPick,
                onClear = onClear,
            )
            ActionTile(Icons.Outlined.PhotoCamera, stringResource(R.string.gen_camera), onCamera)
        }
        if (upload?.phase == ImageUploadPhase.FAILED) {
            Text(
                upload.message ?: stringResource(R.string.gen_upload_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (value.isNotBlank()) {
            TextButton(
                onClick = onVision,
                enabled = visionEnabled,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (visionBusy) R.string.gen_vision_busy else R.string.gen_vision_rewrite),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun tileModifier(onClick: () -> Unit): Modifier {
    val shape = MaterialTheme.shapes.medium
    return Modifier
        .size(Sizes.mediaSlot)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .clickable(role = Role.Button, onClick = onClick)
}

@Composable
private fun ImageSlotTile(
    name: String,
    uploading: Boolean,
    loadBitmap: suspend (String) -> Bitmap?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(tileModifier(onPick), contentAlignment = Alignment.Center) {
        if (name.isNotBlank()) {
            val bitmap by produceState<Bitmap?>(null, name) { value = loadBitmap(name) }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                val loaded = bitmap
                if (loaded != null) {
                    Image(loaded.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                // White reads on the scrim in both themes.
                Icon(Icons.Outlined.Close, stringResource(R.string.action_clear), tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            TileLabel(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.gen_add))
        }
        if (uploading) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, caption: String, onClick: () -> Unit) {
    Box(tileModifier(onClick), contentAlignment = Alignment.Center) { TileLabel(icon, caption) }
}

@Composable
private fun TileLabel(icon: ImageVector, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One audio input: current file (tap to preview), clear, and the picker. */
@Composable
internal fun AudioReferenceField(
    label: String,
    value: String,
    upload: ImageUploadStatus?,
    running: Boolean,
    onPreview: (String) -> Unit,
    onClear: () -> Unit,
    onPick: () -> Unit,
) {
    val compact = LocalAppSettings.current.compactUi
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IrisCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (value.isNotBlank()) {
                    val path = value.removePrefix("local:")
                    MediaThumbnail(
                        path = path,
                        size = Sizes.thumbSm,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { onPreview(path) },
                    )
                    Text(
                        audioDisplayName(value),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.action_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        stringResource(R.string.gen_no_audio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                IrisTonalButton(
                    text = stringResource(R.string.gen_pick_audio),
                    onClick = onPick,
                    enabled = !running,
                    icon = Icons.Outlined.AudioFile,
                )
            }
        }
        if (upload?.phase == ImageUploadPhase.FAILED) {
            Text(
                upload.message ?: stringResource(R.string.gen_audio_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
