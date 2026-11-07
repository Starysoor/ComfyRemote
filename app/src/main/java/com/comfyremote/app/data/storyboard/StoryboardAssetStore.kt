package com.comfyremote.app.data.storyboard

import android.content.Context
import android.net.Uri
import com.comfyremote.app.data.media.ImagePreprocessor
import com.comfyremote.app.data.media.BoundedMediaInput
import com.comfyremote.app.data.reference.ProjectAsset
import com.comfyremote.app.data.reference.ReferenceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies project reference images into app-owned storage so they remain durable. */
class StoryboardAssetStore(private val context: Context) {

    suspend fun importUri(uri: Uri, role: ReferenceRole, label: String = ""): ProjectAsset {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use {
                BoundedMediaInput.read(it, BoundedMediaInput.MAX_IMAGE_BYTES)
            }
        } ?: throw IllegalStateException("Cannot read image")
        return save(bytes, context.contentResolver.getType(uri), role, label)
    }

    suspend fun importFile(path: String, role: ReferenceRole, label: String = ""): ProjectAsset {
        val file = File(path)
        if (!file.isFile) throw IllegalStateException("Image file does not exist")
        return save(withContext(Dispatchers.IO) {
            if (file.length() > BoundedMediaInput.MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("Image source exceeds 32 MiB")
            }
            file.readBytes()
        }, null, role, label)
    }

    private suspend fun save(
        bytes: ByteArray,
        mime: String?,
        role: ReferenceRole,
        label: String,
    ): ProjectAsset {
        val (prepared, ext) = withContext(Dispatchers.IO) {
            ImagePreprocessor.prepare(bytes, mime, 1536, 88, forceJpeg = true)
        }
        val dir = File(context.filesDir, "storyboards/assets").apply { mkdirs() }
        val file = File(dir, "asset_${UUID.randomUUID()}.$ext")
        withContext(Dispatchers.IO) { file.writeBytes(prepared) }
        return ProjectAsset(
            role = role,
            path = file.absolutePath,
            label = label.ifBlank { role.name },
        )
    }
}
