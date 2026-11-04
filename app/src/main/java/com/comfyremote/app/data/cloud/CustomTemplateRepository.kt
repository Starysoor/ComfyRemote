package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced custom provider: submits a user-defined JSON template and polls a
 * user-defined status URL. Placeholders use `{{field}}` / `{{token}}` syntax.
 */
class CustomTemplateRepository(
    private val settings: CloudProviderSettingsStore,
    private val taskStore: CloudProviderTaskStore,
) : CloudProvider {
    override val id: String = PROVIDER
    override val label: String = "Custom API Template"
    override val settingsSummary: String = "Your own JSON request/response contract"
    override val pathPrefix: String = "template:"
    override val baseUrl: String = ""

    private data class Pending(val config: TemplateConfig, val taskId: String)
    private val cached = ConcurrentHashMap<String, CloudTaskResult>()
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun account() = settings.account(id, "")

    private fun config(): TemplateConfig =
        TemplateConfig.parse(settings.extra(id), account().token).getOrElse {
            TemplateConfig.empty()
        }

    override fun configured(): Boolean = config().submitUrl.isNotBlank()

    override fun endpoint(): String = config().submitUrl

    /** A template can only prove health when it defines a side-effect-free `healthUrl`. */
    override suspend fun check(): CloudHealth {
        val config = config()
        templatePreflightHealth(configured(), config.healthUrl)?.let {
            return it.copy(checkedAt = System.currentTimeMillis())
        }
        val values = emptyMap<String, String>()
        val headers = config.headers.mapValues { (_, value) ->
            TemplateValues.renderString(value, values, account().token)
        }
        return try {
            CloudHttp.probe(
                url = TemplateValues.renderString(config.healthUrl, values, account().token),
                headers = headers,
            )
            CloudHealth(
                CloudHealthStatus.READY,
                modelCount = 1,
                checkedAt = System.currentTimeMillis(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            classifyCloudFailure(error).copy(checkedAt = System.currentTimeMillis())
        }
    }

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        val config = config()
        if (config.submitUrl.isBlank()) return emptyList()
        if (settings.enabledModels(id, listOf("template")).isEmpty()) return emptyList()
        return listOf(
            CloudWorkflowSummary(
                id = "template",
                name = "Custom template",
                description = config.description.ifBlank { "User-defined JSON API" },
                kind = config.resultType,
                priceType = "custom_api",
            )
        )
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec {
        val config = config()
        return CloudWorkflowSpec(
            id = "template",
            name = "Custom template",
            kind = config.resultType,
            description = config.description,
            rules = config.fields.ifEmpty {
                listOf(CloudInputRule("prompt", "prompt", required = true))
            },
        )
    }

    override suspend fun submit(workflowId: String, body: JSONObject): String {
        val config = config()
        if (config.submitUrl.isBlank()) throw ComfyException("Configure the custom template first")
        val values = linkedMapOf<String, String>().apply {
            body.keys().forEach { key -> put(key, body.optString(key)) }
        }
        val payload = TemplateValues.render(
            config.bodyTemplate,
            values,
            account().token,
            typedValues = body,
        )
        val headers = config.headers.mapValues { (_, value) ->
            TemplateValues.renderString(value, values, account().token)
        }
        val response = CloudHttp.requestJson(
            method = config.method,
            url = TemplateValues.renderString(config.submitUrl, values, account().token),
            body = payload,
            headers = headers,
        )
        if (config.statusUrlTemplate.isBlank()) {
            val taskId = UUID.randomUUID().toString()
            val results = TemplateValues.resultUrls(response, config.resultUrlPath, config.resultType)
            if (results.isEmpty()) {
                throw ComfyException(CloudHttp.errorMessage(response, "Template response has no result URL"))
            }
            val result = CloudTaskResult(taskId, "SUCCESS", results, "", 0)
            cached[taskId] = result
            taskStore.saveResult(id, taskId, result)
            return taskId
        }
        val taskId = TemplateValues.pathValue(response, config.taskIdPath)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: throw ComfyException(CloudHttp.errorMessage(response, "Template response has no task id"))
        pending[taskId] = Pending(config, taskId)
        taskStore.savePending(id, taskId, JSONObject().put("config", settings.extra(id)))
        return taskId
    }

    override suspend fun result(taskId: String): CloudTaskResult {
        cached[taskId]?.let { return it }
        taskStore.result(id, taskId)?.let { return it }
        val pending = pending[taskId] ?: taskStore.pending(id, taskId)?.let { stored ->
            val snapshot = stored.optString("config")
            val config = TemplateConfig.parse(snapshot, account().token).getOrNull() ?: return@let null
            Pending(config, taskId)
        } ?: return CloudTaskResult(taskId, "FAILED", emptyList(), "Template task expired", 0)
        val config = pending.config
        val values = mapOf("taskId" to taskId, "task_id" to taskId)
        val url = TemplateValues.renderString(config.statusUrlTemplate, values, account().token)
        val response = CloudHttp.requestJson(
            method = config.statusMethod.ifBlank { "GET" },
            url = url,
            body = null,
            headers = config.headers.mapValues { (_, value) ->
                TemplateValues.renderString(value, values, account().token)
            },
        )
        val status = TemplateValues.pathValue(response, config.statusPath)?.toString().orEmpty()
        return if (config.successValues.any { it.equals(status, ignoreCase = true) }) {
            val results = TemplateValues.resultUrls(response, config.resultUrlPath, config.resultType)
            CloudTaskResult(taskId, "SUCCESS", results, "", 0).also {
                taskStore.saveResult(id, taskId, it)
            }
        } else if (status.equals("cancelled", true) || status.equals("canceled", true)) {
            CloudTaskResult(taskId, "CANCELED", emptyList(), status, 0)
        } else if (status.equals("failed", true) || status.equals("error", true)) {
            CloudTaskResult(taskId, "FAILED", emptyList(), status, 0)
        } else {
            CloudTaskResult(taskId, "RUNNING", emptyList(), status, 0)
        }
    }

    override fun forgetTask(taskId: String) {
        cached.remove(taskId)
        pending.remove(taskId)
        taskStore.clear(id, taskId)
    }

    override suspend fun download(url: String): ByteArray = CloudHttp.download(url)

    override suspend fun downloadTo(url: String, target: java.io.File) {
        CloudHttp.downloadTo(url, target)
    }

    companion object {
        const val PROVIDER = "template"
    }
}

internal fun templatePreflightHealth(configured: Boolean, healthUrl: String): CloudHealth? = when {
    !configured -> CloudHealth(CloudHealthStatus.NOT_CONFIGURED)
    healthUrl.isBlank() -> CloudHealth(CloudHealthStatus.CONFIGURED_UNVERIFIED, modelCount = 1)
    else -> null
}

data class TemplateConfig(
    val submitUrl: String,
    val method: String,
    val headers: Map<String, String>,
    val bodyTemplate: JSONObject?,
    val taskIdPath: String,
    val statusUrlTemplate: String,
    val statusMethod: String,
    val statusPath: String,
    val successValues: List<String>,
    val resultUrlPath: String,
    val resultType: String,
    val fields: List<CloudInputRule>,
    val description: String,
    /** Optional side-effect-free GET endpoint used by "Test connection". */
    val healthUrl: String = "",
) {
    companion object {
        fun empty() = TemplateConfig(
            submitUrl = "", method = "POST", headers = emptyMap(), bodyTemplate = null,
            taskIdPath = "", statusUrlTemplate = "", statusMethod = "GET",
            statusPath = "status", successValues = DEFAULT_SUCCESS, resultUrlPath = "",
            resultType = "image", fields = emptyList(), description = "", healthUrl = "",
        )

        fun parse(raw: String, token: String): Result<TemplateConfig> = runCatching {
            val root = JSONObject(raw)
            val body = root.optJSONObject("bodyTemplate")
            val fields = root.optJSONArray("fields")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    CloudInputRule(
                        name = item.optString("name"),
                        type = item.optString("type", "string"),
                        required = item.optBoolean("required", false),
                        defaultValue = item.optString("default").takeIf { it.isNotBlank() },
                        min = item.optDouble("min").takeIf { !item.isNull("min") },
                        max = item.optDouble("max").takeIf { !item.isNull("max") },
                        options = item.optJSONArray("options")?.let { options ->
                            (0 until options.length()).map { options.optString(it) }
                        }.orEmpty(),
                    )
                }.filter { it.name.isNotBlank() }
            }.orEmpty()
            val headers = root.optJSONObject("headers")?.let { objectMap(it) }.orEmpty()
            val inferredFields = if (fields.isEmpty() && body != null) {
                TemplateValues.placeholders(body).map { name ->
                    CloudInputRule(
                        name = name,
                        type = when {
                            "prompt" in name.lowercase() -> "prompt"
                            "image" in name.lowercase() -> "image"
                            else -> "string"
                        },
                        required = "prompt" in name.lowercase(),
                    )
                }
            } else fields
            TemplateConfig(
                submitUrl = root.optString("submitUrl"),
                method = root.optString("method", "POST"),
                headers = headers,
                bodyTemplate = body,
                taskIdPath = root.optString("taskIdPath"),
                statusUrlTemplate = root.optString("statusUrlTemplate"),
                statusMethod = root.optString("statusMethod", "GET"),
                statusPath = root.optString("statusPath", "status"),
                successValues = root.optJSONArray("successValues")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }
                }.orEmpty().ifEmpty { DEFAULT_SUCCESS },
                resultUrlPath = root.optString("resultUrlPath"),
                resultType = root.optString("resultType", "image"),
                fields = inferredFields,
                description = root.optString("description"),
                healthUrl = root.optString("healthUrl"),
            )
        }

        private fun objectMap(o: JSONObject): Map<String, String> {
            val out = linkedMapOf<String, String>()
            o.keys().forEach { out[it] = o.optString(it) }
            return out
        }

        private val DEFAULT_SUCCESS = listOf("succeeded", "success", "completed", "Succeed")
    }
}

