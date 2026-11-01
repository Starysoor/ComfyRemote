package com.comfyremote.app.feature.workflows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.WorkflowEntry
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
    val session: ServerSession? = null,
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
            session = container.serverSession.session.value,
        )
    )
    val state: StateFlow<WorkflowsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.serverSession.session.collect { session -> _state.update { it.copy(session = session) } }
        }
        load()
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun load() {
        if (!container.serverSession.connected || _state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { container.workflowRepository.list() }
                .onSuccess { list ->
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
}

fun workflowType(entry: WorkflowEntry): String = when {
    entry.name.contains("video", true) || entry.name.contains("wan", true) -> "VIDEO"
    entry.name.contains("edit", true) -> "EDIT"
    entry.name.contains("i2i", true) || entry.name.contains("img2img", true) -> "IMG2IMG"
    entry.name.contains("t2i", true) || entry.name.contains("txt2img", true) -> "TXT2IMG"
    else -> "FLOW"
}
