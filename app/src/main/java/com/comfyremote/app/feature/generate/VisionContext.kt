package com.comfyremote.app.feature.generate

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.data.reference.inferReferenceRole

enum class VisionMode { IMAGE, VIDEO }

data class VisionReference(
    val fieldKey: String,
    val role: ReferenceRole,
    val order: Int,
    val label: String,
    val value: String,
)

/** Builds the hidden visual context and prompt instructions for the vision rewrite. */
object VisionContext {

    fun mode(draft: WorkflowDraft): VisionMode {
        if (draft.cloud?.kind == "video") return VisionMode.VIDEO
        val name = draft.workflowName.lowercase()
        return if (listOf("video", "wan", "h3", "minimax", "i2v", "t2v", "首尾").any { it in name }) {
            VisionMode.VIDEO
        } else {
            VisionMode.IMAGE
        }
    }

    fun references(
        draft: WorkflowDraft,
        values: Map<String, String>,
        fallbackValue: String = "",
    ): List<VisionReference> {
        val firstLast = listOf("首尾", "first_last", "first-last", "start_end", "start-end")
            .any { draft.workflowName.lowercase().contains(it) }
        val refs = mutableListOf<VisionReference>()
        draft.fields.forEachIndexed { index, field ->
            if (field.kind != FieldKind.IMAGE) return@forEachIndexed
            val value = values[field.key] ?: field.value
            if (value.isBlank()) return@forEachIndexed
            val declared = if (field.referenceRole != ReferenceRole.AUTO) {
                field.referenceRole
            } else {
                inferReferenceRole(field.input, field.label)
            }
            refs += VisionReference(field.key, declared, index, field.label, value)
        }
        if (fallbackValue.isNotBlank() && refs.none { it.value == fallbackValue }) {
            refs += VisionReference("", ReferenceRole.AUTO, draft.fields.size, "", fallbackValue)
        }
        if (firstLast && refs.size >= 2) {
            if (refs.none { it.role == ReferenceRole.START_FRAME || it.role == ReferenceRole.END_FRAME }) {
                refs[0] = refs[0].copy(role = ReferenceRole.START_FRAME)
                refs[1] = refs[1].copy(role = ReferenceRole.END_FRAME)
            }
        }
        return refs
    }

    fun systemPrompt(base: String, mode: VisionMode, references: List<VisionReference>): String {
        val rules = buildString {
            append("Reference images are hidden context for your understanding; do not describe them back to the user. ")
            append("Preserve identity, face, hair, outfit, scene, lighting, composition and style unless the user explicitly asks to change them. ")
            append("Do not mechanically caption the image. Output only the final prompt, with no explanations, prefixes or quotes.")
            when (mode) {
                VisionMode.VIDEO -> append(
                    " Describe what happens and how: subject motion, camera movement and temporal progression. " +
                        "When start/end frames are present, describe one continuous shot transitioning from the start frame to the end frame; " +
                        "never say \"the first image\" or \"the second image\"."
                )
                VisionMode.IMAGE -> append(" Infer the user's intended change and keep every unmentioned element stable.")
            }
            if (references.size > 1) {
                append(" Fuse all role-tagged references into one coherent prompt instead of describing them one by one.")
            }
            if (references.any { it.role == ReferenceRole.START_FRAME } &&
                references.any { it.role == ReferenceRole.END_FRAME }
            ) {
                append(" The start and end frames define the beginning and ending of the same shot.")
            }
        }
        return base.trim().trimEnd() + "\n\n" + rules
    }

    fun userText(
        instruction: String,
        currentPrompt: String,
        mode: VisionMode,
        references: List<VisionReference>,
    ): String = buildString {
        append("Mode: ").append(if (mode == VisionMode.VIDEO) "video" else "image")
        append("\nReference images: ").append(references.size)
        references.forEach { ref ->
            append("\n- ").append(ref.role.name.lowercase().replace('_', ' '))
            if (ref.label.isNotBlank()) append(" (").append(ref.label).append(")")
        }
        append("\n\nUser request: ").append(
            instruction.trim().ifBlank { "Improve the prompt while preserving the reference images." }
        )
        if (currentPrompt.isNotBlank()) {
            append("\nCurrent prompt: ").append(currentPrompt)
        }
    }
}
