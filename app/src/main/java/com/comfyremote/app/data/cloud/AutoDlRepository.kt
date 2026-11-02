package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException

/**
 * Hosted-cloud workflow façade. Metadata is public; submitting needs a token.
 * The catalogue is cached briefly because the Create screen can re-read it on
 * every tab switch.
 */
class AutoDlRepository(
    private val settings: AutoDlSettingsStore,
    private val api: AutoDlApi = AutoDlApi(),
) : CloudProvider {
    override val id: String = AutoDlConfig.PROVIDER
    override val label: String = "AutoDL.Art"
    override val pathPrefix: String = AutoDlConfig.PATH_PREFIX
    override val baseUrl: String = AutoDlConfig.BASE_URL

    private val lock = Any()
    private var cachedCatalog: List<CloudWorkflowSummary>? = null
    private var cachedAt = 0L

    override fun configured(): Boolean = settings.configured()

    fun token(): String? = settings.token()

    override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cached = cachedCatalog
            if (!force && cached != null && now - cachedAt < CATALOG_TTL_MS) return cached
        }
        val fresh = api.listWorkflows()
        synchronized(lock) {
            cachedCatalog = fresh
            cachedAt = System.currentTimeMillis()
        }
        return fresh
    }

    override suspend fun detail(workflowId: String): CloudWorkflowSpec = api.workflowDetail(workflowId)

    override suspend fun submit(workflowId: String, body: org.json.JSONObject): String {
        val token = settings.token() ?: throw ComfyException("请先在设置 → 云端服务中配置 AutoDL Token")
        return api.submit(workflowId, body, token)
    }

    override suspend fun result(taskId: String): CloudTaskResult {
        val token = settings.token() ?: throw ComfyException("AutoDL Token 已失效或被清除")
        return api.result(taskId, token)
    }

    override suspend fun download(url: String): ByteArray = api.download(url)

    companion object { private const val CATALOG_TTL_MS = 10 * 60_000L }
}
