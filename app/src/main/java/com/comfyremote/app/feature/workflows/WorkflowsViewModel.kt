package com.comfyremote.app.feature.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.WorkflowEntry
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.data.repository.ServerHealth
import com.comfyremote.app.data.repository.ServerSession
import com.comfyremote.app.R
import com.comfyremote.app.ui.AppStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkflowSource { LOCAL, CLOUD }

data class WorkflowsUiState(
    val workflows: List<WorkflowEntry> = emptyList(),
    val cloudWorkflows: List<WorkflowEntry> = emptyList(),
    val source: WorkflowSource = WorkflowSource.LOCAL,
    val cloudLoading: Boolean = false,
    val cloudError: String? = null,
    val cloudLoaded: Boolean = false,
    val cloudConfigured: Boolean = false,
    val cloudPrefixes: Set<String> = emptySet(),
    val cloudBaseUrls: Set<String> = emptySet(),
    val reuseTransfer: DraftTransfer? = null,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val recentPaths: List<String> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
    val defaultPaths: Set<String> = emptySet(),
    val recentPresets: List<PromptPreset> = emptyList(),
    val recentResults: List<HistoryItem> = emptyList(),
    val session: ServerSession? = null,
    val servers: List<ServerEntry> = emptyList(),
    val serverHealth: Map<String, ServerHealth> = emptyMap(),
    val appActiveByServer: Map<String, Int> = emptyMap(),
    val probingServers: Boolean = false,
    val switchingServer: String? = null,
    val message: String? = null,
) {
    val visible: List<WorkflowEntry>
        get() {
            val q = query.trim()
            val recentRank = recentPaths.withIndex().associate { it.value to it.index }
            val sourceList = if (source == WorkflowSource.CLOUD) cloudWorkflows else workflows
            return sourceList.asSequence()
                .filter {
                    q.isBlank() || it.name.contains(q, true) || it.path.contains(q, true) ||
                        it.description.contains(q, true)
                }
                .sortedWith(
                    compareByDescending<WorkflowEntry> { it.path in favoritePaths }
                        .thenByDescending { it.path in defaultPaths }
                        .thenBy { recentRank[it.path] ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase() }
                )
                .toList()
        }
}

