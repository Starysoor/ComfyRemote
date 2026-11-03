package com.comfyremote.app.data.generation

import com.comfyremote.app.ComfyException
import com.comfyremote.app.OutputImage
import com.comfyremote.app.QueueSnapshot
import com.comfyremote.app.WsEvent
import android.util.Log
import com.comfyremote.app.BuildConfig
import com.comfyremote.app.data.cloud.CloudProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adapts a hosted workflow provider to the generation runner's ComfyUI port.
 * Hosted providers have no WebSocket, so result polling is the only progress
 * source; the runner already treats /history as authoritative.
 */
class CloudWorkflowBackend(
    private val repository: CloudProvider,
    private val workflowId: String,
) : GenerationBackend {

    private var taskId: String? = null
    private var lastStatus: String = ""

    override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String {
        if (BuildConfig.DEBUG) {
            // Log only media metadata, never prompts or base64 contents. This makes
            // "was my reference included?" reproducible from logcat.
            val media = buildList {
                prompt.keys().forEach { key ->
                    val value = prompt.optString(key)
                    if (value.startsWith("data:")) {
                        add("$key(${value.substringAfter("data:").substringBefore(";")}, ${value.length}B)")
                    }
                }
            }
            Log.d("IrisCloud", "submit workflow=$workflowId keys=${prompt.keys().asSequence().toList()} media=$media")
        }
        val id = repository.submit(workflowId, prompt)
        taskId = id
        lastStatus = "QUEUED"
        return id
    }

    override fun wsEvents(clientId: String): Flow<WsEvent> = emptyFlow()

    override suspend fun historyEntry(promptId: String): JSONObject? {
        val result = repository.result(promptId)
        taskId = promptId
        lastStatus = result.status
        return when (result.status) {
            "SUCCESS", "COMPLETED" -> successEntry(result)
            "FAILED", "FAILURE", "ERROR", "CANCELLED", "CANCELED" -> errorEntry(
                result.message.ifBlank { "AutoDL 任务失败（${result.status}）" }
            )
            else -> null
        }
    }

    override suspend fun queueSnapshot(): QueueSnapshot? {
        val id = taskId ?: return null
        return when (lastStatus) {
            "QUEUED", "PENDING" -> QueueSnapshot(emptyList(), listOf(id))
            "RUNNING", "PROCESSING", "IN_PROGRESS" -> QueueSnapshot(listOf(id), emptyList())
            else -> null
        }
    }

    override suspend fun downloadImage(image: OutputImage): ByteArray {
        val url = image.url ?: throw ComfyException("云端结果缺少下载地址")
        return repository.download(url)
    }

    override suspend fun downloadTo(image: OutputImage, target: java.io.File) {
        val url = image.url ?: throw ComfyException("云端结果缺少下载地址")
        repository.downloadTo(url, target)
    }

    private fun successEntry(result: com.comfyremote.app.data.cloud.CloudTaskResult): JSONObject {
        if (result.results.isEmpty()) {
            return errorEntry("AutoDL 任务完成但没有返回结果")
        }
        val images = JSONArray()
        val videos = JSONArray()
        result.results.forEach { item ->
            val entry = JSONObject()
                .put("filename", item.url)
                .put("subfolder", "")
                .put("type", item.outputType.ifBlank { "output" })
                .put("url", item.url)
                .put("file_type", item.fileType)
            if (item.type.equals("video", true)) {
                entry.put("media_kind", "video")
                videos.put(entry)
            } else {
                entry.put("media_kind", item.type.ifBlank { "image" })
                images.put(entry)
            }
        }
        val output = JSONObject()
        if (images.length() > 0) output.put("images", images)
        if (videos.length() > 0) output.put("videos", videos)
        return JSONObject()
            .put("status", JSONObject().put("status_str", "success").put("messages", JSONArray()))
            .put("outputs", JSONObject().put("cloud", output))
    }

    private fun errorEntry(message: String): JSONObject = JSONObject()
        .put(
            "status",
            JSONObject()
                .put("status_str", "error")
                .put(
                    "messages",
                    JSONArray().put(
                        JSONArray().put("execution_error").put(
                            JSONObject()
                                .put("node_id", "cloud")
                                .put("node_type", "AutoDL")
                                .put("exception_message", message)
                        )
                    )
                ),
        )
        .put("outputs", JSONObject())
}
