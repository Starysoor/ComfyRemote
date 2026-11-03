package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Google Gemini / Imagen official image APIs. */
class GeminiImageRepository(
    private val settings: CloudProviderSettingsStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "Google Gemini"
    override val settingsSummary: String = "Imagen & Gemini image models · API key"
    override val defaultModels: List<String> get() = MODELS
    override val pathPrefix: String = "gemini:"
    override val baseUrl: String = DEFAULT_BASE

    private val results = ConcurrentHashMap<String, CloudTaskResult>()

    private fun account() = settings.account(id, DEFAULT_BASE, MODELS)

    override fun configured(): Boolean = account().token.isNotBlank()

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> =
        account().models.ifEmpty { MODELS }.map { model ->
            CloudWorkflowSummary(
                id = model,
                name = displayName(model),
                description = "Google ${displayName(model)} image generation",
                kind = "image",
                priceType = "official_api",
            )
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
        val headers = mapOf("x-goog-api-key" to account.token)
        val prompt = body.optString("prompt").trim()
        if (prompt.isBlank()) throw ComfyException("Prompt is required")
        val aspect = body.optString("aspect_ratio").ifBlank { "1:1" }
        val items = if (workflowId.startsWith("imagen")) {
            submitImagen(base, workflowId, prompt, aspect, body, headers)
        } else {
            submitGemini(base, workflowId, prompt, aspect, headers)
        }
        if (items.isEmpty()) throw ComfyException("Provider returned no images")
        val taskId = UUID.randomUUID().toString()
        results[taskId] = CloudTaskResult(taskId, "SUCCESS", items, "", 0)
        return taskId
    }

    private fun submitImagen(
        base: String,
        model: String,
        prompt: String,
        aspect: String,
        body: JSONObject,
        headers: Map<String, String>,
    ): List<CloudResult> {
        val parameters = JSONObject()
            .put("sampleCount", body.optInt("sample_count", 1).coerceIn(1, 4))
            .put("aspectRatio", aspect)
        body.optString("person_generation").takeIf { it.isNotBlank() }?.let {
            parameters.put("personGeneration", it)
        }
        val payload = JSONObject()
            .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
            .put("parameters", parameters)
        val response = CloudHttp.postJson(
            "${base}/models/${model}:predict",
            payload,
            headers,
        )
        val predictions = response.optJSONArray("predictions")
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Imagen returned no predictions"))
        return (0 until predictions.length()).mapNotNull { index ->
            val item = predictions.optJSONObject(index) ?: return@mapNotNull null
            val data = item.optString("bytesBase64Encoded").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val mime = item.optString("mimeType").ifBlank { "image/png" }
            CloudResult(
                url = "data:$mime;base64,$data",
                type = "image",
                fileType = mime.substringAfter('/', "png"),
                outputType = "output",
            )
        }
    }

    private fun submitGemini(
        base: String,
        model: String,
        prompt: String,
        aspect: String,
        headers: Map<String, String>,
    ): List<CloudResult> {
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    )
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                    .put("imageConfig", JSONObject().put("aspectRatio", aspect)),
            )
        val response = CloudHttp.postJson("${base}/models/${model}:generateContent", payload, headers)
        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Gemini returned no image parts"))
        return (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index) ?: return@mapNotNull null
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
        results[taskId] ?: CloudTaskResult(taskId, "FAILED", emptyList(), "Image result expired", 0)

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun rulesFor(model: String): List<CloudInputRule> {
        val imagen = model.startsWith("imagen")
        val rules = mutableListOf(
            CloudInputRule("prompt", "prompt", required = true),
            CloudInputRule(
                "aspect_ratio", "enum", required = false, defaultValue = "1:1",
                options = listOf("1:1", "3:4", "4:3", "9:16", "16:9"),
            ),
        )
        if (imagen) {
            rules += CloudInputRule(
                "sample_count", "integer", required = false, defaultValue = "1",
                min = 1.0, max = 4.0,
            )
            rules += CloudInputRule(
                "person_generation", "enum", required = false, defaultValue = "allow_adult",
                options = listOf("dont_allow", "allow_adult"),
            )
        }
        return rules
    }

    private fun displayName(model: String): String = when {
        model.startsWith("imagen") -> "Imagen 4"
        model.contains("flash-image") -> "Gemini Flash Image"
        else -> model
    }

    companion object {
        const val PROVIDER = "gemini"
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
        val MODELS = listOf("imagen-4.0-generate-001", "gemini-2.5-flash-image")
    }
}
