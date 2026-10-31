package com.comfyremote.app.data.generation

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.ComfyException
import com.comfyremote.app.FieldKind
import com.comfyremote.app.LabeledText
import com.comfyremote.app.historyCancelled
import com.comfyremote.app.WorkflowConverter
import com.comfyremote.app.data.repository.HistoryRepository
import com.comfyremote.app.data.repository.ServerSessionRepository
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Executes one [GenerationRequest] end to end and reports every state change
 * through [onUpdate]. Policy unchanged: WebSocket = realtime feedback,
 * /history = authoritative completion.
 */
class GenerationRunner(
    private val session: ServerSessionRepository,
    private val historyRepository: HistoryRepository,
    private val onInterruptRequested: suspend () -> Unit,
) {

    suspend fun run(
        task: GenerationTask,
        onUpdate: (GenerationTask) -> Unit,
    ): GenerationTask {
        var current = task
        fun update(t: GenerationTask) {
            if (current.isTerminal && !t.isTerminal) return  // late WS event after finish
            current = t
            onUpdate(t)
        }

        val a: ComfyApi = session.require()
        val request = task.request

        // ---- queue ----
        update(current.copy(phase = GenerationPhase.QUEUEING, startedAt = System.currentTimeMillis()))
        val (prompt, seedsUsed) = WorkflowConverter.buildPrompt(
            com.comfyremote.app.ConvertedWorkflow(
                request.convertedPrompt, request.fields, emptyList()
            ),
            request.fieldValues,
            request.randomSeed,
        )
        val clientId = task.clientId ?: error("missing clientId")
        val promptId = a.queuePrompt(prompt, clientId)
        update(
            current.copy(
                phase = GenerationPhase.QUEUED,
                promptId = promptId,
            )
        )

        // ---- live WS feedback (best effort) ----
        val entry: JSONObject = coroutineScope {
            val wsJob: Job = launch {
            runCatching {
                a.wsEvents(clientId).collect { ev ->
                    when (ev) {
                        is com.comfyremote.app.WsEvent.Preview ->
                            update(current.copy(preview = ev.bitmap))
                        is com.comfyremote.app.WsEvent.Json -> {
                            val data = ev.obj.optJSONObject("data")
                            when (ev.obj.optString("type")) {
                                "status" -> {
                                    val q = data?.optJSONObject("status")?.optJSONObject("exec_info")
                                        ?.optInt("queue_remaining", -1) ?: -1
                                    if (q >= 0) update(current.copy(queueRemaining = q))
                                }
                                "execution_start" -> if (data?.optString("prompt_id") == promptId)
                                    update(current.copy(phase = GenerationPhase.RUNNING, progress = 0f))
                                "executing" -> if (data?.optString("prompt_id") == promptId) {
                                    val node = data.optString("node")
                                    if (node.isNotEmpty() && node != "null") update(
                                        current.copy(
                                            nodeId = node,
                                            nodeTitle = request.nodeTitles[node] ?: "",
                                            progress = null,
                                        )
                                    )
                                }
                                "progress" -> {
                                    val p = data?.optString("prompt_id")
                                    if (data != null && data.has("value") && (p.isNullOrEmpty() || p == promptId)) {
                                        val v = data.optDouble("value", 0.0)
                                        val m = data.optDouble("max", 1.0).coerceAtLeast(1e-6)
                                        update(current.copy(progress = (v / m).toFloat().coerceIn(0f, 1f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            // ---- authoritative completion via /history ----
            try {
                pollHistory(a, promptId) {
                    if (current.phase == GenerationPhase.QUEUED) {
                        update(current.copy(phase = GenerationPhase.RUNNING, progress = 0f))
                    }
                }
            } finally {
                wsJob.cancel()
            }
        }

        if (historyCancelled(entry)) {
            update(
                current.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                )
            )
            return current
        }

        extractError(entry)?.let { err ->
            update(
                current.copy(
                    phase = GenerationPhase.FAILED,
                    error = err,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                )
            )
            return current
        }

        // ---- download outputs ----
        val results = mutableListOf<ResultImage>()
        parseImages(entry).forEachIndexed { idx, im ->
            try {
                val bytes = a.downloadImage(im)
                val f = historyRepository.newImageFile(promptId, idx)
                withContext(Dispatchers.IO) { f.writeBytes(bytes) }
                results.add(ResultImage(id = "$promptId:$idx", localPath = f.absolutePath))
            } catch (_: Exception) {
                // partial download failure: keep the rest
            }
        }
        // Persist BEFORE marking SUCCEEDED so UI collectors that reload history
        // on the SUCCEEDED transition already see the new entry.
        val summary = request.fields.firstOrNull { it.kind == FieldKind.TEXT }
            ?.let { request.fieldValues[it.key] ?: it.value }?.take(50) ?: request.workflowName
        val prompts = request.fields.filter { it.kind == FieldKind.TEXT }
            .map { LabeledText(it.label, request.fieldValues[it.key] ?: it.value) }
            .filter { it.value.isNotBlank() }
        val seedFields = request.fields.filter { it.kind == FieldKind.SEED }
            .mapNotNull { request.fieldValues[it.key] }.joinToString(", ")
        historyRepository.add(
            com.comfyremote.app.HistoryItem(
                id = task.promptId ?: task.taskId,
                wfPath = request.workflowPath,
                wfName = request.workflowName,
                time = System.currentTimeMillis(),
                values = request.fieldValues,
                randomSeed = request.randomSeed,
                images = results.map { it.localPath },
                summary = summary,
                prompts = prompts,
                seeds = seedFields.ifEmpty { seedsUsed.values.joinToString(", ") },
            )
        )

        update(
            current.copy(
                phase = GenerationPhase.SUCCEEDED,
                results = results,
                completedAt = System.currentTimeMillis(),
                preview = null,
                seedInfo = seedsUsed.values.joinToString(", "),
            )
        )
        return current
    }

    private suspend fun pollHistory(
        a: ComfyApi,
        promptId: String,
        onStillRunning: () -> Unit,
    ): JSONObject {
        val deadline = System.currentTimeMillis() + 30 * 60_000L
        var first = true
        while (System.currentTimeMillis() < deadline) {
            a.historyEntry(promptId)?.let { return it }
            if (first) {
                first = false
                // WS may have missed execution_start; no history after 4s = executing
                onStillRunning()
            }
            delay(4000)
        }
        throw ComfyException(
            L10n.t(
                "等待结果超时（30 分钟），结果可在服务器 output 目录查看",
                "Timed out after 30 minutes; check the server output folder"
            )
        )
    }

    private fun extractError(entry: JSONObject): GenerationError? {
        val status = entry.optJSONObject("status") ?: return null
        if (status.optString("status_str") != "error") return null
        val msgs = status.optJSONArray("messages")
        if (msgs != null) for (i in 0 until msgs.length()) {
            val m = msgs.optJSONArray(i) ?: continue
            if (m.optString(0) == "execution_error") {
                val d = m.optJSONObject(1)
                if (d != null) return GenerationError(
                    kind = com.comfyremote.app.data.generation.ErrorKind.EXECUTION,
                    message = L10n.t("执行出错", "Execution error") + "：${d.optString("exception_message")}",
                    nodeId = d.optString("node_id").ifEmpty { null },
                    nodeType = d.optString("node_type").ifEmpty { null },
                )
            }
        }
        return GenerationError(
            kind = com.comfyremote.app.data.generation.ErrorKind.EXECUTION,
            message = L10n.t("执行出错", "Execution error"),
        )
    }

    private fun parseImages(entry: JSONObject): List<com.comfyremote.app.OutputImage> {
        val out = mutableListOf<com.comfyremote.app.OutputImage>()
        val outputs = entry.optJSONObject("outputs") ?: return out
        outputs.keys().forEach { k ->
            val o = outputs.optJSONObject(k) ?: return@forEach
            val imgs = o.optJSONArray("images") ?: return@forEach
            for (i in 0 until imgs.length()) {
                val im = imgs.optJSONObject(i) ?: continue
                out.add(com.comfyremote.app.OutputImage(im.optString("filename"), im.optString("subfolder"), im.optString("type", "output")))
            }
        }
        return out
    }
}
