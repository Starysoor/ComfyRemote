package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.OutputImage
import com.comfyremote.app.PromptField
import com.comfyremote.app.QueueSnapshot
import com.comfyremote.app.WsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GenerationRecoveryTest {
    @Test
    fun recoverQueuedTaskUsesQueuePositionThenCompletesFromHistory() = runBlocking {
        val backend = FakeBackend(
            history = mutableListOf(null, successEntry()),
            queue = QueueSnapshot(emptyList(), listOf("other", "prompt-1")),
        )
        val sink = FakeHistorySink()
        val updates = mutableListOf<GenerationTask>()
        val runner = GenerationRunner(backend, sink, pollDelayMs = 1, timeoutMs = 100)
        val original = task().copy(
            phase = GenerationPhase.QUEUED,
            promptId = "prompt-1",
            seedValues = mapOf("2:seed" to "42"),
        )

        val final = runner.recover(original) { updates += it }

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(final.recovered)
        assertTrue(updates.any { it.phase == GenerationPhase.QUEUED && it.queuePosition == 2 })
        assertEquals("prompt-1", sink.items.single().id)
    }

    @Test
    fun knownPendingQueueDoesNotInventRunningStateWhenWebSocketIsSilent() = runBlocking {
        val backend = FakeBackend(
            history = mutableListOf(null, successEntry()),
            queue = QueueSnapshot(emptyList(), listOf("prompt-1")),
        )
        val updates = mutableListOf<GenerationTask>()
        val final = GenerationRunner(backend, FakeHistorySink(), pollDelayMs = 1, timeoutMs = 100)
            .run(task()) { updates += it }

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(updates.any { it.phase == GenerationPhase.QUEUED && it.queuePosition == 1 })
        assertFalse(updates.any { it.phase == GenerationPhase.RUNNING })
    }

    private fun request() = GenerationRequest(
        id = "task-1",
        serverUrl = "http://server:8188",
        workflowPath = "fixture.json",
        workflowName = "Fixture",
        fieldValues = mapOf("1:text" to "hello", "2:seed" to "42"),
        randomSeed = false,
        nodeTitles = mapOf("2" to "Sampler"),
        fields = listOf(
            PromptField("1:text", "1", "text", "Prompt", FieldKind.TEXT, "default"),
            PromptField("2:seed", "2", "seed", "Seed", FieldKind.SEED, "0"),
        ),
        convertedPrompt = JSONObject()
            .put("1", JSONObject().put("class_type", "CLIPTextEncode").put("inputs", JSONObject().put("text", "default")))
            .put("2", JSONObject().put("class_type", "KSampler").put("inputs", JSONObject().put("seed", 0))),
    )

    private fun task() = GenerationTask(
        taskId = "task-1",
        request = request(),
        clientId = "client-1",
    )

    private class FakeBackend(
        private val history: MutableList<JSONObject?>,
        private val queue: QueueSnapshot,
    ) : GenerationBackend {
        override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String = "prompt-1"
        override fun wsEvents(clientId: String): Flow<WsEvent> = emptyFlow()
        override suspend fun historyEntry(promptId: String): JSONObject? =
            if (history.isEmpty()) null else history.removeAt(0)
        override suspend fun downloadImage(image: OutputImage): ByteArray = byteArrayOf(1)
        override suspend fun queueSnapshot(): QueueSnapshot = queue
    }

    private class FakeHistorySink : GenerationHistorySink {
        private val dir: File = Files.createTempDirectory("comfy-recovery-test").toFile().apply { deleteOnExit() }
        val items = mutableListOf<HistoryItem>()
        override fun newImageFile(promptId: String, index: Int): File = File(dir, "${promptId}_${index}.png")
        override fun add(item: HistoryItem) { items.removeAll { it.id == item.id }; items += item }
    }

    companion object {
        private fun successEntry(): JSONObject = JSONObject()
            .put("status", JSONObject().put("status_str", "success").put("messages", JSONArray()))
            .put("outputs", JSONObject())
    }
}
