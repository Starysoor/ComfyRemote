package com.comfyremote.app.data.cloud

import kotlin.coroutines.cancellation.CancellationException
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
    val settingsSummary: String get() = "Hosted workflow API"
    val defaultModels: List<String> get() = emptyList()

    /** Settings can offer a "Refresh models" action when the provider enumerates models. */
    val supportsModelDiscovery: Boolean get() = false
    /** Settings can edit extra HTTP headers for this provider. */
    val supportsCustomHeaders: Boolean get() = false

    fun configured(): Boolean

    /** Endpoint actually used for requests; settings-backed providers override this. */
    fun endpoint(): String = baseUrl

    suspend fun catalog(force: Boolean = false): List<CloudWorkflowSummary>
    suspend fun detail(workflowId: String): CloudWorkflowSpec
    suspend fun submit(workflowId: String, body: JSONObject): String
    suspend fun result(taskId: String): CloudTaskResult
    suspend fun download(url: String): ByteArray

    /** Drops any persisted recovery state once the task reached a terminal phase. */
    fun forgetTask(taskId: String) = Unit

    /**
     * Optional live model discovery (`GET /models` and similar). Null means the
     * provider cannot enumerate models and the manual list stays authoritative.
     */
    suspend fun discoverModels(): List<String>? = null

    /**
     * Lightweight connection check used by Settings. Providers that can discover
     * models hit the network; others fall back to their catalogue.
     */
    suspend fun check(): CloudHealth {
        if (!configured()) {
            return CloudHealth(CloudHealthStatus.NOT_CONFIGURED, checkedAt = System.currentTimeMillis())
        }
        return try {
            val discovered = discoverModels()
            if (discovered != null) {
                CloudHealth(
                    status = CloudHealthStatus.READY,
                    modelCount = discovered.size,
                    checkedAt = System.currentTimeMillis(),
                )
            } else {
                val list = catalog(force = true)
                CloudHealth(
                    status = CloudHealthStatus.READY,
                    modelCount = list.size,
                    checkedAt = System.currentTimeMillis(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            classifyCloudFailure(error).copy(checkedAt = System.currentTimeMillis())
        }
    }

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
    val summary: String = "",
)

class CloudProviderRegistry(private val providers: List<CloudProvider>) {
    val all: List<CloudProvider> get() = providers

    /** Provider cards for the future multi-provider settings page. */
    fun info(): List<CloudProviderInfo> = providers.map {
        CloudProviderInfo(it.id, it.label, it.pathPrefix, it.configured(), it.settingsSummary)
    }

    fun byId(id: String): CloudProvider? = providers.firstOrNull { it.id == id }

    fun forPath(path: String): CloudProvider? =
        providers.firstOrNull { path.startsWith(it.pathPrefix) }

    fun byPrefix(prefix: String): CloudProvider? =
        providers.firstOrNull { it.pathPrefix == prefix }
}

/**
 * Stable draft scope for a cloud provider. Never falls back to a local ComfyUI
 * server URL, so custom providers keep their drafts without a local connection.
 */
fun CloudProvider.draftScopeKey(): String = cloudDraftScope(id, endpoint())

internal fun cloudDraftScope(providerId: String, endpoint: String): String {
    val resolved = endpoint.trim().trimEnd('/')
    return if (resolved.isBlank()) "cloud:$providerId" else "cloud:$providerId:$resolved"
}
