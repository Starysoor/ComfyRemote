package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** SiliconFlow image and video models (OpenAI-compatible image API + async video API). */
class SiliconFlowRepository(
    private val settings: CloudProviderSettingsStore,
    private val taskStore: CloudProviderTaskStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "SiliconFlow"
    override val settingsSummary: String = "Media models · API key"
    override val pathPrefix: String = "siliconflow:"
    override val baseUrl: String = DEFAULT_BASE
    override val supportsModelDiscovery: Boolean = true
    override val supportsCustomHeaders: Boolean = true

    private data class Pending(val model: String, val kind: String, val base: String)
    private val imageResults = ConcurrentHashMap<String, CloudTaskResult>()
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun account() = settings.account(id, DEFAULT_BASE)

    override fun configured(): Boolean = account().token.isNotBlank()

    override fun endpoint(): String = account().baseUrl

    override suspend fun discoverModels(): List<String> {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a SiliconFlow API key first")
        val root = CloudHttp.getJson("${account.baseUrl.trimEnd('/')}/models", auth(account))
        return modelIdsFromResponse(root.optJSONArray("data")).filter(::looksLikeMediaModel)
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        val account = account()
        if (account.token.isBlank()) return emptyList()
        val models = pulledModels(settings, id, account.baseUrl, account.token) { discoverModels() }
            .filter(CloudModelParameters::looksLikeMediaModel)
        return settings.enabledModels(id, models).map { model ->
            CloudWorkflowSummary(
                id = model,
                name = model.substringAfterLast('/'),
                description = "SiliconFlow $model",
                kind = kindOf(model),
                priceType = "official_api",
            )
        }
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec = CloudWorkflowSpec(
        id = workflowId,
        name = workflowId.substringAfterLast('/'),
        kind = kindOf(workflowId),
        description = "SiliconFlow hosted model",
        rules = rulesFor(workflowId),
    )

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a SiliconFlow API key first")
        val headers = auth(account)
        val base = account.baseUrl.trimEnd('/')
        return if (kindOf(workflowId) == "video") {
            val payload = JSONObject()
                .put("model", workflowId)
                .put("prompt", body.optString("prompt"))
            body.optString("image_size").takeIf { it.isNotBlank() }?.let { payload.put("image_size", it) }
            body.optString("negative_prompt").takeIf { it.isNotBlank() }?.let { payload.put("negative_prompt", it) }
            body.opt("seed")?.let { payload.put("seed", it) }
            if (workflowId.contains("I2V", ignoreCase = true)) {
                val image = body.optString("image").takeIf { it.isNotBlank() }
                    ?: throw ComfyException("This video model requires a first-frame image")
                payload.put("image", image)
            }
            val response = CloudHttp.postJson("$base/video/submit", payload, headers)
            val requestId = response.optString("requestId")
                .takeIf { it.isNotBlank() }
                ?: response.optJSONObject("data")?.optString("requestId")?.takeIf { it.isNotBlank() }
                ?: throw ComfyException(CloudHttp.errorMessage(response, "SiliconFlow returned no requestId"))
            pending[requestId] = Pending(workflowId, "video", base)
            taskStore.savePending(id, requestId, JSONObject().put("base", base))
            requestId
        } else {
            val payload = buildSiliconFlowImagePayload(workflowId, body)
            val response = CloudHttp.postJson("$base/images/generations", payload, headers)
            val items = response.optJSONArray("images") ?: response.optJSONArray("data")
                ?: throw ComfyException(CloudHttp.errorMessage(response, "SiliconFlow returned no images"))
            val results = (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CloudResult(
                    url = url,
                    type = "image",
                    fileType = extensionOf(url).ifBlank { "png" },
                    outputType = "output",
                )
            }
            if (results.isEmpty()) throw ComfyException("SiliconFlow returned no images")
            val taskId = UUID.randomUUID().toString()
            val result = CloudTaskResult(taskId, "SUCCESS", results, "", 0)
            imageResults[taskId] = result
            taskStore.saveResult(id, taskId, result)
            taskId
        }
    }

    override suspend fun result(taskId: String): CloudTaskResult {
        imageResults[taskId]?.let { return it }
        taskStore.result(id, taskId)?.let { return it }
        val account = account()
        val base = pending[taskId]?.base
            ?: taskStore.pending(id, taskId)?.optString("base")?.takeIf { it.isNotBlank() }
            ?: account.baseUrl.trimEnd('/')
        val response = CloudHttp.postJson(
            "${base.trimEnd('/')}/video/status",
            JSONObject().put("requestId", taskId),
            auth(account),
        )
        val data = response.optJSONObject("data") ?: response
        val status = data.optString("status", response.optString("status"))
        return when (status.lowercase()) {
            "succeed", "success", "succeeded" -> {
                val videos = data.optJSONObject("results")?.optJSONArray("videos")
                    ?: data.optJSONArray("videos")
                    ?: JSONArray()
                val results = (0 until videos.length()).mapNotNull { index ->
                    val item = videos.optJSONObject(index) ?: return@mapNotNull null
                    val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    CloudResult(
                        url = url,
                        type = "video",
                        fileType = extensionOf(url).ifBlank { "mp4" },
                        outputType = "output",
                    )
                }
                CloudTaskResult(taskId, "SUCCESS", results, "", 0).also {
                    taskStore.saveResult(id, taskId, it)
                }
            }
            "cancelled", "canceled" -> CloudTaskResult(
                taskId, "CANCELED", emptyList(),
                data.optString("message").ifBlank { "SiliconFlow video generation cancelled" }, 0,
            )
            "failed", "failure", "error" -> CloudTaskResult(
                taskId, "FAILED", emptyList(),
                data.optString("message").ifBlank { "SiliconFlow video generation failed" }, 0,
            )
            else -> CloudTaskResult(taskId, "RUNNING", emptyList(), status, 0)
        }
    }

    override fun forgetTask(taskId: String) {
        imageResults.remove(taskId)
        taskStore.clear(id, taskId)
    }

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun rulesFor(model: String): List<CloudInputRule> =
        CloudModelParameters.siliconFlowRules(model)

    private fun kindOf(model: String): String = CloudModelParameters.kindOf(model)

    private fun looksLikeMediaModel(model: String): Boolean =
        CloudModelParameters.looksLikeMediaModel(model)

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }.orEmpty()

    private fun auth(account: CloudProviderAccount) =
        settings.mergeHeaders(id, mapOf("Authorization" to "Bearer ${account.token}"))

    companion object {
        const val PROVIDER = "siliconflow"
        const val DEFAULT_BASE = "https://api.siliconflow.cn/v1"
    }
}

