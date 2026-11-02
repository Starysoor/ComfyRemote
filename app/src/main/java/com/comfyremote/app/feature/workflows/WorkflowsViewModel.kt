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
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowsUiState(
    val workflows: List<WorkflowEntry> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val recentPaths: List<String> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
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
            return workflows.asSequence()
                .filter { q.isBlank() || it.name.contains(q, true) || it.path.contains(q, true) }
                .sortedWith(
                    compareByDescending<WorkflowEntry> { it.path in favoritePaths }
                        .thenBy { recentRank[it.path] ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase() }
                )
                .toList()
        }
}

class WorkflowsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        WorkflowsUiState(
            recentPaths = container.workflowUsageRepository.recentPaths(),
            favoritePaths = container.workflowUsageRepository.favorites(),
            recentPresets = recentSetups(container.promptPresetRepository.load()),
            recentResults = recentResults(container.historyRepository.load(), container.serverSession.session.value?.url),
            session = container.serverSession.session.value,
            servers = container.serverRepository.load(),
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
                        recentResults = recentResults(container.historyRepository.load(), session?.url),
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
                _state.update { state -> state.copy(recentResults = recentResults(history, state.session?.url)) }
            }
        }
        load()
        refreshServerHealth()
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun load() = loadInternal(force = false)

    fun refreshServerHealth() {
        val servers = container.serverRepository.load()
        if (servers.isEmpty() || _state.value.probingServers) return
        _state.update { it.copy(probingServers = true) }
        viewModelScope.launch {
            val health = container.serverHealthRepository.probeAll(servers)
            _state.update { it.copy(serverHealth = health, probingServers = false) }
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
                    _state.update { state -> state.copy(message = L10n.t("已切换服务器", "Server switched")) }
                    refreshServerHealth()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            switchingServer = null,
                            message = L10n.t("切换服务器失败：", "Failed to switch server: ") + (error.message ?: "error"),
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
            _state.update { it.copy(message = L10n.t("没有检测到在线服务器", "No online server detected")) }
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
                            error = L10n.t("获取工作流失败：", "Failed to list workflows: ") + (e.message ?: "error")
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

    fun prepareResult(item: HistoryItem): WorkflowEntry? {
        if (item.wfPath.isBlank()) return null
        container.draftTransferRepository.put(DraftTransfer(item.wfPath, item.wfName, item.values, item.randomSeed))
        return workflowEntryFor(item.wfPath, item.wfName)
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

        private fun recentResults(items: List<HistoryItem>, serverUrl: String?): List<HistoryItem> = items
            .asSequence()
            .filter { serverUrl.isNullOrBlank() || it.serverUrl.isBlank() || it.serverUrl == serverUrl }
            .sortedByDescending { it.time }
            .take(4)
            .toList()
    }
}

fun workflowType(entry: WorkflowEntry): String = when {
    entry.name.contains("video", true) || entry.name.contains("wan", true) -> "VIDEO"
    entry.name.contains("upscale", true) -> "UPSCALE"
    entry.name.contains("inpaint", true) -> "INPAINT"
    entry.name.contains("edit", true) -> "EDIT"
    entry.name.contains("i2i", true) || entry.name.contains("img2img", true) -> "IMG2IMG"
    entry.name.contains("t2i", true) || entry.name.contains("txt2img", true) -> "TXT2IMG"
    else -> "FLOW"
}
