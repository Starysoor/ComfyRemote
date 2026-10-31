package com.comfyremote.app.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.comfyremote.app.ui.L10n
import java.io.File

/** Saves generated images into the phone gallery (Pictures/ComfyRemote). */
class GalleryExporter(private val context: Context) {

    /** Returns the public Uri of the saved image. */
    fun savePng(bitmap: Bitmap): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "comfy_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyRemote")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create media file")
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return uri
    }

    fun savedMessage(): String = L10n.t("已保存到 相册/ComfyRemote", "Saved to Pictures/ComfyRemote")

    fun failMessage(e: Exception): String = L10n.t("保存失败：", "Save failed: ") + (e.message ?: "error")

    companion object {
        fun shareFile(context: Context, path: String): File? {
            val f = File(path)
            return if (f.isFile) f else null
        }
    }
}