object TemplateValues {
    fun renderString(template: String, values: Map<String, String>, token: String): String =
        Regex("\\{\\{([^}]+)}}").replace(template) { match ->
            val key = match.groupValues[1].trim()
            when {
                key == "token" -> token
                else -> values[key].orEmpty()
            }
        }

    /**
     * [typedValues] preserves JSON numbers/booleans for exact `{{field}}` placeholders.
     * Embedded placeholders such as `run-{{name}}` always interpolate as strings.
     */
    fun render(
        template: JSONObject?,
        values: Map<String, String>,
        token: String,
        typedValues: JSONObject? = null,
    ): JSONObject? {
        template ?: return null
        return renderObject(template, values, token, typedValues) as? JSONObject
    }

    private fun renderObject(
        value: Any?,
        values: Map<String, String>,
        token: String,
        typedValues: JSONObject?,
    ): Any? = when (value) {
        is JSONObject -> {
            val out = JSONObject()
            value.keys().forEach { key ->
                out.put(key, renderObject(value.opt(key), values, token, typedValues))
            }
            out
        }
        is JSONArray -> {
            val out = JSONArray()
            (0 until value.length()).forEach { index ->
                out.put(renderObject(value.opt(index), values, token, typedValues))
            }
            out
        }
        is String -> renderScalar(value, values, token, typedValues)
        else -> value
    }

