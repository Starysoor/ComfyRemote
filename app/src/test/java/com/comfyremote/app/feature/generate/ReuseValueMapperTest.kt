package com.comfyremote.app.feature.generate

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.data.cloud.CloudWorkflowSpec
import com.comfyremote.app.data.repository.DraftTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReuseValueMapperTest {

    private fun field(
        key: String,
        input: String,
        label: String,
        kind: FieldKind,
        value: String = "",
    ) = PromptField(
        key = key,
        nodeId = key.substringBefore(':'),
        input = input,
        label = label,
        kind = kind,
        value = value,
    )

    private fun transfer(
        path: String,
        values: Map<String, String>,
        fields: List<PromptField> = emptyList(),
    ) = DraftTransfer(
        workflowPath = path,
        workflowName = "source",
        values = values,
        randomSeed = true,
        sourceFields = fields,
        reuseAcrossWorkflows = true,
    )

    @Test
    fun sameWorkflowKeepsRawKeys() {
        val values = mapOf("6:text" to "castle")
        val target = WorkflowDraft(
            workflowPath = "wf-a.json",
            workflowName = "same",
            fields = listOf(field("10:text", "text", "Prompt", FieldKind.TEXT)),
        )

        val result = ReuseValueMapper.map(transfer("wf-a.json", values), target)

        assertEquals(values, result.values)
        assertEquals(0, result.unmapped)
    }

    @Test
    fun crossWorkflowMapsPositiveNegativeAndSeedByRole() {
        val sourceFields = listOf(
            field("6:text", "text", "Positive Prompt", FieldKind.TEXT),
            field("7:text", "text", "Negative Prompt", FieldKind.TEXT),
            field("3:seed", "seed", "Seed", FieldKind.SEED),
        )
        val target = WorkflowDraft(
            workflowPath = "wf-b.json",
            workflowName = "target",
            fields = listOf(
                field("10:text", "text", "Positive Prompt", FieldKind.TEXT),
                field("11:text", "text", "Negative Prompt", FieldKind.TEXT),
                field("12:seed", "seed", "Seed", FieldKind.SEED),
            ),
        )

        val result = ReuseValueMapper.map(
            transfer(
                "wf-a.json",
                mapOf("6:text" to "castle", "7:text" to "blurry", "3:seed" to "42"),
                sourceFields,
            ),
            target,
        )

        assertEquals("castle", result.values["10:text"])
        assertEquals("blurry", result.values["11:text"])
        assertEquals("42", result.values["12:seed"])
        assertEquals(0, result.unmapped)
    }

    @Test
    fun ambiguousTextIsSkippedWhenLegacyMetadataCannotSeparatePrompts() {
        val target = WorkflowDraft(
            workflowPath = "wf-b.json",
            workflowName = "target",
            fields = listOf(
                field("10:text", "text", "Positive Prompt", FieldKind.TEXT),
                field("11:text", "text", "Negative Prompt", FieldKind.TEXT),
            ),
        )

        val result = ReuseValueMapper.map(
            transfer("wf-a.json", mapOf("6:text" to "castle", "7:text" to "blurry")),
            target,
        )

        assertFalse(result.values.containsKey("10:text"))
        assertFalse(result.values.containsKey("11:text"))
    }

    @Test
    fun positivePromptNeverLandsInANegativeField() {
        val sourceFields = listOf(field("6:text", "text", "Positive Prompt", FieldKind.TEXT))
        val target = WorkflowDraft(
            workflowPath = "wf-b.json",
            workflowName = "target",
            fields = listOf(field("11:text", "text", "Negative Prompt", FieldKind.TEXT)),
        )

        val result = ReuseValueMapper.map(
            transfer("wf-a.json", mapOf("6:text" to "castle"), sourceFields),
            target,
        )

        assertFalse(result.values.containsKey("11:text"))
        assertEquals(1, result.unmapped)
    }

    @Test
    fun cloudKeysStayStableAcrossCloudWorkflows() {
        val target = WorkflowDraft(
            workflowPath = "autodl:target",
            workflowName = "cloud",
            fields = listOf(field("prompt", "prompt", "Prompt", FieldKind.TEXT)),
            cloud = CloudWorkflowSpec(
                id = "target",
                name = "Cloud",
                kind = "video",
                description = "",
                rules = emptyList(),
            ),
        )

        val result = ReuseValueMapper.map(
            transfer("autodl:source", mapOf("prompt" to "a castle")),
            target,
        )

        assertEquals("a castle", result.values["prompt"])
    }

    @Test
    fun localImagesStayInTheComfyUiNamespace() {
        val sourceFields = listOf(field("9:image", "image", "Reference", FieldKind.IMAGE))
        val target = WorkflowDraft(
            workflowPath = "wf-b.json",
            workflowName = "target",
            fields = listOf(field("20:image", "image", "Reference", FieldKind.IMAGE)),
        )

        val local = ReuseValueMapper.map(
            transfer("wf-a.json", mapOf("9:image" to "ref.jpg"), sourceFields),
            target,
        )
        val cloudPath = ReuseValueMapper.map(
            transfer("wf-a.json", mapOf("9:image" to "local:/tmp/ref.jpg"), sourceFields),
            target,
        )

        assertEquals("ref.jpg", local.values["20:image"])
        assertFalse(cloudPath.values.containsKey("20:image"))
    }

    @Test
    fun cloudImageValuesMapToCloudTargets() {
        val sourceFields = listOf(field("9:image", "image", "Reference", FieldKind.IMAGE))
        val target = WorkflowDraft(
            workflowPath = "autodl:target",
            workflowName = "cloud",
            fields = listOf(field("ref_image_1", "ref_image_1", "Reference", FieldKind.IMAGE)),
            cloud = CloudWorkflowSpec(
                id = "target",
                name = "Cloud",
                kind = "video",
                description = "",
                rules = emptyList(),
            ),
        )

        val result = ReuseValueMapper.map(
            transfer("wf-a.json", mapOf("9:image" to "local:/tmp/ref.jpg"), sourceFields),
            target,
        )

        assertEquals("local:/tmp/ref.jpg", result.values["ref_image_1"])
    }
}
