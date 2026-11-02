package com.comfyremote.app.feature.generate

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptSourceResolverTest {
    private val positive = PromptField(
        key = "1:text",
        nodeId = "1",
        input = "text",
        label = "Positive Prompt · text",
        kind = FieldKind.TEXT,
        value = "default positive",
        multiline = true,
    )
    private val negative = PromptField(
        key = "2:text",
        nodeId = "2",
        input = "text",
        label = "Negative Prompt · text",
        kind = FieldKind.TEXT,
        value = "default negative",
        multiline = true,
    )

    @Test
    fun negativeSkillOnPositiveFieldUsesCurrentPositiveText() {
        val draft = WorkflowDraft("wf.json", "wf", listOf(positive, negative))
        assertEquals("fresh positive", resolveNegativePromptSource(draft, positive.key, "fresh positive"))
    }

    @Test
    fun negativeSkillOnNegativeFieldReadsPositiveField() {
        val draft = WorkflowDraft(
            "wf.json",
            "wf",
            listOf(positive, negative),
            values = mapOf(positive.key to "edited positive", negative.key to "edited negative"),
        )
        assertEquals("edited positive", resolveNegativePromptSource(draft, negative.key, "edited negative"))
    }

    @Test
    fun negativeSkillOnNegativeFieldRequiresNonBlankPositiveText() {
        val blankPositive = positive.copy(value = "")
        val draft = WorkflowDraft("wf.json", "wf", listOf(blankPositive, negative))
        assertNull(resolveNegativePromptSource(draft, negative.key, "existing negative"))
    }
}
