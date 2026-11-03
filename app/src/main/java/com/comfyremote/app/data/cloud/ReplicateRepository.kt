package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Replicate models. Inputs are discovered from the model's OpenAPI input schema. */
class ReplicateRepository(
    private val settings: CloudProviderSettingsStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "Replicate"
    override val settingsSummary: String = "Flux / video models · API token"
    override val defaultModels: List<String> get() = MODELS
    override val pathPrefix: String = "replicate:"
    override val baseUrl: String = DEFAULT_BASE
    override val supportsCustomHeaders: Boolean = true

    private data class Pending(val model: String, val kind: String)
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun account() = settings.account(id, DEFAULT_BASE, MODELS)

    override fun configured(): Boolean = account().token.isNotBlank()

    override fun endpoint(): String = account().baseUrl

    /** Replicate has no user model catalogue; validate the token against the account API. */
    override suspend fun check(): CloudHealth {
        if (!configured()) {
            return CloudHealth(CloudHealthStatus.NOT_CONFIGURED, checkedAt = System.currentTimeMillis())
        }
        return try {
            val account = account()
            CloudHttp.getJson("${account.baseUrl.trimEnd('/')}/account", auth(account))
            CloudHealth(
                status = CloudHealthStatus.READY,
                modelCount = account.models.ifEmpty { MODELS }.size,
                checkedAt = System.currentTimeMillis(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            classifyCloudFailure(error).copy(checkedAt = System.currentTimeMillis())
        }
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> =
        settings.enabledModels(id, account().models.ifEmpty { MODELS }).map { model ->
            CloudWorkflowSummary(
                id = model,
                name = model.substringAfterLast('/'),
                description = "Replicate $model",
                kind = kindOf(model),
                priceType = "official_api",
            )
        }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec {
        val account = account()
        val fallback = CloudWorkflowSpec(
            id = workflowId,
            name = workflowId.substringAfterLast('/'),
            kind = kindOf(workflowId),
            description = "Replicate model",
            rules = fallbackRules(),
        )
        if (account.token.isBlank()) return fallback
        return runCatching {
            val model = CloudHttp.getJson(
                "${account.baseUrl.trimEnd('/')}/models/$workflowId",
                auth(account),
            )
            val inputSchema = model.optJSONObject("latest_version")
                ?.optJSONObject("openapi_schema")
                ?.optJSONObject("components")
                ?.optJSONObject("schemas")
                ?.optJSONObject("Input")
                ?: return@runCatching fallback
            val rules = ReplicateSchema.rules(inputSchema)
            if (rules.isEmpty()) fallback else fallback.copy(rules = rules)
        }.getOrDefault(fallback)
    }

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val account = account()
        if (account.token.isBlank()) throw ComfyException("Configure a Replicate token first")
        val payload = JSONObject().put("input", body)
        val response = CloudHttp.postJson(
            "${account.baseUrl.trimEnd('/')}/models/$workflowId/predictions",
            payload,
            auth(account),
        )
        val taskId = response.optString("id").takeIf { it.isNotBlank() }
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Replicate returned no prediction id"))
        pending[taskId] = Pending(workflowId, kindOf(workflowId))
        return taskId
    }

    override suspend fun result(taskId: String): CloudTaskResult {
        val account = account()
        val response = CloudHttp.getJson(
            "${account.baseUrl.trimEnd('/')}/predictions/$taskId",
            auth(account),
        )
        val status = response.optString("status")
        return when (status) {
            "succeeded" -> {
                val kind = pending[taskId]?.kind ?: kindOf(response.optString("model"))
                val results = outputs(response.opt("output"), kind)
                CloudTaskResult(taskId, "SUCCESS", results, "", 0)
            }
            "canceled", "cancelled" -> CloudTaskResult(
                taskId,
                "CANCELED",
                emptyList(),
                response.optString("error").ifBlank { "Replicate prediction $status" },
                0,
            )
            "failed" -> CloudTaskResult(
                taskId,
                "FAILED",
                emptyList(),
                response.optString("error").ifBlank { "Replicate prediction $status" },
                0,
            )
            else -> CloudTaskResult(taskId, "RUNNING", emptyList(), status, 0)
        }
    }

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    private fun outputs(output: Any?, kind: String): List<CloudResult> {
        val urls = when (output) {
            is String -> listOf(output)
            is JSONArray -> (0 until output.length()).mapNotNull { output.optString(it).takeIf(String::isNotBlank) }
            is JSONObject -> listOfNotNull(
                output.optString("url").takeIf(String::isNotBlank),
                output.optString("video").takeIf(String::isNotBlank),
                output.optString("image").takeIf(String::isNotBlank),
            )
            else -> emptyList()
        }
        return urls.map { url ->
            CloudResult(
                url = url,
                type = kind,
                fileType = extensionOf(url).ifBlank { if (kind == "video") "mp4" else "png" },
                outputType = "output",
            )
        }
    }

    private fun fallbackRules(): List<CloudInputRule> = listOf(
        CloudInputRule("prompt", "prompt", required = true),
        CloudInputRule("aspect_ratio", "enum", required = false, defaultValue = "1:1", options = listOf("1:1", "16:9", "9:16", "4:3", "3:4")),
        CloudInputRule("num_outputs", "integer", required = false, defaultValue = "1", min = 1.0, max = 4.0),
        CloudInputRule("seed", "integer", required = false),
    )

    private fun auth(account: CloudProviderAccount): Map<String, String> =
        settings.mergeHeaders(id, mapOf("Authorization" to "Bearer ${account.token}"))

    private fun kindOf(model: String): String {
        val lower = model.lowercase()
        return if (listOf("video", "hunyuan", "wan-", "kling", "veo", "minimax/video").any { it in lower }) {
            "video"
        } else {
            "image"
        }
    }

    private fun extensionOf(url: String): String =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }.orEmpty()

    companion object {
        const val PROVIDER = "replicate"
        const val DEFAULT_BASE = "https://api.replicate.com/v1"
        val MODELS = listOf(
            "black-forest-labs/flux-schnell",
            "black-forest-labs/flux-dev",
            "minimax/video-01",
            "tencent/hunyuan-video",
        )
    }
}
