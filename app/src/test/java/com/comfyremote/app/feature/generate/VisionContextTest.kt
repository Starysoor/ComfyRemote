package com.comfyremote.app.feature.generate

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.data.reference.ReferenceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionContextTest {

    private fun image(key: String, input: String, role: ReferenceRole = ReferenceRole.AUTO) = PromptField(
        key = key,
        nodeId = key,
        input = input,
        label = input,
        kind = FieldKind.IMAGE,
        value = "",
        referenceRole = role,
    )

    @Test
    fun referencesCollectAllImagesWithDeclaredRoles() {
        val draft = WorkflowDraft(
            workflowPath = "wf",
            workflowName = "H3首尾帧",
            fields = listOf(
                image("1:start", "start_image", ReferenceRole.START_FRAME),
                image("2:end", "end_image", ReferenceRole.END_FRAME),
                PromptField("3:text", "3", "text", "Prompt", FieldKind.TEXT, ""),
            ),
        )
        val refs = VisionContext.references(
            draft,
            mapOf("1:start" to "a.png", "2:end" to "b.png", "3:text" to "go"),
        )

        assertEquals(2, refs.size)
        assertEquals(ReferenceRole.START_FRAME, refs[0].role)
        assertEquals(ReferenceRole.END_FRAME, refs[1].role)
    }

    @Test
    fun firstLastWorkflowAssignsRolesWhenAdapterDidNot() {
        val draft = WorkflowDraft(
            workflowPath = "wf",
            workflowName = "H3首尾帧生成视频",
            fields = listOf(
                image("1:a", "ref_image_1"),
                image("2:b", "ref_image_2"),
            ),
        )

        val refs = VisionContext.references(
            draft,
            mapOf("1:a" to "a.png", "2:b" to "b.png"),
        )

        assertEquals(ReferenceRole.START_FRAME, refs[0].role)
        assertEquals(ReferenceRole.END_FRAME, refs[1].role)
    }

    @Test
    fun videoSystemPromptDescribesTransitionNotCaptions() {
        val prompt = VisionContext.systemPrompt(
            base = "base",
            mode = VisionMode.VIDEO,
            references = listOf(
                VisionReference("1", ReferenceRole.START_FRAME, 0, "start", "a.png"),
                VisionReference("2", ReferenceRole.END_FRAME, 1, "end", "b.png"),
            ),
        )

        assertTrue(prompt.contains("hidden context"))
        assertTrue(prompt.contains("continuous shot"))
        assertTrue(prompt.contains("start and end frames"))
    }

    @Test
    fun userTextKeepsInstructionAndCurrentPrompt() {
        val text = VisionContext.userText(
            instruction = "让角色转身",
            currentPrompt = "a girl",
            mode = VisionMode.VIDEO,
            references = listOf(VisionReference("1", ReferenceRole.AUTO, 0, "", "a.png")),
        )

        assertTrue(text.contains("让角色转身"))
        assertTrue(text.contains("a girl"))
        assertTrue(text.contains("Reference images: 1"))
    }
}
