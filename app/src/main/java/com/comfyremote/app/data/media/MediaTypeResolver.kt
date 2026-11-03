package com.comfyremote.app.data.media

enum class MediaKind { IMAGE, VIDEO, AUDIO }

/** Single source of truth for result file type, gallery collection and share MIME. */
object MediaTypeResolver {
    private val videoExtensions = setOf("mp4", "webm", "mov", "m4v", "mkv", "avi", "3gp")
    private val audioExtensions = setOf("wav", "mp3", "m4a", "ogg", "flac", "aac")

    fun extension(path: String): String =
        path.substringAfterLast('.', "").lowercase().ifBlank { "png" }

    fun kind(path: String): MediaKind {
        val ext = extension(path)
        return when {
            ext in videoExtensions -> MediaKind.VIDEO
            ext in audioExtensions -> MediaKind.AUDIO
            else -> MediaKind.IMAGE
        }
    }

    fun isVideo(path: String): Boolean = kind(path) == MediaKind.VIDEO
    fun isAudio(path: String): Boolean = kind(path) == MediaKind.AUDIO

    fun mimeType(path: String): String {
        val ext = extension(path)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
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
            else -> when (kind(path)) {
                MediaKind.VIDEO -> "video/*"
                MediaKind.AUDIO -> "audio/*"
                MediaKind.IMAGE -> "image/*"
            }
        }
    }
}
