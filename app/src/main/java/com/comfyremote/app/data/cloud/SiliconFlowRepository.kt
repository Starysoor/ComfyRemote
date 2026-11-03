package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** SiliconFlow image and video models (OpenAI-compatible image API + async video API). */
class SiliconFlowRepository(
    private val settings: CloudProviderSettingsStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "SiliconFlow"
    override val settingsSummary: String = "Kolors / Qwen-Image / Wan2.2 · API key"
    override val pathPrefix: String = "siliconflow:"
    override val baseUrl: String = DEFAULT_BASE
    override val defaultModels: List<String> get() = MODELS

    private data class Pending(val model: String, val kind: String)
    private val imageResults = ConcurrentHashMap<String, CloudTaskResult>()
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun account() = settings.account(id, DEFAULT_BASE, MODELS)

    override fun configured(): Boolean = account().token.isNotBlank()

    override suspend fun discoverModels(): List<String> {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a SiliconFlow API key first")
        val root = CloudHttp.getJson("${account.baseUrl.trimEnd('/')}/models", auth(account))
        return modelIdsFromResponse(root.optJSONArray("data")).filter(::looksLikeMediaModel)
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> =
        account().models.ifEmpty { MODELS }.map { model ->
            CloudWorkflowSummary(
                id = model,
                name = model.substringAfterLast('/'),
                description = "SiliconFlow $model",
                kind = kindOf(model),
                priceType = "official_api",
            )
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
            pending[requestId] = Pending(workflowId, "video")
            requestId
        } else {
            val payload = JSONObject()
                .put("model", workflowId)
                .put("prompt", body.optString("prompt"))
            listOf("negative_prompt", "image_size", "prompt_enhancement").forEach { key ->
                body.optString(key).takeIf { it.isNotBlank() }?.let { payload.put(key, it) }
            }
            body.opt("image")?.toString()?.takeIf { it.isNotBlank() }?.let { payload.put("image", it) }
            body.optInt("batch_size", 1).takeIf { it > 0 }?.let { payload.put("batch_size", it) }
            body.optInt("num_inference_steps", 20).takeIf { it > 0 }?.let { payload.put("num_inference_steps", it) }
            body.opt("guidance_scale")?.let {
                it.toString().toDoubleOrNull()?.let { value -> payload.put("guidance_scale", value) }
            }
            body.opt("seed")?.let { payload.put("seed", it) }
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
            imageResults[taskId] = CloudTaskResult(taskId, "SUCCESS", results, "", 0)
            taskId
        }
    }

    override suspend fun result(taskId: String): CloudTaskResult {
        imageResults[taskId]?.let { return it }
        val account = account()
        val response = CloudHttp.postJson(
            "${account.baseUrl.trimEnd('/')}/video/status",
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
                CloudTaskResult(taskId, "SUCCESS", results, "", 0)
            }
            "failed", "failure", "error" -> CloudTaskResult(
                taskId, "FAILED", emptyList(),
                data.optString("message").ifBlank { "SiliconFlow video generation failed" }, 0,
            )
            else -> CloudTaskResult(taskId, "RUNNING", emptyList(), status, 0)
        }
    }

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun rulesFor(model: String): List<CloudInputRule> {
        val kind = kindOf(model)
        val rules = mutableListOf(CloudInputRule("prompt", "prompt", required = kind != "image" || !model.contains("Edit")))
        if (kind == "video") {
            rules += CloudInputRule(
                "image_size", "enum", required = false, defaultValue = "1280x720",
                options = listOf("1280x720", "720x1280", "960x960"),
            )
            if (model.contains("I2V", ignoreCase = true)) {
                rules += CloudInputRule("image", "image", required = true)
            }
            rules += CloudInputRule("negative_prompt", "string", required = false)
            rules += CloudInputRule("seed", "integer", required = false)
            return rules
        }
        if (model.contains("Edit", ignoreCase = true)) {
            rules += CloudInputRule("image", "image", required = true)
        }
        rules += CloudInputRule(
            "image_size", "enum", required = false, defaultValue = "1024x1024",
            options = listOf("1024x1024", "768x1024", "1024x768", "512x512", "1280x720", "720x1280"),
        )
        rules += CloudInputRule("negative_prompt", "string", required = false)
        rules += CloudInputRule(
            "batch_size", "integer", required = false, defaultValue = "1", min = 1.0, max = 4.0,
        )
        rules += CloudInputRule(
            "num_inference_steps", "integer", required = false, defaultValue = "20", min = 20.0, max = 50.0,
        )
        rules += CloudInputRule(
            "guidance_scale", "number", required = false, defaultValue = "7.5", min = 1.0, max = 20.0,
        )
        rules += CloudInputRule("seed", "integer", required = false)
        rules += CloudInputRule("prompt_enhancement", "boolean", required = false, defaultValue = "false")
        return rules
    }

    private fun kindOf(model: String): String {
        val lower = model.lowercase()
        return if (listOf("t2v", "i2v", "video", "wan2").any { it in lower }) "video" else "image"
    }

    private fun looksLikeMediaModel(model: String): Boolean {
        val lower = model.lowercase()
        return listOf(
            "image", "kolors", "flux", "stable", "sd3", "sdxl", "wan", "video",
            "seedance", "seedream", "kling", "hailuo", "minimax", "hunyuan",
            "cogvideo", "z-image",
        ).any { it in lower }
    }

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }.orEmpty()

    private fun auth(account: CloudProviderAccount) = mapOf("Authorization" to "Bearer ${account.token}")

    companion object {
        const val PROVIDER = "siliconflow"
        const val DEFAULT_BASE = "https://api.siliconflow.cn/v1"
        val MODELS = listOf(
            "Kwai-Kolors/Kolors",
            "Qwen/Qwen-Image",
            "Qwen/Qwen-Image-Edit-2509",
            "Tongyi-MAI/Z-Image-Turbo",
            "Wan-AI/Wan2.2-T2V-A14B",
            "Wan-AI/Wan2.2-I2V-A14B",
        )
    }
}
