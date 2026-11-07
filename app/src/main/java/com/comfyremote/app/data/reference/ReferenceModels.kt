package com.comfyremote.app.data.reference

import java.util.UUID

/** Semantic purpose of a reference image, independent of the workflow field that carries it. */
enum class ReferenceRole {
    AUTO,
    START_FRAME,
    END_FRAME,
    CHARACTER_REFERENCE,
    SCENE_REFERENCE,
    STYLE_REFERENCE,
    OUTFIT_REFERENCE,
    COMPOSITION_REFERENCE,
    ACTION_REFERENCE,
    GENERIC_REFERENCE;

    companion object {
        fun fromStorage(raw: String?): ReferenceRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

/** One reference image with its semantic role and order within a request/shot. */
data class ReferenceImage(
    val role: ReferenceRole,
    val order: Int,
    val label: String = "",
    val path: String = "",
    val fieldKey: String = "",
    val source: String = "",
)

/** Media type of a project asset; defaults to IMAGE for legacy reference-only assets. */
enum class AssetKind {
    IMAGE,
    VIDEO,
    AUDIO,
    SUBTITLE;

    companion object {
        fun fromStorage(raw: String?): AssetKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IMAGE

        /** Infers the kind from a file extension; unknown extensions stay images. */
        fun fromPath(path: String): AssetKind {
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mov", "webm", "avi", "mkv", "m4v" -> VIDEO
                "mp3", "wav", "aac", "ogg", "m4a", "flac", "opus" -> AUDIO
                "srt", "vtt", "ass" -> SUBTITLE
                else -> IMAGE
            }
        }
    }
}

/** A project-level asset (character/scene/style/etc.) that can be reused across shots. */
data class ProjectAsset(
    val id: String = UUID.randomUUID().toString(),
    val role: ReferenceRole = ReferenceRole.GENERIC_REFERENCE,
    val path: String,
    val label: String = "",
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: AssetKind = AssetKind.IMAGE,
    val tags: List<String> = emptyList(),
)

/** Infers a useful default role from a workflow input name/label. */
fun inferReferenceRole(input: String, label: String = ""): ReferenceRole {
    val text = "$input $label".lowercase()
    return when {
        text.contains("start") || text.contains("first") || text.contains("首帧") -> ReferenceRole.START_FRAME
        text.contains("end") || text.contains("last") || text.contains("尾帧") -> ReferenceRole.END_FRAME
        text.contains("character") || text.contains("人物") || text.contains("角色") -> ReferenceRole.CHARACTER_REFERENCE
        text.contains("scene") || text.contains("场景") || text.contains("背景") -> ReferenceRole.SCENE_REFERENCE
        text.contains("style") || text.contains("风格") -> ReferenceRole.STYLE_REFERENCE
        text.contains("outfit") || text.contains("clothes") || text.contains("服装") -> ReferenceRole.OUTFIT_REFERENCE
        text.contains("composition") || text.contains("构图") -> ReferenceRole.COMPOSITION_REFERENCE
        text.contains("action") || text.contains("pose") || text.contains("动作") -> ReferenceRole.ACTION_REFERENCE
        text.contains("ref") || text.contains("reference") || text.contains("参考") -> ReferenceRole.GENERIC_REFERENCE
        else -> ReferenceRole.AUTO
    }
}