/** Strict JSON types matter: SiliconFlow rejects stringified booleans and oversized seeds. */
internal fun buildSiliconFlowImagePayload(workflowId: String, body: JSONObject): JSONObject {
    val payload = JSONObject()
        .put("model", workflowId)
        .put("prompt", body.optString("prompt"))
    listOf("negative_prompt", "image_size").forEach { key ->
        body.optString(key).takeIf { it.isNotBlank() }?.let { payload.put(key, it) }
    }
    if (body.has("prompt_enhancement")) {
        payload.put("prompt_enhancement", body.optBoolean("prompt_enhancement", false))
    }
    // Re-number non-blank slots so image, image3 still becomes image, image2.
    CloudModelParameters.referenceImageInputs(body).forEachIndexed { index, (_, value) ->
        payload.put(if (index == 0) "image" else "image${index + 1}", value)
    }
    body.optInt("batch_size", 1).takeIf { it > 0 }?.let { payload.put("batch_size", it) }
    body.optInt("num_inference_steps", 20).takeIf { it > 0 }?.let { payload.put("num_inference_steps", it) }
    body.opt("guidance_scale")?.let {
        it.toString().toDoubleOrNull()?.let { value -> payload.put("guidance_scale", value) }
    }
    body.opt("seed")?.let { payload.put("seed", it) }
    return payload
}
