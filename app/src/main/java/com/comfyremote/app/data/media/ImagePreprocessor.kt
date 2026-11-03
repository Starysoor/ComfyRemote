package com.comfyremote.app.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Pure image transformation used before uploading reference images. */
object ImagePreprocessor {

    const val MAX_DIMENSION = 1536
    const val JPEG_QUALITY = 88

    /**
     * Returns (bytes, extension). Big camera/gallery photos are downscaled to
     * [maxDimension] JPEG (EXIF rotation aware); small images pass through.
     */
    fun prepare(
        bytes: ByteArray,
        mime: String?,
        maxDimension: Int = MAX_DIMENSION,
        jpegQuality: Int = JPEG_QUALITY,
        /** Cloud APIs expect a predictable MIME type; always re-encode then. */
        forceJpeg: Boolean = false,
    ): Pair<ByteArray, String> {
        val limit = maxDimension.coerceAtLeast(256)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) {
            if (forceJpeg) throw IllegalArgumentException("无法解码图片，请换一张 JPG/PNG")
            return Pair(bytes, extFor(mime))
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= limit && !forceJpeg) return Pair(bytes, extFor(mime))

        val opts = BitmapFactory.Options().apply {
            inSampleSize = (maxDim / limit).coerceAtLeast(1)
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (bmp == null) {
            if (forceJpeg) throw IllegalArgumentException("无法解码图片，请换一张 JPG/PNG")
            return Pair(bytes, extFor(mime))
        }
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
        // inSampleSize only rounds down to powers of two, so enforce the limit exactly.
        val orientedMax = maxOf(oriented.width, oriented.height)
        val scaled = if (orientedMax > limit) {
            val ratio = limit.toFloat() / orientedMax
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * ratio).roundToInt().coerceAtLeast(1),
                (oriented.height * ratio).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            oriented
        }
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bos)
        return Pair(bos.toByteArray(), "jpg")
    }

    private fun extFor(mime: String?): String =
        mime?.substringAfterLast('/', "png")?.takeIf { it.length in 2..5 } ?: "png"
}
