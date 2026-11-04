package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTaskCodecTest {
    @Test
    fun roundTripPreservesRecoverableRequestAndRuntimeState() {
        val request = GenerationRequest(
            id = "task-1",
            provider = "openai",
            providerLabel = "OpenAI",
            serverUrl = "http://10.0.0.8:8188",
            workflowPath = "portrait.json",
            workflowName = "Portrait",
            fieldValues = mapOf("1:text" to "hello", "2:seed" to "42"),
            randomSeed = false,
            nodeTitles = mapOf("2" to "Sampler"),
            fields = listOf(
                PromptField("1:text", "1", "text", "Prompt", FieldKind.TEXT, "default", multiline = true),
                PromptField("2:seed", "2", "seed", "Seed", FieldKind.SEED, "0"),
            ),
            convertedPrompt = JSONObject().put("1", JSONObject().put("class_type", "CLIPTextEncode")),
            createdAt = 123L,
            storyboardProjectId = "project-1",
            storyboardShotId = "shot-2",
        )
        val task = GenerationTask(
            taskId = "task-1",
            request = request,
            phase = GenerationPhase.QUEUED,
            promptId = "prompt-1",
            clientId = "client-1",
            queueRemaining = 3,
            queuePosition = 2,
            nodeId = "2",
            nodeTitle = "Sampler",
            progress = 0.25f,
            createdAt = 123L,
            startedAt = 124L,
            seedInfo = "42",
            seedValues = mapOf("2:seed" to "42"),
            recovered = true,
            recoveryNote = "Recovered queued task",
            batchId = "batch-a",
            batchIndex = 2,
            batchSize = 4,
        )

        val restored = GenerationTaskCodec.decode(GenerationTaskCodec.encode(listOf(task))).single()

        assertEquals(task.taskId, restored.taskId)
        assertEquals(task.phase, restored.phase)
        assertEquals(task.promptId, restored.promptId)
        assertEquals(task.queuePosition, restored.queuePosition)
        assertEquals(task.seedValues, restored.seedValues)
        assertEquals(task.request.serverUrl, restored.request.serverUrl)
        assertEquals("openai", restored.request.provider)
        assertEquals("OpenAI", restored.request.providerLabel)
        assertEquals(task.request.fieldValues, restored.request.fieldValues)
        assertEquals(FieldKind.TEXT, restored.request.fields.first().kind)
        assertEquals("CLIPTextEncode", restored.request.convertedPrompt.getJSONObject("1").getString("class_type"))
        assertEquals("project-1", restored.request.storyboardProjectId)
        assertEquals("shot-2", restored.request.storyboardShotId)
        assertTrue(restored.recovered)
        assertEquals("batch-a", restored.batchId)
        assertEquals(2, restored.batchIndex)
        assertEquals(4, restored.batchSize)
        assertNull(restored.preview)
        assertFalse(restored.isTerminal)
    }

    @Test
    fun localPendingBatchTaskRoundTripsWithoutInventingPromptId() {
        val request = GenerationRequest(
            id = "local-1",
            serverUrl = "http://server:8188",
            workflowPath = "w.json",
            workflowName = "W",
            fieldValues = emptyMap(),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = emptyList(),
            convertedPrompt = JSONObject(),
            createdAt = 10L,
        )
        val task = GenerationTask(
            taskId = "local-1",
            request = request,
            localPending = true,
            batchId = "batch-1",
            batchIndex = 1,
            batchSize = 3,
        )

        val restored = GenerationTaskCodec.decode(GenerationTaskCodec.encode(listOf(task))).single()

        assertTrue(restored.localPending)
        assertNull(restored.promptId)
        assertEquals("batch-1", restored.batchId)
        assertEquals(1, restored.batchIndex)
        assertEquals(3, restored.batchSize)
        assertTrue(restored.isActive)
    }
}