class WorkflowsViewModel(private val container: AppContainer) : ViewModel() {
    private val pendingAtStart = container.draftTransferRepository.peek()
        ?.takeIf { it.reuseAcrossWorkflows }
    private val initialSource = when {
        pendingAtStart?.workflowPath?.let { container.cloudProviders.forPath(it) != null } == true ->
            WorkflowSource.CLOUD
        pendingAtStart != null -> WorkflowSource.LOCAL
        container.serverSession.connected -> WorkflowSource.LOCAL
        else -> WorkflowSource.CLOUD
    }
    private val _state = MutableStateFlow(
        WorkflowsUiState(
            recentPaths = container.workflowUsageRepository.recentPaths(),
            favoritePaths = container.workflowUsageRepository.favorites(),
            defaultPaths = defaultCloudPaths(),
            recentPresets = recentSetups(container.promptPresetRepository.load()),
            recentResults = recentResults(container.historyRepository.load()),
            session = container.serverSession.session.value,
            servers = container.serverRepository.load(),
            source = initialSource,
            reuseTransfer = pendingAtStart,
            cloudConfigured = container.cloudProviders.all.any { it.configured() },
            cloudPrefixes = container.cloudProviders.all.mapTo(hashSetOf()) { it.pathPrefix },
            cloudBaseUrls = container.cloudProviders.all.mapTo(hashSetOf()) { it.baseUrl },
        )
    )
    val state: StateFlow<WorkflowsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var previousUrl = container.serverSession.session.value?.url
            container.serverSession.session.collect { session ->
                val changed = session?.url != previousUrl
                previousUrl = session?.url
                _state.update {
                    it.copy(
                        session = session,
                        workflows = if (session == null || changed) emptyList() else it.workflows,
                        error = if (session == null || changed) null else it.error,
                        recentResults = recentResults(container.historyRepository.load()),
                        switchingServer = null,
                    )
                }
                if (changed && session != null) loadInternal(force = true)
            }
        }
        viewModelScope.launch {
            container.serverRepository.servers.collect { servers ->
                _state.update { it.copy(servers = servers) }
                refreshServerHealth()
            }
        }
        viewModelScope.launch {
            container.generationManager.tasks.collect { tasks ->
                _state.update { state ->
                    state.copy(
                        appActiveByServer = tasks.filter { it.isActive }
                            .groupingBy { it.request.serverUrl }
                            .eachCount()
                    )
                }
            }
        }
        viewModelScope.launch {
            container.promptPresetRepository.presets.collect { presets ->
                _state.update { it.copy(recentPresets = recentSetups(presets)) }
            }
        }
        viewModelScope.launch {
            container.historyRepository.items.collect { history ->
                _state.update { state -> state.copy(recentResults = recentResults(history)) }
            }
        }
        load()
        refreshServerHealth()
        if (_state.value.source == WorkflowSource.CLOUD) loadCloud(force = false)
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Pick up a transfer that asked the user to choose a different workflow. */
    fun syncReuse() {
        val pending = container.draftTransferRepository.peek()
        if (pending?.reuseAcrossWorkflows != true) {
            _state.update { it.copy(reuseTransfer = null) }
            return
        }
        val source = if (container.cloudProviders.forPath(pending.workflowPath) != null) {
            WorkflowSource.CLOUD
        } else {
            WorkflowSource.LOCAL
        }
        _state.update { it.copy(source = source, reuseTransfer = pending, query = "") }
        if (source == WorkflowSource.CLOUD && !_state.value.cloudLoaded) loadCloud(force = false)
    }

    fun cancelReuse() {
        container.draftTransferRepository.clear()
        _state.update { it.copy(reuseTransfer = null) }
    }

    fun setSource(source: WorkflowSource) {
        if (_state.value.source == source) return
        _state.update { it.copy(source = source, query = "") }
        if (source == WorkflowSource.CLOUD && !_state.value.cloudLoaded) loadCloud(force = false)
        if (source == WorkflowSource.LOCAL) {
            _state.update {
                it.copy(cloudConfigured = container.cloudProviders.all.any { provider -> provider.configured() })
            }
            loadInternal(force = false)
        }
    }

    fun load() {
        if (_state.value.source == WorkflowSource.CLOUD) loadCloud(force = true)
        else loadInternal(force = false)
    }

    fun loadCloud(force: Boolean = false) {
        if (_state.value.cloudLoading) return
        val providers = container.cloudProviders.all
        _state.update {
            it.copy(
                cloudLoading = true,
                cloudError = null,
                cloudConfigured = providers.any { provider -> provider.configured() },
            )
        }
        viewModelScope.launch {
            val entries = mutableListOf<WorkflowEntry>()
            val errors = mutableListOf<String>()
            providers.forEach { provider ->
                runCatching { provider.catalog(force) }
                    .onSuccess { list ->
                        entries += list.map { summary ->
                            summary.toEntry(provider.pathPrefix, provider.label)
                        }
                    }
                    .onFailure { error ->
                        errors += "${provider.label}: ${error.message ?: "error"}"
                    }
            }
            _state.update {
                it.copy(
                    cloudLoading = false,
                    cloudLoaded = true,
                    cloudWorkflows = entries,
                    defaultPaths = defaultCloudPaths(),
                    cloudError = if (entries.isEmpty() && errors.isNotEmpty()) {
                        AppStrings.get(R.string.wf_err_cloud_load, errors.joinToString("; "))
                    } else null,
                    cloudConfigured = providers.any { provider -> provider.configured() },
                )
            }
        }
    }

    private fun defaultCloudPaths(): Set<String> {
        val paths = container.cloudProviders.all.mapNotNull { provider ->
            container.cloudProviderSettingsStore.defaultModel(provider.id)
                .takeIf { it.isNotBlank() }
                ?.let { provider.pathPrefix + it }
        }.toMutableSet()
        container.appSettingsStore.settings.value.defaultVideoWorkflowId
            .takeIf { it.isNotBlank() }
            ?.let { paths += com.comfyremote.app.data.cloud.AutoDlConfig.PATH_PREFIX + it }
        return paths
    }

    fun refreshServerHealth() {
        val servers = container.serverRepository.load()
        if (servers.isEmpty() || _state.value.probingServers) return
        _state.update { it.copy(probingServers = true) }
        viewModelScope.launch {
            coroutineScope {
                servers.distinctBy { it.url }.map { server ->
                    async {
                        val health = container.serverHealthRepository.probe(server.url)
                        _state.update { state ->
                            state.copy(serverHealth = state.serverHealth + (server.url to health))
                        }
                    }
                }.awaitAll()
            }
            _state.update { it.copy(probingServers = false) }
        }
    }

    fun switchServer(url: String) {
        if (_state.value.switchingServer != null) return
        if (_state.value.session?.url == url) return
        _state.update { it.copy(switchingServer = url, message = null) }
        viewModelScope.launch {
            runCatching { container.serverSession.connect(url) }
                .onSuccess {
                    container.serverRepository.remember(url)
                    container.serverRepository.saveLastUrl(url)
                    _state.update { state ->
                        state.copy(source = WorkflowSource.LOCAL, message = AppStrings.get(R.string.wf_server_switched))
                    }
                    refreshServerHealth()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            switchingServer = null,
                            message = AppStrings.get(R.string.wf_switch_failed, error.message ?: "error"),
                        )
                    }
                }
        }
    }

    fun switchToLeastBusy() {
        val candidate = _state.value.servers
            .mapNotNull { server -> _state.value.serverHealth[server.url]?.takeIf { it.online } }
            .minWithOrNull(compareBy<ServerHealth> { it.loadScore + (_state.value.appActiveByServer[it.url] ?: 0) * 100 }
                .thenBy { it.latencyMs ?: Long.MAX_VALUE })
        if (candidate == null) {
            _state.update { it.copy(message = AppStrings.get(R.string.wf_no_online_server)) }
        } else switchServer(candidate.url)
    }

    private fun loadInternal(force: Boolean) {
        if (!container.serverSession.connected || (_state.value.loading && !force)) return
        val requestedUrl = container.serverSession.url
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { container.workflowRepository.list() }
                .onSuccess { list ->
                    if (container.serverSession.url != requestedUrl) return@onSuccess
                    _state.update {
                        it.copy(
                            loading = false,
                            workflows = list,
                            recentPaths = container.workflowUsageRepository.recentPaths(),
                            favoritePaths = container.workflowUsageRepository.favorites(),
                        )
                    }
                }
                .onFailure { e ->
                    if (container.serverSession.url != requestedUrl) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            error = AppStrings.get(R.string.wf_err_list, e.message ?: "error")
                        )
                    }
                }
        }
    }

    fun opened(entry: WorkflowEntry) {
        container.workflowUsageRepository.markOpened(entry.path)
        _state.update { it.copy(recentPaths = container.workflowUsageRepository.recentPaths()) }
    }

    fun toggleFavorite(path: String) {
        _state.update { it.copy(favoritePaths = container.workflowUsageRepository.toggleFavorite(path)) }
    }

    fun preparePreset(preset: PromptPreset): WorkflowEntry? {
        val path = preset.workflowPath ?: return null
        container.promptPresetRepository.markUsed(preset.id)
        container.draftTransferRepository.put(
            DraftTransfer(
                workflowPath = path,
                workflowName = workflowNameFor(path),
                values = preset.values,
                randomSeed = preset.randomSeed ?: true,
            )
        )
        return workflowEntryFor(path)
    }

    fun prepareResult(item: HistoryItem): DraftTransfer? {
        if (item.wfPath.isBlank()) return null
        val transfer = DraftTransfer(
            workflowPath = item.wfPath,
            workflowName = item.wfName,
            values = item.values,
            randomSeed = item.randomSeed,
            sourceFields = item.fields,
            serverUrl = item.serverUrl.takeIf(String::isNotBlank),
        )
        container.draftTransferRepository.put(transfer)
        return transfer
    }

    private fun workflowEntryFor(path: String, fallbackName: String = workflowNameFor(path)): WorkflowEntry =
        _state.value.workflows.firstOrNull { it.path == path } ?: WorkflowEntry(path, fallbackName, "")

    private fun workflowNameFor(path: String): String =
        _state.value.workflows.firstOrNull { it.path == path }?.name
            ?: path.substringAfterLast('/').removeSuffix(".json")

    companion object {
        private fun recentSetups(presets: List<PromptPreset>): List<PromptPreset> = presets
            .filter { it.isWorkflowPreset }
            .sortedWith(compareByDescending<PromptPreset> { it.favorite }.thenByDescending { it.lastUsedAt ?: it.createdAt })
            .take(4)

        private fun recentResults(items: List<HistoryItem>): List<HistoryItem> =
            items.sortedByDescending { it.time }
    }
}

fun workflowType(entry: WorkflowEntry): String = when {
    entry.cloud -> entry.kind.uppercase().ifBlank { "CLOUD" }
    entry.name.contains("video", true) || entry.name.contains("wan", true) -> "VIDEO"
    entry.name.contains("upscale", true) -> "UPSCALE"
    entry.name.contains("inpaint", true) -> "INPAINT"
    entry.name.contains("edit", true) -> "EDIT"
    entry.name.contains("i2i", true) || entry.name.contains("img2img", true) -> "IMG2IMG"
    entry.name.contains("t2i", true) || entry.name.contains("txt2img", true) -> "TXT2IMG"
    else -> "FLOW"
}
