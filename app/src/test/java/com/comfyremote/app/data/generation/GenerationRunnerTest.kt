package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.OutputImage
import com.comfyremote.app.PromptField
import com.comfyremote.app.WsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GenerationRunnerTest {

    private fun request(randomSeed: Boolean = false): GenerationRequest {
        val prompt = JSONObject()
            .put("1", JSONObject().put("class_type", "CLIPTextEncode").put("inputs", JSONObject().put("text", "default")))
            .put("2", JSONObject().put("class_type", "KSampler").put("inputs", JSONObject().put("seed", 123L)))
        return GenerationRequest(
            id = "req-1",
            serverUrl = "http://server:8188",
            workflowPath = "fixture.json",
            workflowName = "Fixture",
            fieldValues = mapOf("1:text" to "user prompt", "2:seed" to "777"),
            randomSeed = randomSeed,
            nodeTitles = mapOf("2" to "Sampler"),
            fields = listOf(
                PromptField("1:text", "1", "text", "Prompt", FieldKind.TEXT, "default", multiline = true),
                PromptField("2:seed", "2", "seed", "Seed", FieldKind.SEED, "123"),
            ),
            convertedPrompt = prompt,
            createdAt = 1L,
        )
    }

    private fun task(request: GenerationRequest = request()) = GenerationTask(
        taskId = request.id,
        request = request,
        clientId = "client-1",
        createdAt = 1L,
    )

    @Test
    fun successUsesRequestSnapshotPersistsHistoryBeforeSucceededAndKeepsActualSeed() = runBlocking {
        val backend = FakeBackend(history = mutableListOf(successEntry("a.png")))
        backend.downloads["a.png"] = Result.success(byteArrayOf(1, 2, 3))
        val sink = FakeHistorySink()
        val phases = mutableListOf<GenerationPhase>()
        var historyVisibleAtSuccess = false
        val runner = GenerationRunner(backend, sink, pollDelayMs = 1, timeoutMs = 100)

        val final = runner.run(task(request(randomSeed = true))) { updated ->
            phases += updated.phase
            if (updated.phase == GenerationPhase.SUCCEEDED) historyVisibleAtSuccess = sink.items.isNotEmpty()
        }

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(historyVisibleAtSuccess)
        assertEquals("user prompt", backend.capturedPrompt!!.getJSONObject("1").getJSONObject("inputs").getString("text"))
        val submittedSeed = backend.capturedPrompt!!.getJSONObject("2").getJSONObject("inputs").getLong("seed").toString()
        assertNotEquals("777", submittedSeed)
        assertEquals(submittedSeed, final.seedValues["2:seed"])
        assertEquals("prompt-1", sink.items.single().id)
        assertEquals(submittedSeed, sink.items.single().values["2:seed"])
        assertEquals(submittedSeed, sink.items.single().seeds)
        assertTrue(phases.containsAll(listOf(GenerationPhase.QUEUEING, GenerationPhase.QUEUED, GenerationPhase.SUCCEEDED)))
    }

    @Test
    fun missedExecutionStartFallsBackToRunningAndHistoryStillCompletes() = runBlocking {
        val backend = FakeBackend(history = mutableListOf(null, successEntry()))
        val sink = FakeHistorySink()
        val phases = mutableListOf<GenerationPhase>()
        val final = GenerationRunner(backend, sink, pollDelayMs = 1, timeoutMs = 100)
            .run(task()) { phases += it.phase }

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(phases.contains(GenerationPhase.RUNNING))
    }

    @Test
    fun websocketProgressAndNodeAreBestEffortButObservable() = runBlocking {
        val backend = FakeBackend(history = mutableListOf(null, successEntry()))
        backend.events = flow {
            emit(WsEvent.Json(JSONObject().put("type", "execution_start").put("data", JSONObject().put("prompt_id", "prompt-1"))))
            emit(WsEvent.Json(JSONObject().put("type", "executing").put("data", JSONObject().put("prompt_id", "prompt-1").put("node", "2"))))
            emit(WsEvent.Json(JSONObject().put("type", "progress").put("data", JSONObject().put("prompt_id", "prompt-1").put("value", 3).put("max", 4))))
        }
        val updates = mutableListOf<GenerationTask>()

        val final = GenerationRunner(backend, FakeHistorySink(), pollDelayMs = 10, timeoutMs = 200)
            .run(task()) { updates += it }

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(updates.any { it.nodeId == "2" && it.nodeTitle == "Sampler" })
        assertTrue(updates.any { it.progress != null && it.progress!! >= 0.75f })
    }

    @Test
    fun interruptedHistoryBecomesCancelled() = runBlocking {
        val final = GenerationRunner(
            FakeBackend(history = mutableListOf(cancelledEntry())),
            FakeHistorySink(),
            pollDelayMs = 1,
            timeoutMs = 100,
        ).run(task()) {}

        assertEquals(GenerationPhase.CANCELLED, final.phase)
        assertTrue(final.isTerminal)
    }

    @Test
    fun executionErrorIncludesNodeMetadata() = runBlocking {
        val final = GenerationRunner(
            FakeBackend(history = mutableListOf(errorEntry())),
            FakeHistorySink(),
            pollDelayMs = 1,
            timeoutMs = 100,
        ).run(task()) {}

        assertEquals(GenerationPhase.FAILED, final.phase)
        assertEquals(ErrorKind.EXECUTION, final.error?.kind)
        assertEquals("2", final.error?.nodeId)
        assertEquals("KSampler", final.error?.nodeType)
        assertEquals("Sampler", final.error?.nodeTitle)
        assertTrue(final.error?.message?.contains("boom") == true)
    }

    @Test
    fun historyTimeoutIsTyped() = runBlocking {
        val error = runCatching {
            GenerationRunner(
                FakeBackend(history = mutableListOf()),
                FakeHistorySink(),
                pollDelayMs = 1,
                timeoutMs = 5,
            ).run(task()) {}
        }.exceptionOrNull()

        assertTrue(error is GenerationTimeoutException)
    }

    @Test
    fun partialDownloadFailureKeepsSuccessfulResults() = runBlocking {
        val backend = FakeBackend(history = mutableListOf(successEntry("a.png", "b.png")))
        backend.downloads["a.png"] = Result.failure(IllegalStateException("broken"))
        backend.downloads["b.png"] = Result.success(byteArrayOf(9))
        val sink = FakeHistorySink()

        val final = GenerationRunner(backend, sink, pollDelayMs = 1, timeoutMs = 100).run(task()) {}

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertEquals(1, final.results.size)
        assertTrue(final.results.single().localPath.endsWith("prompt-1_1.png"))
        assertEquals(1, sink.items.single().images.size)
    }

    @Test
    fun allOutputDownloadsFailProducesDownloadFailureAndNoHistory() = runBlocking {
        val backend = FakeBackend(history = mutableListOf(successEntry("a.png")))
        backend.downloads["a.png"] = Result.failure(IllegalStateException("broken"))
        val sink = FakeHistorySink()

        val final = GenerationRunner(backend, sink, pollDelayMs = 1, timeoutMs = 100).run(task()) {}

        assertEquals(GenerationPhase.FAILED, final.phase)
        assertEquals(ErrorKind.DOWNLOAD, final.error?.kind)
        assertTrue(sink.items.isEmpty())
    }

    @Test
    fun zeroImageWorkflowCanSucceedAndPersistHistory() = runBlocking {
        val sink = FakeHistorySink()
        val final = GenerationRunner(
            FakeBackend(history = mutableListOf(successEntry())),
            sink,
            pollDelayMs = 1,
            timeoutMs = 100,
        ).run(task()) {}

        assertEquals(GenerationPhase.SUCCEEDED, final.phase)
        assertTrue(final.results.isEmpty())
        assertEquals(1, sink.items.size)
    }

    private class FakeBackend(
        private val history: MutableList<JSONObject?>,
    ) : GenerationBackend {
        var capturedPrompt: JSONObject? = null
        var events: Flow<WsEvent> = emptyFlow()
        val downloads = mutableMapOf<String, Result<ByteArray>>()

        override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String {
            capturedPrompt = JSONObject(prompt.toString())
            return "prompt-1"
        }

        override fun wsEvents(clientId: String): Flow<WsEvent> = events

        override suspend fun historyEntry(promptId: String): JSONObject? =
            if (history.isEmpty()) null else history.removeAt(0)

        override suspend fun downloadImage(image: OutputImage): ByteArray =
            downloads[image.filename]?.getOrThrow() ?: byteArrayOf(1)
    }

    private class FakeHistorySink : GenerationHistorySink {
        private val dir: File = Files.createTempDirectory("comfy-runner-test").toFile().apply { deleteOnExit() }
        val items = mutableListOf<HistoryItem>()

        override fun newImageFile(promptId: String, index: Int): File = File(dir, "${promptId}_${index}.png")
        override fun add(item: HistoryItem) { items += item }
    }

    companion object {
        private fun successEntry(vararg filenames: String): JSONObject {
            val images = JSONArray()
            filenames.forEach { images.put(JSONObject().put("filename", it).put("subfolder", "").put("type", "output")) }
            return JSONObject()
                .put("status", JSONObject().put("status_str", "success").put("messages", JSONArray()))
                .put("outputs", if (filenames.isEmpty()) JSONObject() else JSONObject().put("9", JSONObject().put("images", images)))
        }

        private fun cancelledEntry(): JSONObject = JSONObject()
            .put("status", JSONObject().put("status_str", "error").put("messages", JSONArray().put(JSONArray().put("execution_interrupted").put(JSONObject()))))
            .put("outputs", JSONObject())

        private fun errorEntry(): JSONObject = JSONObject()
            .put("status", JSONObject().put("status_str", "error").put("messages", JSONArray().put(
                JSONArray().put("execution_error").put(
                    JSONObject().put("node_id", "2").put("node_type", "KSampler").put("exception_message", "boom")
                )
            )))
            .put("outputs", JSONObject())
    }
}
