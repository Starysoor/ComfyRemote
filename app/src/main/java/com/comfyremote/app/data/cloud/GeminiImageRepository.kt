package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Google Gemini / Imagen official image APIs. */
class GeminiImageRepository(
    private val settings: CloudProviderSettingsStore,
    private val taskStore: CloudProviderTaskStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "Google Gemini"
    override val settingsSummary: String = "Gemini image models · API key"
    override val pathPrefix: String = "gemini:"
    override val baseUrl: String = DEFAULT_BASE
    override val supportsModelDiscovery: Boolean = true
    override val supportsCustomHeaders: Boolean = true

    private val results = ConcurrentHashMap<String, CloudTaskResult>()

    private fun account() = settings.account(id, DEFAULT_BASE)

    override fun configured(): Boolean = account().token.isNotBlank()

    override fun endpoint(): String = account().baseUrl

    override suspend fun discoverModels(): List<String> {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a Gemini API key first")
        val root = CloudHttp.getJson(
            "${account.baseUrl.trimEnd('/')}/models",
            auth(account),
        )
        // Imagen uses a different :predict contract; only list models that this
        // adapter can actually call through :generateContent.
        return modelIdsFromResponse(root.optJSONArray("models"), idKey = "name")
            .map { it.removePrefix("models/") }
            .filter { model ->
                val lower = model.lowercase()
                lower.startsWith("gemini") && "image" in lower
            }
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        val account = account()
        if (account.token.isBlank()) return emptyList()
        val models = pulledModels(settings, id, account.baseUrl, account.token) { discoverModels() }
        return settings.enabledModels(id, models).map { model ->
            CloudWorkflowSummary(
                id = model,
                name = displayName(model),
                description = "Google ${displayName(model)} image generation",
                kind = "image",
                priceType = "official_api",
            )
        }
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec = CloudWorkflowSpec(
        id = workflowId,
        name = displayName(workflowId),
        kind = "image",
        description = "Google image generation API",
        rules = rulesFor(workflowId),
    )

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a Gemini API key first")
        val base = account.baseUrl.trimEnd('/')
        val headers = auth(account)
        val prompt = body.optString("prompt").trim()
        if (prompt.isBlank()) throw ComfyException("Prompt is required")
        val aspect = body.optString("aspect_ratio").ifBlank { "1:1" }
        val items = submitGemini(base, workflowId, prompt, aspect, body, headers)
        if (items.isEmpty()) throw ComfyException("Provider returned no images")
        val taskId = UUID.randomUUID().toString()
        val result = CloudTaskResult(taskId, "SUCCESS", items, "", 0)
        results[taskId] = result
        taskStore.saveResult(id, taskId, result)
        return taskId
    }

    private fun submitGemini(
        base: String,
        model: String,
        prompt: String,
        aspect: String,
        body: JSONObject,
        headers: Map<String, String>,
    ): List<CloudResult> {
        val imageConfig = JSONObject().put("aspectRatio", aspect)
        body.optString("image_size").takeIf { it.isNotBlank() }?.let {
            imageConfig.put("imageSize", it)
        }
        val parts = buildGeminiImageParts(model, prompt, body) { value ->
            CloudHttp.imagePayload(value)
        }
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("IMAGE"))
            .put("imageConfig", imageConfig)
        body.opt("seed")?.toString()?.toLongOrNull()?.let { generationConfig.put("seed", it) }
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", parts),
                ),
            )
            .put("generationConfig", generationConfig)
        val response = CloudHttp.postJson("${base}/models/${model}:generateContent", payload, headers)
        val responseParts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Gemini returned no image parts"))
        return (0 until responseParts.length()).mapNotNull { index ->
            val part = responseParts.optJSONObject(index) ?: return@mapNotNull null
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                ?: return@mapNotNull null
            val data = inline.optString("data").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mime = inline.optString("mimeType").ifBlank { "image/png" }
            CloudResult(
                url = "data:$mime;base64,$data",
                type = "image",
                fileType = mime.substringAfter('/', "png"),
                outputType = "output",
            )
        }
    }

    override suspend fun result(taskId: String): CloudTaskResult =
        results[taskId]
            ?: taskStore.result(id, taskId)
            ?: CloudTaskResult(taskId, "FAILED", emptyList(), "Google image result expired", 0)

    override fun forgetTask(taskId: String) {
        results.remove(taskId)
        taskStore.clear(id, taskId)
    }

    private fun auth(account: CloudProviderAccount) =
        settings.mergeHeaders(id, mapOf("x-goog-api-key" to account.token))

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun rulesFor(model: String): List<CloudInputRule> =
        CloudModelParameters.geminiRules(model)

    private fun displayName(model: String): String = when {
        model == "gemini-3-pro-image" -> "Gemini 3 Pro Image (Nano Banana Pro)"
        model.contains("flash-lite-image") -> "Gemini 3.1 Flash Lite Image"
        model.contains("flash-image") -> "Gemini 3.1 Flash Image (Nano Banana 2)"
        else -> model
    }

    companion object {
        const val PROVIDER = "gemini"
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}

/** Pure part builder so multi-image Gemini requests can be regression-tested. */
internal fun buildGeminiImageParts(
    model: String,
    prompt: String,
    body: JSONObject,
    resolveImage: (String) -> Pair<String, ByteArray>?,
): JSONArray {
    val parts = JSONArray()
    if (CloudModelParameters.supportsReferenceImage(model)) {
        CloudModelParameters.referenceImageInputs(body).forEach { (_, value) ->
            val payload = resolveImage(value)
                ?: throw ComfyException("The reference image could not be read")
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", payload.first)
                        .put("data", java.util.Base64.getEncoder().encodeToString(payload.second)),
                )
            )
        }
    }
    parts.put(JSONObject().put("text", prompt))
    return parts
}
