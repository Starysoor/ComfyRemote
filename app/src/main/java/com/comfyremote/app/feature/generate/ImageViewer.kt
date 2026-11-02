package com.comfyremote.app.feature.generate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun stripMarkdown(markdown: String): String = markdown
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace("**", "")
    .replace(Regex("`(.*?)`"), "$1")
    .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
    .trim()

@Composable
fun ImageViewerDialog(
    bitmap: Bitmap,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Image(
                bitmap.asImageBitmap(), null,
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onShare?.let { action -> IconButton(onClick = action) { Icon(Icons.Default.Share, L10n.t("分享", "Share"), tint = Color.White) } }
                onSave?.let { action -> IconButton(onClick = action) { Icon(Icons.Default.Add, L10n.t("保存", "Save"), tint = Color.White) } }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Text(
                L10n.t("双击复位 · 双指缩放", "Double-tap to reset · pinch to zoom"),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

fun shareImage(context: Context, path: String) {
    shareMedia(context, path)
}

fun shareMedia(context: Context, path: String) {
    val file = File(path)
    if (!file.isFile) return
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType(path)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, L10n.t("分享", "Share")))
    }
}

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "mkv", "avi", "3gp")

fun isVideoPath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

fun isAudioPath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in setOf("wav", "mp3", "m4a", "ogg", "flac", "aac")

fun isNonImageMedia(path: String): Boolean = isVideoPath(path) || isAudioPath(path)

fun mediaMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    else -> if (isAudioPath(path)) "audio/*" else "image/png"
}

@Composable
fun MediaThumbnail(
    path: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (isAudioPath(path)) {
        Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Default.PlayArrow,
                    L10n.t("音频", "Audio"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size / 2),
                )
            }
        }
    } else if (isVideoPath(path)) {
        val frame by produceState<Bitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { videoFrame(path) }
        }
        Box(modifier.size(size).background(Color.Black), contentAlignment = Alignment.Center) {
            frame?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    } else {
        val bitmap by produceState<Bitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { decodeSampled(path, (size.value * 3).toInt().coerceAtLeast(96)) }
        }
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), null, modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

/** Full-screen viewer that handles images and videos behind one entry point. */
@Composable
fun MediaViewerDialog(
    path: String,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    if (!isNonImageMedia(path)) {
        val bitmap by produceState<Bitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { decodeSampled(path, 1800) }
        }
        bitmap?.let {
            ImageViewerDialog(bitmap = it, onClose = onClose, onSave = onSave, onShare = onShare)
        }
        return
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(path)
                        val controller = MediaController(context)
                        controller.setMediaPlayer(this)
                        setMediaController(controller)
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onShare?.let { action -> IconButton(onClick = action) { Icon(Icons.Default.Share, L10n.t("分享", "Share"), tint = Color.White) } }
                onSave?.let { action -> IconButton(onClick = action) { Icon(Icons.Default.Add, L10n.t("保存", "Save"), tint = Color.White) } }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
        }
    }
}

private fun videoFrame(path: String): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        retriever.release()
    }
}.getOrNull()
