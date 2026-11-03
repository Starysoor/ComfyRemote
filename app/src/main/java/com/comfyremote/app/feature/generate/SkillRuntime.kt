package com.comfyremote.app.feature.generate

import com.comfyremote.app.data.reference.ReferenceRole

/** Prompt scaffolding for multi-stage skills (hidden analysis -> planner -> final prompt). */
object SkillRuntime {

    const val MAX_ANALYSIS_CHARS = 4_000

    fun visionAnalysisSystemPrompt(mode: VisionMode): String = buildString {
        append("You are the visual analysis stage of a two-stage prompt pipeline. ")
        append("Study every reference image and the user's request, but DO NOT write the final prompt and DO NOT answer the user. ")
        append("Return a compact structured analysis with these headings: ")
        append("SUBJECTS, KEEP, CHANGE, ROLES, TRANSITION. ")
        append("KEEP lists identity, face, hair, outfit, scene, lighting, composition and style that must stay stable. ")
        append("CHANGE lists only what the user asked to change. ")
        append("ROLES lists each image with its role. ")
        if (mode == VisionMode.VIDEO) {
            append("TRANSITION describes how the start frame should become the end frame, including subject motion and camera movement. ")
        } else {
            append("TRANSITION is empty for image edits. ")
        }
        append("Keep the analysis under 400 words and never quote it back as the final answer.")
    }

    fun plannerSystemPrompt(baseSkill: String, mode: VisionMode, references: List<VisionReference>): String {
        val extra = buildString {
            append("You are the planner/rewrite stage. A previous vision stage produced hidden structured analysis; ")
            append("use it as internal context, never quote or mention it, and never write a caption of the reference images. ")
            append("Preserve every KEEP item unless the user explicitly asks to change it. ")
            if (references.size > 1) append("Fuse all role-tagged references into one coherent prompt. ")
            if (mode == VisionMode.VIDEO) {
                append("Write a single video prompt describing the action, camera movement and the transition from start to end frame. ")
            } else {
                append("Write a single image prompt describing the intended change while keeping unmentioned elements stable. ")
            }
            append("Output only the final prompt, no headings, explanations or quotes.")
        }
        return baseSkill.trim().trimEnd() + "\n\n" + extra
    }

    fun plannerUserText(
        request: String,
        currentPrompt: String,
        analysis: String,
        references: List<VisionReference>,
    ): String = buildString {
        append("User request: ").append(request.ifBlank { "Improve the prompt using the references." })
        if (currentPrompt.isNotBlank()) append("\nCurrent prompt: ").append(currentPrompt)
        append("\nReference roles: ")
        append(references.joinToString(", ") { it.role.name.lowercase().replace('_', ' ') })
        append("\n\nHidden visual analysis (internal only):\n")
        append(analysis.take(MAX_ANALYSIS_CHARS))
    }

    fun analysisMentionsRoles(analysis: String, references: List<VisionReference>): Boolean =
        references.all { ref ->
            ref.role == ReferenceRole.AUTO ||
                analysis.contains(ref.role.name.replace('_', ' '), ignoreCase = true) ||
                analysis.contains(ref.role.name, ignoreCase = true)
        }
}
