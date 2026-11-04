package com.comfyremote.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.cloud.CloudHealth
import com.comfyremote.app.data.cloud.CloudHealthStatus
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.R
import com.comfyremote.app.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudSettingsUiState(
    val token: String = "",
    val configured: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val workflows: List<CloudWorkflowSummary> = emptyList(),
    val defaultVideoId: String = "",
    val spendTotal: Int = 0,
    val spendCount: Int = 0,
    val testing: Boolean = false,
    val health: CloudHealth? = null,
    val message: String? = null,
)

class CloudSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        CloudSettingsUiState(
            token = container.autoDlSettingsStore.token().orEmpty(),
            configured = container.autoDlRepository.configured(),
            enabled = container.cloudProviders.isEnabled(AutoDlConfig.PROVIDER),
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
        val wasConfigured = container.autoDlRepository.configured()
        container.autoDlSettingsStore.saveToken(_state.value.token)
        if (!wasConfigured && container.autoDlRepository.configured()) {
            container.cloudProviderSettingsStore.setProviderEnabled(AutoDlConfig.PROVIDER, true)
        }
        _state.update {
            it.copy(
                configured = container.autoDlRepository.configured(),
                enabled = container.cloudProviders.isEnabled(AutoDlConfig.PROVIDER),
                health = null,
                message = AppStrings.get(R.string.settings_cloud_token_saved),
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        container.cloudProviderSettingsStore.setProviderEnabled(AutoDlConfig.PROVIDER, enabled)
        _state.update { it.copy(enabled = container.cloudProviders.isEnabled(AutoDlConfig.PROVIDER)) }
    }

    fun clearToken() {
        container.autoDlSettingsStore.saveToken("")
        container.cloudProviderSettingsStore.setProviderEnabled(AutoDlConfig.PROVIDER, false)
        _state.update {
            it.copy(
                token = "",
                configured = false,
                enabled = false,
                health = null,
                workflows = emptyList(),
                message = AppStrings.get(R.string.cloud_provider_disconnected),
            )
        }
    }

    fun setDefaultVideo(id: String) {
        container.appSettingsStore.update { it.copy(defaultVideoWorkflowId = id) }
        _state.update { it.copy(defaultVideoId = id) }
    }

    fun refresh() {
        if (_state.value.loading) return
        if (!container.autoDlRepository.configured()) {
            _state.update {
                it.copy(
                    loading = false,
                    workflows = emptyList(),
                    configured = false,
                    enabled = container.cloudProviders.isEnabled(AutoDlConfig.PROVIDER),
                    error = null,
                )
            }
            return
        }
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
                            error = AppStrings.get(R.string.settings_cloud_catalog_failed, e.message ?: "error"),
                        )
                    }
                }
        }
    }

    fun testConnection() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, health = null) }
        viewModelScope.launch {
            val provider = container.cloudProviders.byId(AutoDlConfig.PROVIDER)
            val result = if (provider == null) {
                CloudHealth(CloudHealthStatus.ERROR, message = "AutoDL provider missing")
            } else {
                withContext(Dispatchers.IO) { provider.check() }
            }
            _state.update { it.copy(testing = false, health = result) }
        }
    }

    fun clearCloudInputs() {
        container.clearCloudInputCache()
        _state.update { it.copy(message = AppStrings.get(R.string.settings_cloud_inputs_cleared)) }
    }

    fun clearSpend() {
        container.cloudSpendStore.clear()
        _state.update { it.copy(message = AppStrings.get(R.string.settings_cloud_spend_cleared)) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
