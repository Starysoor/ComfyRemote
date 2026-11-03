package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** OpenAI Images API: official image models with built-in parameter catalogs. */
class OpenAiImageRepository(
    private val settings: CloudProviderSettingsStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "OpenAI"
    override val settingsSummary: String = "Official image models · API key"
    override val defaultModels: List<String> get() = MODELS
    override val pathPrefix: String = "openai:"
    override val baseUrl: String = DEFAULT_BASE

    private val results = ConcurrentHashMap<String, CloudTaskResult>()

    private fun account() = settings.account(id, DEFAULT_BASE, MODELS)

    override fun configured(): Boolean = account().token.isNotBlank()

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        val configuredModels = account().models.ifEmpty { MODELS }
        return configuredModels.map { model ->
            CloudWorkflowSummary(
                id = model,
                name = displayName(model),
                description = "OpenAI ${displayName(model)} image generation",
                kind = "image",
                priceType = "official_api",
            )
        }
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec = CloudWorkflowSpec(
        id = workflowId,
        name = displayName(workflowId),
        kind = "image",
        description = "OpenAI Images API",
        rules = rulesFor(workflowId),
    )

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure an OpenAI API key first")
        val payload = JSONObject()
            .put("model", workflowId)
            .put("prompt", body.optString("prompt"))
        body.opt("size")?.toString()?.takeIf { it.isNotBlank() }?.let { payload.put("size", it) }
        body.opt("quality")?.toString()?.takeIf { it.isNotBlank() }?.let { payload.put("quality", it) }
        body.opt("background")?.toString()?.takeIf { it.isNotBlank() }?.let { payload.put("background", it) }
        body.opt("output_format")?.toString()?.takeIf { it.isNotBlank() }?.let { payload.put("output_format", it) }
        body.optInt("n", 1).takeIf { it > 0 }?.let { payload.put("n", it) }
        val response = CloudHttp.postJson(
            url = "${account.baseUrl.trimEnd('/')}/images/generations",
            body = payload,
            headers = mapOf("Authorization" to "Bearer ${account.token}"),
        )
        val data = response.optJSONArray("data")
            ?: throw ComfyException(CloudHttp.errorMessage(response, "OpenAI returned no image data"))
        val results = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val format = body.optString("output_format").ifBlank { "png" }
            val resolvedUrl = when {
                !url.isNullOrBlank() -> url
                !b64.isNullOrBlank() -> "data:image/$format;base64,$b64"
                else -> null
            } ?: return@mapNotNull null
            CloudResult(
                url = resolvedUrl,
                type = "image",
                fileType = if (!url.isNullOrBlank()) extensionOf(url).ifBlank { format } else format,
                outputType = "output",
            )
        }
        if (results.isEmpty()) throw ComfyException("OpenAI returned no images")
        val taskId = UUID.randomUUID().toString()
        this.results[taskId] = CloudTaskResult(
            taskId = taskId,
            status = "SUCCESS",
            results = results,
            message = "",
            durationSeconds = 0,
        )
        return taskId
    }

    override suspend fun result(taskId: String): CloudTaskResult =
        results[taskId] ?: CloudTaskResult(taskId, "FAILED", emptyList(), "Image result expired", 0)

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun rulesFor(model: String): List<CloudInputRule> {
        val dallE = model.startsWith("dall-e")
        val sizes = if (dallE) {
            listOf("1024x1024", "1792x1024", "1024x1792")
        } else {
            listOf(
                "1024x1024", "1536x1024", "1024x1536",
                "2048x2048", "2048x1152", "1152x2048", "auto",
            )
        }
        val rules = mutableListOf(
            CloudInputRule("prompt", "prompt", required = true),
            CloudInputRule("size", "enum", required = false, defaultValue = sizes.first(), options = sizes),
            CloudInputRule(
                "quality",
                "enum",
                required = false,
                defaultValue = if (dallE) "standard" else "auto",
                options = if (dallE) listOf("standard", "hd")
                else listOf("auto", "low", "medium", "high", "xhigh", "max"),
            ),
            CloudInputRule(
                "n",
                "integer",
                required = false,
                defaultValue = "1",
                min = 1.0,
                max = if (dallE) 1.0 else 4.0,
            ),
        )
        if (dallE) {
            rules += CloudInputRule(
                "style", "enum", required = false, defaultValue = "vivid",
                options = listOf("vivid", "natural"),
            )
        } else {
            rules += CloudInputRule(
                "background", "enum", required = false, defaultValue = "opaque",
                options = listOf("transparent", "opaque", "auto"),
            )
            rules += CloudInputRule(
                "output_format", "enum", required = false, defaultValue = "png",
                options = listOf("png", "jpeg", "webp"),
            )
        }
        return rules
    }

    private fun displayName(model: String): String = when {
        model.startsWith("gpt-image-2.5-flare") -> "GPT Image 2.5 Flare"
        model.startsWith("gpt-image-2.5-sunburst") -> "GPT Image 2.5 Sunburst"
        model.startsWith("gpt-image-2") -> "GPT Image 2"
        model.startsWith("gpt-image-1.5") -> "GPT Image 1.5"
        model == "dall-e-3" -> "DALL·E 3"
        else -> model
    }

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }.orEmpty()

    companion object {
        const val PROVIDER = "openai"
        const val DEFAULT_BASE = "https://api.openai.com/v1"
        val MODELS = listOf(
            "gpt-image-2.5-flare",
            "gpt-image-2.5-sunburst",
            "gpt-image-2",
            "gpt-image-1.5",
            "chatgpt-image-latest",
        )
    }
}