    private fun renderScalar(
        value: String,
        values: Map<String, String>,
        token: String,
        typedValues: JSONObject?,
    ): Any {
        val exact = Regex("^\\{\\{([^}]+)}}$").matchEntire(value)
        if (exact != null) {
            val key = exact.groupValues[1].trim()
            if (key == "token") return token
            if (typedValues != null && typedValues.has(key)) {
                val typed = typedValues.opt(key)
                return if (typed == null || typed == JSONObject.NULL) "" else typed
            }
            return values[key] ?: ""
        }
        return renderString(value, values, token)
    }

    fun placeholders(tree: JSONObject): List<String> {
        val names = linkedSetOf<String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { visit(value.opt(it)) }
                is JSONArray -> (0 until value.length()).forEach { visit(value.opt(it)) }
                is String -> Regex("\\{\\{([^}]+)}}").findAll(value).forEach {
                    names += it.groupValues[1].trim()
                }
            }
        }
        visit(tree)
        names.remove("token")
        return names.toList()
    }

    fun pathValue(root: JSONObject, path: String): Any? {
        if (path.isBlank()) return null
        var current: Any? = root
        path.split('.').forEach { segment ->
            val node = current
            current = when (node) {
                is JSONObject -> node.opt(segment)
                is JSONArray -> segment.toIntOrNull()?.let { node.opt(it) }
                else -> null
            }
        }
        return current
    }

    fun resultUrls(root: JSONObject, path: String, kind: String): List<CloudResult> {
        val value = pathValue(root, path) ?: return emptyList()
        val urls = mutableListOf<String>()
        fun collect(node: Any?) {
            when (node) {
                is String -> if (node.startsWith("http") || node.startsWith("data:")) urls += node
                is JSONObject -> listOf("url", "image", "video", "audio", "output").forEach { key ->
                    node.optString(key).takeIf { it.isNotBlank() }?.let { urls += it }
                }
                is JSONArray -> (0 until node.length()).forEach { collect(node.opt(it)) }
            }
        }
        collect(value)
        return urls.distinct().map { url ->
            CloudResult(
                url = url,
                type = kind,
                fileType = url.substringBefore('?').substringAfterLast('.', "")
                    .lowercase().takeIf { it.length in 2..5 }
                    ?: if (kind == "video") "mp4" else "png",
                outputType = "output",
            )
        }
    }

    /** Best-effort config inference from one sample request and response pair. */
    fun inferConfig(sampleRequest: String, sampleResponse: String): JSONObject {
        val config = JSONObject()
        val request = runCatching { JSONObject(sampleRequest) }.getOrNull()
        val response = runCatching { JSONObject(sampleResponse) }.getOrNull()
        if (request != null) {
            val fields = JSONArray()
            val template = JSONObject(request.toString())
            request.keys().forEach { key ->
                val value = request.opt(key)
                val type = when (value) {
                    is Number -> "number"
                    is Boolean -> "boolean"
                    is JSONArray, is JSONObject -> null
                    else -> if ("prompt" in key.lowercase() || "text" in key.lowercase()) "prompt" else "string"
                }
                if (type != null) {
                    fields.put(
                        JSONObject()
                            .put("name", key)
                            .put("type", type)
                            .put("required", true)
                            .put("default", value?.toString() ?: "")
                    )
                    template.put(key, "{{" + key + "}}")
                }
            }
            if (fields.length() > 0) {
                config.put("fields", fields)
                config.put("bodyTemplate", template)
            }
        }
        if (response != null) {
            val paths = findPaths(response)
            paths["taskIdPath"]?.let { config.put("taskIdPath", it) }
            paths["statusPath"]?.let { config.put("statusPath", it) }
            paths["resultUrlPath"]?.let { config.put("resultUrlPath", it) }
            val status = paths["statusPath"]?.let { pathValue(response, it)?.toString() }
            if (status != null) config.put("successValues", JSONArray().put(status))
        }
        return config
    }

    private fun findPaths(root: JSONObject): Map<String, String> {
        val found = mutableMapOf<String, String>()
        fun visit(node: Any?, path: String) {
            when (node) {
                is JSONObject -> node.keys().forEach { key ->
                    val next = if (path.isBlank()) key else "$path.$key"
                    val lower = key.lowercase()
                    when {
                        lower in setOf("id", "taskid", "task_id", "requestid", "request_id") &&
                            "taskIdPath" !in found -> found["taskIdPath"] = next
                        lower in setOf("status", "state") && "statusPath" !in found ->
                            found["statusPath"] = next
                        lower in setOf("url", "output", "image", "video", "images", "videos", "results") &&
                            "resultUrlPath" !in found -> found["resultUrlPath"] = next
                    }
                    visit(node.opt(key), next)
                }
                is JSONArray -> (0 until node.length()).forEach { visit(node.opt(it), path) }
            }
        }
        visit(root, "")
        return found
    }
}
