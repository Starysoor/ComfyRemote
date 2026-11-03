package com.comfyremote.app.data.cloud

/** Capability labels shown next to a model in provider settings. */
object CloudModelCapabilities {
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val EDIT = "edit"
    const val IMAGE_TO_VIDEO = "i2v"
    const val TEXT_TO_VIDEO = "t2v"
    const val AUDIO = "audio"

    fun of(modelId: String, kind: String? = null): List<String> {
        val lower = modelId.lowercase()
        val tags = linkedSetOf<String>()
        if (kind == VIDEO) tags += VIDEO
        if (kind == AUDIO) tags += AUDIO
        if (kind == IMAGE) tags += IMAGE

        val looksVideo = listOf(
            "video", "wan2", "seedance", "kling", "hailuo",
            "minimax-h", "hunyuanvideo", "hunyuan-video", "cogvideo", "veo",
            "runway", "luma",
        ).any { it in lower }
        val looksAudio = listOf("audio", "tts", "speech", "voice").any { it in lower }
        if (looksAudio) tags += AUDIO else if (looksVideo) tags += VIDEO
        if (tags.isEmpty()) tags += IMAGE

        if ("edit" in lower) tags += EDIT
        if ("i2v" in lower || "image-to-video" in lower || "image2video" in lower) {
            tags += IMAGE_TO_VIDEO
        }
        if ("t2v" in lower || "text-to-video" in lower || "text2video" in lower) {
            tags += TEXT_TO_VIDEO
        }
        return tags.toList()
    }
}
