package com.comfyremote.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudSettingsUiState(
    val token: String = "",
    val configured: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val workflows: List<CloudWorkflowSummary> = emptyList(),
    val defaultVideoId: String = "",
    val spendTotal: Int = 0,
    val spendCount: Int = 0,
    val message: String? = null,
)

class CloudSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        CloudSettingsUiState(
            token = container.autoDlSettingsStore.token().orEmpty(),
            configured = container.autoDlRepository.configured(),
            defaultVideoId = container.appSettingsStore.settings.value.defaultVideoWorkflowId,
        )
    )
    val state: StateFlow<CloudSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.cloudSpendStore.entries.collect { entries ->
                _state.update {
                    it.copy(
                        spendTotal = entries.sumOf { entry -> entry.points ?: 0 },
                        spendCount = entries.size,
                    )
                }
            }
        }
    }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun saveToken() {
        container.autoDlSettingsStore.saveToken(_state.value.token)
        _state.update {
            it.copy(
                configured = container.autoDlRepository.configured(),
                message = L10n.t("AutoDL Token 已保存", "AutoDL token saved"),
            )
        }
    }

    fun setDefaultVideo(id: String) {
        container.appSettingsStore.update { it.copy(defaultVideoWorkflowId = id) }
        _state.update { it.copy(defaultVideoId = id) }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { container.autoDlRepository.catalog(force = true) }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            loading = false,
                            workflows = list.filter { workflow -> workflow.kind == "video" }
                                .sortedBy { workflow -> workflow.name.lowercase() },
                            configured = container.autoDlRepository.configured(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = L10n.t("读取云端目录失败：", "Could not load cloud catalogue: ") + (e.message ?: "error"),
                        )
                    }
                }
        }
    }

    fun clearCloudInputs() {
        container.cloudInputStore.clearAll()
        _state.update { it.copy(message = L10n.t("云端输入缓存已清除", "Cloud input cache cleared")) }
    }

    fun clearSpend() {
        container.cloudSpendStore.clear()
        _state.update { it.copy(message = L10n.t("本地预估消耗已清除", "Local spend estimate cleared")) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
