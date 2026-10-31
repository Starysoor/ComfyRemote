package com.comfyremote.app.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Pure image transformation used before uploading reference images. */
object ImagePreprocessor {

    const val MAX_DIMENSION = 1536
    const val JPEG_QUALITY = 88

    /**
     * Returns (bytes, extension). Big camera/gallery photos are downscaled to
     * [MAX_DIMENSION] JPEG (EXIF rotation aware); small images pass through.
     */
    fun prepare(bytes: ByteArray, mime: String?): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return Pair(bytes, extFor(mime))
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= MAX_DIMENSION) return Pair(bytes, extFor(mime))

        val opts = BitmapFactory.Options().apply {
            inSampleSize = (maxDim / MAX_DIMENSION).coerceAtLeast(1)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return Pair(bytes, "jpg")
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val oriented = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } else bmp
        val bos = ByteArrayOutputStream()
        oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
        return Pair(bos.toByteArray(), "jpg")
    }

    private fun extFor(mime: String?): String =
        mime?.substringAfterLast('/', "png")?.takeIf { it.length in 2..5 } ?: "png"
}
