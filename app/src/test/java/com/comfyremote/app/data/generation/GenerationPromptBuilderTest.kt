package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.WorkflowConverter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPromptBuilderTest {

    private fun request(
        randomSeed: Boolean,
        values: Map<String, String>,
    ): GenerationRequest {
        val prompt = JSONObject()
            .put(
                "1",
                JSONObject()
                    .put("class_type", "CLIPTextEncode")
                    .put("inputs", JSONObject().put("text", "workflow default"))
            )
            .put(
                "2",
                JSONObject()
                    .put("class_type", "KSampler")
                    .put("inputs", JSONObject().put("seed", 123L))
            )
            .put(
                "3",
                JSONObject()
                    .put("class_type", "LoadImage")
                    .put("inputs", JSONObject().put("image", "default.png"))
            )

        val fields = listOf(
            PromptField("1:text", "1", "text", "Prompt", FieldKind.TEXT, "workflow default", multiline = true),
            PromptField("2:seed", "2", "seed", "Seed", FieldKind.SEED, "123"),
            PromptField("3:image", "3", "image", "Reference", FieldKind.IMAGE, "default.png"),
        )

        return GenerationRequest(
            id = "test-request",
            serverUrl = "http://127.0.0.1:8188",
            workflowPath = "test.json",
            workflowName = "Regression fixture",
            fieldValues = values,
            randomSeed = randomSeed,
            nodeTitles = emptyMap(),
            fields = fields,
            convertedPrompt = prompt,
            createdAt = 1L,
        )
    }

    @Test
    fun fixedSeedSubmissionKeepsUserPromptImageAndSeed() = runBlocking {
        val req = request(
            randomSeed = false,
            values = mapOf(
                "1:text" to "user typed prompt",
                "2:seed" to "777",
                "3:image" to "uploaded-reference.jpg",
            )
        )

        val (prompt, seeds) = GenerationPromptBuilder.build(req)

        assertEquals("user typed prompt", prompt.getJSONObject("1").getJSONObject("inputs").getString("text"))
        assertEquals(777L, prompt.getJSONObject("2").getJSONObject("inputs").getLong("seed"))
        assertEquals("uploaded-reference.jpg", prompt.getJSONObject("3").getJSONObject("inputs").getString("image"))
        assertEquals("777", seeds["2:seed"])
    }

    @Test
    fun randomSeedSubmissionStillKeepsUserPromptAndUsesGeneratedSeed() = runBlocking {
        val req = request(
            randomSeed = true,
            values = mapOf(
                "1:text" to "do not drop this prompt",
                "2:seed" to "777",
            )
        )

        val (prompt, seeds) = GenerationPromptBuilder.build(req)
        val submittedSeed = prompt.getJSONObject("2").getJSONObject("inputs").getLong("seed")

        assertEquals("do not drop this prompt", prompt.getJSONObject("1").getJSONObject("inputs").getString("text"))
        assertTrue(submittedSeed in 0 until WorkflowConverter.SEED_MAX)
        assertEquals(submittedSeed.toString(), seeds["2:seed"])
    }

    @Test
    fun nonAutoDlCloudProvidersUseTheCloudPromptBuilder() = runBlocking {
        val spec = com.comfyremote.app.data.cloud.CloudWorkflowSpec(
            id = "black-forest-labs/flux-dev",
            name = "Flux Dev",
            kind = "image",
            description = "",
            rules = listOf(
                com.comfyremote.app.data.cloud.CloudInputRule("prompt", "prompt", required = true),
                com.comfyremote.app.data.cloud.CloudInputRule(
                    "steps", "integer", required = false, defaultValue = "20",
                ),
                com.comfyremote.app.data.cloud.CloudInputRule(
                    "watermark", "boolean", required = false, defaultValue = "false",
                ),
                com.comfyremote.app.data.cloud.CloudInputRule("image", "image", required = false),
            ),
        )
        val fields = com.comfyremote.app.data.cloud.CloudWorkflowMapping.fields(spec)
        val request = GenerationRequest(
            id = "replicate-request",
            provider = "replicate",
            serverUrl = "https://api.replicate.com/v1",
            workflowPath = "replicate:black-forest-labs/flux-dev",
            workflowName = "Flux Dev",
            fieldValues = mapOf(
                "prompt" to "a red fox",
                "steps" to "30",
                "watermark" to "true",
                "image" to "local:/tmp/ref.png",
            ),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = fields,
            convertedPrompt = spec.toJson(),
            createdAt = 1L,
        )

        val (body, _) = GenerationPromptBuilder.build(request) { "data:image/png;base64,AA==" }

        assertEquals("a red fox", body.getString("prompt"))
        assertEquals(30L, body.getLong("steps"))
        assertEquals(true, body.getBoolean("watermark"))
        assertEquals("data:image/png;base64,AA==", body.getString("image"))
    }

    @Test
    fun randomCloudSeedRespectsTheProviderMaximum() = runBlocking {
        val max = 9_999_999_999L
        val spec = com.comfyremote.app.data.cloud.CloudWorkflowSpec(
            id = "Kwai-Kolors/Kolors",
            name = "Kolors",
            kind = "image",
            description = "",
            rules = listOf(
                com.comfyremote.app.data.cloud.CloudInputRule("prompt", "prompt", required = true),
                com.comfyremote.app.data.cloud.CloudInputRule(
                    "seed", "integer", required = false, max = max.toDouble(),
                ),
            ),
        )
        val fields = com.comfyremote.app.data.cloud.CloudWorkflowMapping.fields(spec)
        val request = GenerationRequest(
            id = "siliconflow-seed",
            provider = "siliconflow",
            serverUrl = "https://api.siliconflow.cn/v1",
            workflowPath = "siliconflow:Kwai-Kolors/Kolors",
            workflowName = "Kolors",
            fieldValues = mapOf("prompt" to "a fox"),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = fields,
            convertedPrompt = spec.toJson(),
            createdAt = 1L,
        )

        val (body, seeds) = GenerationPromptBuilder.build(request)
        val seed = body.getLong("seed")

        assertTrue(seed in 1L..max)
        assertEquals(seed.toString(), seeds["seed"])
    }

    @Test
    fun aFilledButUnreadableOptionalReferenceBlocksSubmission() = runBlocking {
        val spec = com.comfyremote.app.data.cloud.CloudWorkflowSpec(
            id = "gpt-image-2.5-sunburst",
            name = "GPT Image",
            kind = "image",
            description = "",
            rules = listOf(
                com.comfyremote.app.data.cloud.CloudInputRule("prompt", "prompt", required = true),
                com.comfyremote.app.data.cloud.CloudInputRule("image", "image", required = false),
                com.comfyremote.app.data.cloud.CloudInputRule("image2", "image", required = false),
            ),
        )
        val fields = com.comfyremote.app.data.cloud.CloudWorkflowMapping.fields(spec)
        val request = GenerationRequest(
            id = "missing-reference",
            provider = "openai",
            serverUrl = "https://api.openai.com/v1",
            workflowPath = "openai:gpt-image-2.5-sunburst",
            workflowName = "GPT Image",
            fieldValues = mapOf("prompt" to "combine", "image2" to "local:/missing.png"),
            randomSeed = false,
            nodeTitles = emptyMap(),
            fields = fields,
            convertedPrompt = spec.toJson(),
            createdAt = 1L,
        )

        val error = runCatching {
            GenerationPromptBuilder.build(request) { null }
        }.exceptionOrNull()

        assertTrue(error is com.comfyremote.app.PromptValidationException)
        assertTrue(error!!.message.orEmpty().contains("读取失败"))
    }

    @Test
    fun generationTaskTerminalStatesAreNotActive() {
        val req = request(randomSeed = false, values = emptyMap())

        for (phase in listOf(GenerationPhase.CANCELLED, GenerationPhase.SUCCEEDED, GenerationPhase.FAILED)) {
            val task = GenerationTask(taskId = phase.name, request = req, phase = phase)
            assertTrue(task.isTerminal)
            assertFalse(task.isActive)
        }

        for (phase in listOf(
            GenerationPhase.PREPARING,
            GenerationPhase.QUEUEING,
            GenerationPhase.QUEUED,
            GenerationPhase.RUNNING,
            GenerationPhase.CANCELLING,
        )) {
            val task = GenerationTask(taskId = phase.name, request = req, phase = phase)
            assertFalse(task.isTerminal)
            assertTrue(task.isActive)
        }
    }
}
