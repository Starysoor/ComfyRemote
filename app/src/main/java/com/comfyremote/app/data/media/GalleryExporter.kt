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
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return uri
    }

    /** Copies a generated image/video file into the phone gallery. */
    fun saveMediaFile(path: String): android.net.Uri {
        val file = File(path)
        if (!file.isFile) throw IllegalStateException("Media file does not exist")
        val ext = file.extension.lowercase().ifBlank { "png" }
        val video = ext in setOf("mp4", "webm", "mov", "m4v", "mkv")
        val audio = ext in setOf("wav", "mp3", "m4a", "ogg", "flac", "aac")
        val mime = when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "jpg", "jpeg" -> "image/jpeg"
            else -> when {
                video -> "video/*"
                audio -> "audio/*"
                else -> "image/*"
            }
        }
        val collection = when {
            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "iris_${System.currentTimeMillis()}.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                when {
                    video -> Environment.DIRECTORY_MOVIES
                    audio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_PICTURES
                } + "/Iris",
            )
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create media file")
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return uri
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
