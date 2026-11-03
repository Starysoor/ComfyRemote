package com.comfyremote.app.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.comfyremote.app.R
import java.io.File

/** Saves generated images into the phone gallery (Pictures/ComfyRemote). */
class GalleryExporter(private val context: Context) {
    /** Returns the public Uri of the saved image. */
    fun savePng(bitmap: Bitmap): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "iris_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Iris")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create media file")
        return try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open media output")
            output.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    throw IllegalStateException("Could not encode image")
                }
            }
            uri
        } catch (t: Throwable) {
            deleteQuietly(uri)
            throw t
        }
    }

    /** Copies a generated image/video file into the phone gallery. */
    fun saveMediaFile(path: String): android.net.Uri {
        val file = File(path)
        if (!file.isFile) throw IllegalStateException("Media file does not exist")
        val ext = MediaTypeResolver.extension(path)
        val kind = MediaTypeResolver.kind(path)
        val mime = MediaTypeResolver.mimeType(path)
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "iris_${System.currentTimeMillis()}.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                when (kind) {
                    MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
                    MediaKind.AUDIO -> Environment.DIRECTORY_MUSIC
                    MediaKind.IMAGE -> Environment.DIRECTORY_PICTURES
                } + "/Iris",
            )
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create media file")
        return try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open media output")
            out.use { target -> file.inputStream().use { it.copyTo(target) } }
            uri
        } catch (t: Throwable) {
            deleteQuietly(uri)
            throw t
        }
    }

    private fun deleteQuietly(uri: android.net.Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun savedMessage(): String = context.getString(R.string.gallery_saved)
    fun failMessage(error: Throwable): String = context.getString(R.string.gallery_save_failed, error.message ?: "error")

    companion object {
        fun shareFile(context: Context, path: String): File? {
            val file = File(path)
            return if (file.isFile) file else null
        }
    }
}
