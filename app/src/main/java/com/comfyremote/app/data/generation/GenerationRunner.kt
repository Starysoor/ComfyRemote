package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.LabeledText
import com.comfyremote.app.OutputImage
import com.comfyremote.app.WsEvent
import com.comfyremote.app.historyCancelled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Executes exactly one immutable generation request. WebSocket is best-effort
 * realtime feedback; /history remains the authoritative completion source.
 */
class GenerationRunner(
    private val backend: GenerationBackend,
    private val history: GenerationHistorySink,
    private val pollDelayMs: Long = 4_000L,
    private val timeoutMs: Long = 30 * 60_000L,
) {
    suspend fun run(
        task: GenerationTask,
        onUpdate: (GenerationTask) -> Unit,
    ): GenerationTask {
        var current = task
        fun update(next: GenerationTask) {
            if (current.isTerminal) return
            current = next
            onUpdate(next)
        }

        val request = task.request
        update(current.copy(phase = GenerationPhase.QUEUEING, startedAt = System.currentTimeMillis()))
        val (prompt, seedsUsed) = GenerationPromptBuilder.build(request)
        val clientId = task.clientId ?: error("missing clientId")
        val promptId = backend.queuePrompt(prompt, clientId)
        update(current.copy(phase = GenerationPhase.QUEUED, promptId = promptId))

        val entry: JSONObject = coroutineScope {
            val wsJob: Job = launch {
                runCatching {
                    backend.wsEvents(clientId).collect { event ->
                        when (event) {
                            is WsEvent.Preview -> update(current.copy(preview = event.bitmap))
                            is WsEvent.Json -> {
                                val data = event.obj.optJSONObject("data")
                                when (event.obj.optString("type")) {
                                    "status" -> {
                                        val remaining = data?.optJSONObject("status")?.optJSONObject("exec_info")
                                            ?.optInt("queue_remaining", -1) ?: -1
                                        if (remaining >= 0) update(current.copy(queueRemaining = remaining))
                                    }
                                    "execution_start" -> if (data?.optString("prompt_id") == promptId) {
                                        update(current.copy(phase = GenerationPhase.RUNNING, progress = 0f))
                                    }
                                    "executing" -> if (data?.optString("prompt_id") == promptId) {
                                        val node = data.optString("node")
                                        if (node.isNotEmpty() && node != "null") {
                                            update(
                                                current.copy(
                                                    nodeId = node,
                                                    nodeTitle = request.nodeTitles[node].orEmpty(),
                                                    progress = null,
                                                )
                                            )
                                        }
                                    }
                                    "progress" -> {
                                        val eventPrompt = data?.optString("prompt_id")
                                        if (data != null && data.has("value") && (eventPrompt.isNullOrEmpty() || eventPrompt == promptId)) {
                                            val value = data.optDouble("value", 0.0)
                                            val max = data.optDouble("max", 1.0).coerceAtLeast(1e-6)
                                            update(current.copy(progress = (value / max).toFloat().coerceIn(0f, 1f)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            try {
                pollHistory(promptId) {
                    if (current.phase == GenerationPhase.QUEUED) {
                        update(current.copy(phase = GenerationPhase.RUNNING, progress = 0f))
                    }
                }
            } finally {
                wsJob.cancel()
            }
        }

        if (historyCancelled(entry)) {
            update(current.copy(phase = GenerationPhase.CANCELLED, completedAt = System.currentTimeMillis(), preview = null))
            return current
        }

        extractError(entry, request.nodeTitles)?.let { error ->
            update(current.copy(phase = GenerationPhase.FAILED, error = error, completedAt = System.currentTimeMillis(), preview = null))
            return current
        }

        val outputImages = parseImages(entry)
        val results = mutableListOf<ResultImage>()
        outputImages.forEachIndexed { index, image ->
            runCatching {
                val bytes = backend.downloadImage(image)
                val file = history.newImageFile(promptId, index)
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                results += ResultImage(id = "$promptId:$index", localPath = file.absolutePath)
            }
        }
        if (outputImages.isNotEmpty() && results.isEmpty()) {
            update(
                current.copy(
                    phase = GenerationPhase.FAILED,
                    error = GenerationError(ErrorKind.DOWNLOAD, "All result image downloads failed"),
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                )
            )
            return current
        }

        val valuesUsed = request.fieldValues + seedsUsed
        val summary = request.fields.firstOrNull { it.kind == FieldKind.TEXT }
            ?.let { valuesUsed[it.key] ?: it.value }?.take(50) ?: request.workflowName
        val prompts = request.fields.filter { it.kind == FieldKind.TEXT }
            .map { LabeledText(it.label, valuesUsed[it.key] ?: it.value) }
            .filter { it.value.isNotBlank() }
        val seedInfo = seedsUsed.values.joinToString(", ")

        // Persist before publishing SUCCEEDED so History observers see the result immediately.
        history.add(
            HistoryItem(
                id = promptId,
                wfPath = request.workflowPath,
                wfName = request.workflowName,
                time = System.currentTimeMillis(),
                values = valuesUsed,
                randomSeed = request.randomSeed,
                images = results.map { it.localPath },
                summary = summary,
                prompts = prompts,
                seeds = seedInfo,
            )
        )

        update(
            current.copy(
                phase = GenerationPhase.SUCCEEDED,
                results = results,
                completedAt = System.currentTimeMillis(),
                preview = null,
                seedInfo = seedInfo,
                seedValues = seedsUsed,
            )
        )
        return current
    }

    private suspend fun pollHistory(promptId: String, onStillRunning: () -> Unit): JSONObject {
        val deadline = System.currentTimeMillis() + timeoutMs
        var first = true
        while (System.currentTimeMillis() < deadline) {
            backend.historyEntry(promptId)?.let { return it }
            if (first) {
                first = false
                onStillRunning()
            }
            delay(pollDelayMs)
        }
        throw GenerationTimeoutException("Timed out waiting for ComfyUI history")
    }

    private fun extractError(entry: JSONObject, nodeTitles: Map<String, String>): GenerationError? {
        val status = entry.optJSONObject("status") ?: return null
        if (status.optString("status_str") != "error") return null
        val messages = status.optJSONArray("messages")
        if (messages != null) for (i in 0 until messages.length()) {
            val message = messages.optJSONArray(i) ?: continue
            if (message.optString(0) == "execution_error") {
                val data = message.optJSONObject(1) ?: continue
                val nodeId = data.optString("node_id").ifEmpty { null }
                return GenerationError(
                    kind = ErrorKind.EXECUTION,
                    message = data.optString("exception_message").ifEmpty { "Execution error" },
                    nodeId = nodeId,
                    nodeType = data.optString("node_type").ifEmpty { null },
                    nodeTitle = nodeId?.let(nodeTitles::get),
                )
            }
        }
        return GenerationError(ErrorKind.EXECUTION, "Execution error")
    }

    private fun parseImages(entry: JSONObject): List<OutputImage> {
        val result = mutableListOf<OutputImage>()
        val outputs = entry.optJSONObject("outputs") ?: return result
        outputs.keys().forEach { key ->
            val images = outputs.optJSONObject(key)?.optJSONArray("images") ?: return@forEach
            for (i in 0 until images.length()) {
                val image = images.optJSONObject(i) ?: continue
                result += OutputImage(
                    image.optString("filename"),
                    image.optString("subfolder"),
                    image.optString("type", "output"),
                )
            }
        }
        return result
    }
}
