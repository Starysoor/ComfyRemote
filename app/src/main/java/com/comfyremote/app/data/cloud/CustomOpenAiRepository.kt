package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * User-configured OpenAI-compatible provider. The base URL and model list are editable;
 * image parameters are inferred from the OpenAI Images contract.
 */
class CustomOpenAiRepository(
    private val settings: CloudProviderSettingsStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "Custom OpenAI API"
    override val settingsSummary: String = "Bring your own OpenAI-compatible endpoint"
    override val pathPrefix: String = "custom:"
    override val baseUrl: String = ""

    private val results = ConcurrentHashMap<String, CloudTaskResult>()

    private fun account() = settings.account(id, "", emptyList())

    override fun configured(): Boolean = account().baseUrl.isNotBlank()

    override suspend fun discoverModels(): List<String> {
        val account = account()
        if (account.baseUrl.isBlank()) return emptyList()
        val root = CloudHttp.getJson(
            "${account.baseUrl.trimEnd('/')}/models",
            if (account.token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${account.token}"),
        )
        return modelIdsFromResponse(root.optJSONArray("data"))
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        val account = account()
        if (account.baseUrl.isBlank()) return emptyList()
        val configured = account.models
        if (configured.isNotEmpty()) return configured.map(::summary)
        return runCatching {
            val root = CloudHttp.getJson(
                "${account.baseUrl.trimEnd('/')}/models",
                if (account.token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${account.token}"),
            )
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()
            (0 until data.length()).mapNotNull { index ->
                data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::summary)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec = CloudWorkflowSpec(
        id = workflowId,
        name = workflowId,
        kind = "image",
        description = "Custom OpenAI-compatible image API",
        rules = IMAGE_RULES,
    )

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val account = account()
        if (account.baseUrl.isBlank()) throw ComfyException("Configure the custom API base URL first")
        val payload = JSONObject()
            .put("model", workflowId)
            .put("prompt", body.optString("prompt"))
        listOf("size", "quality", "style", "response_format").forEach { key ->
            body.optString(key).takeIf { it.isNotBlank() }?.let { payload.put(key, it) }
        }
        body.optInt("n", 1).takeIf { it > 0 }?.let { payload.put("n", it) }
        val headers = if (account.token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${account.token}")
        val response = CloudHttp.postJson(
            "${account.baseUrl.trimEnd('/')}/images/generations",
            payload,
            headers,
        )
        val data = response.optJSONArray("data")
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Custom API returned no image data"))
        val results = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            val url = item.optString("url").takeIf { it.isNotBlank() }
            val resolved = when {
                !url.isNullOrBlank() -> url
                !b64.isNullOrBlank() -> "data:image/png;base64,$b64"
                else -> null
            } ?: return@mapNotNull null
            CloudResult(
                url = resolved,
                type = "image",
                fileType = if (!url.isNullOrBlank()) extensionOf(url).ifBlank { "png" } else "png",
                outputType = "output",
            )
        }
        if (results.isEmpty()) throw ComfyException("Custom API returned no images")
        val taskId = UUID.randomUUID().toString()
        this.results[taskId] = CloudTaskResult(taskId, "SUCCESS", results, "", 0)
        return taskId
    }

    override suspend fun result(taskId: String): CloudTaskResult =
        results[taskId] ?: CloudTaskResult(taskId, "FAILED", emptyList(), "Image result expired", 0)

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun summary(model: String) = CloudWorkflowSummary(
        id = model,
        name = model,
        description = "Custom vision model",
        kind = "image",
        priceType = "custom_api",
    )

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }.orEmpty()

    companion object {
        const val PROVIDER = "custom"
        private val IMAGE_RULES = listOf(
            CloudInputRule("prompt", "prompt", required = true),
            CloudInputRule(
                "size", "enum", required = false, defaultValue = "1024x1024",
                options = listOf("1024x1024", "512x512", "768x768", "1792x1024", "1024x1792", "auto"),
            ),
            CloudInputRule(
                "quality", "enum", required = false, defaultValue = "standard",
                options = listOf("standard", "hd", "auto"),
            ),
            CloudInputRule(
                "style", "enum", required = false, defaultValue = "vivid",
                options = listOf("vivid", "natural"),
            ),
            CloudInputRule(
                "response_format", "enum", required = false, defaultValue = "url",
                options = listOf("url", "b64_json"),
            ),
            CloudInputRule("n", "integer", required = false, defaultValue = "1", min = 1.0, max = 4.0),
        )
    }
}
