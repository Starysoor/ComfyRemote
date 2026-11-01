package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.WorkflowConverter
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
    fun fixedSeedSubmissionKeepsUserPromptImageAndSeed() {
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
    fun randomSeedSubmissionStillKeepsUserPromptAndUsesGeneratedSeed() {
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
