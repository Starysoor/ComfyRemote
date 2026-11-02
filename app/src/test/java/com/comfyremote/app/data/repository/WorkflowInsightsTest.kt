package com.comfyremote.app.data.repository

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInsightsTest {
    @Test
    fun detectsImageControlAndLoraCapabilities() {
        val prompt = JSONObject()
            .put("1", JSONObject().put("class_type", "LoadImage"))
            .put("2", JSONObject().put("class_type", "ControlNetApplyAdvanced"))
            .put("3", JSONObject().put("class_type", "LoraLoader"))
        val fields = listOf(
            PromptField("1:image", "1", "image", "Reference", FieldKind.IMAGE, "input.png"),
            PromptField("4:image", "4", "image", "Reference 2", FieldKind.IMAGE, "input2.png"),
        )

        val insight = WorkflowInsights.analyze(prompt, fields, emptyMap(), emptyList())

        assertTrue(WorkflowCapability.IMG2IMG in insight.capabilities)
        assertTrue(WorkflowCapability.MULTI_REFERENCE in insight.capabilities)
        assertTrue(WorkflowCapability.CONTROL in insight.capabilities)
        assertTrue(WorkflowCapability.LORA in insight.capabilities)
        assertTrue(insight.compatible)
    }

    @Test
    fun missingModelChoiceBlocksButEmptyReferenceIsOnlyWarning() {
        val prompt = JSONObject().put("1", JSONObject().put("class_type", "CheckpointLoaderSimple"))
        val fields = listOf(
            PromptField(
                key = "1:ckpt_name",
                nodeId = "1",
                input = "ckpt_name",
                label = "Checkpoint",
                kind = FieldKind.PARAM,
                value = "missing.safetensors",
                ptype = "combo",
                options = listOf("available.safetensors"),
            ),
            PromptField("2:image", "2", "image", "Reference", FieldKind.IMAGE, ""),
        )

        val insight = WorkflowInsights.analyze(prompt, fields, emptyMap(), emptyList())

        assertFalse(insight.compatible)
        assertTrue(insight.blockingIssues.any { it.message.contains("missing.safetensors") })
        assertTrue(insight.issues.any { it.message.contains("Reference") && !it.blocking })
    }

    @Test
    fun emptyReferenceAloneDoesNotBlockPossiblyOptionalImageInput() {
        val insight = WorkflowInsights.analyze(
            JSONObject().put("1", JSONObject().put("class_type", "LoadImage")),
            listOf(PromptField("1:image", "1", "image", "Optional reference", FieldKind.IMAGE, "")),
            emptyMap(),
            emptyList(),
        )

        assertTrue(insight.compatible)
        assertTrue(insight.issues.single().message.contains("Optional reference"))
    }

    @Test
    fun requiredEmptyImageBlocksGenerationButOptionalDoesNot() {
        val required = WorkflowInsights.analyze(
            JSONObject().put("1", JSONObject().put("class_type", "LoadImage")),
            listOf(
                PromptField(
                    "1:image", "1", "image", "Required reference", FieldKind.IMAGE, "",
                    required = true,
                )
            ),
            emptyMap(),
            emptyList(),
        )
        assertFalse(required.compatible)
        assertTrue(required.blockingIssues.any { it.message.contains("Required reference") })

        val optional = WorkflowInsights.analyze(
            JSONObject().put("1", JSONObject().put("class_type", "LoadImage")),
            listOf(PromptField("1:image", "1", "image", "Optional reference", FieldKind.IMAGE, "")),
            emptyMap(),
            emptyList(),
        )
        assertTrue(optional.compatible)
    }

    @Test
    fun converterUnknownNodeWarningBlocksGeneration() {
        val insight = WorkflowInsights.analyze(
            JSONObject().put("1", JSONObject().put("class_type", "KSampler")),
            emptyList(),
            emptyMap(),
            listOf("未知节点 CustomFoo #9 已跳过"),
        )

        assertFalse(insight.compatible)
    }
}
