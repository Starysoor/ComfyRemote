package com.comfyremote.app.data.cloud

import org.json.JSONObject

/**
 * A hosted workflow backend. AutoDL is the first implementation; adding
 * RunningHub/LibLibAI/other vendors means implementing this interface and
 * registering it in [CloudProviderRegistry].
 */
interface CloudProvider {
    val id: String
    val label: String
    val pathPrefix: String
    val baseUrl: String

    fun configured(): Boolean

    suspend fun catalog(force: Boolean = false): List<CloudWorkflowSummary>
    suspend fun detail(workflowId: String): CloudWorkflowSpec
    suspend fun submit(workflowId: String, body: JSONObject): String
    suspend fun result(taskId: String): CloudTaskResult
    suspend fun download(url: String): ByteArray

    /** Prefer this for large media; providers can stream directly to [target]. */
    suspend fun downloadTo(url: String, target: java.io.File) {
        target.writeBytes(download(url))
    }
}

data class CloudProviderInfo(
    val id: String,
    val label: String,
    val pathPrefix: String,
    val configured: Boolean,
)

class CloudProviderRegistry(private val providers: List<CloudProvider>) {
    val all: List<CloudProvider> get() = providers

    /** Provider cards for the future multi-provider settings page. */
    fun info(): List<CloudProviderInfo> = providers.map {
        CloudProviderInfo(it.id, it.label, it.pathPrefix, it.configured())
    }

    fun byId(id: String): CloudProvider? = providers.firstOrNull { it.id == id }

    fun forPath(path: String): CloudProvider? =
        providers.firstOrNull { path.startsWith(it.pathPrefix) }

    fun byPrefix(prefix: String): CloudProvider? =
        providers.firstOrNull { it.pathPrefix == prefix }
}
