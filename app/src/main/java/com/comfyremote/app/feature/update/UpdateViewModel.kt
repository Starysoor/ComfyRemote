package com.comfyremote.app.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.GhRelease
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val currentVersion: String = "",
    val token: String = "",
    val checking: Boolean = false,
    val release: GhRelease? = null,
    val status: String? = null,
    val isError: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float? = null,
)

class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        UpdateUiState(
            currentVersion = container.updateRepository.currentVersion(),
            token = container.updateSettingsStore.ghToken().orEmpty(),
        )
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun updateToken(value: String) = _state.update { it.copy(token = value) }

    fun check() {
        if (_state.value.checking || _state.value.downloading) return
        val token = _state.value.token.trim().ifBlank { null }
        container.updateSettingsStore.saveGhToken(token)
        _state.update { it.copy(checking = true, status = L10n.t("正在检查…", "Checking…"), isError = false) }
        viewModelScope.launch {
            runCatching { container.updateRepository.latest(token) }
                .onSuccess { release ->
                    container.updateSettingsStore.markUpdateChecked()
                    if (container.updateRepository.isNewer(release)) {
                        _state.update { it.copy(checking = false, release = release, status = null) }
                    } else {
                        _state.update {
                            it.copy(
                                checking = false,
                                release = null,
                                status = L10n.t("已是最新版本", "Already up to date"),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            checking = false,
                            status = L10n.t("检查失败：", "Check failed: ") + (e.message ?: "error").take(200),
                            isError = true,
                        )
                    }
                }
        }
    }

    fun downloadAndInstall() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return
        val token = container.updateSettingsStore.ghToken()
        _state.update { it.copy(downloading = true, progress = 0f, isError = false) }
        viewModelScope.launch {
            runCatching {
                container.updateRepository.download(token, release) { p ->
                    _state.update { it.copy(progress = p) }
                }
            }.onSuccess { apk ->
                _state.update { it.copy(downloading = false, progress = 1f) }
                container.updateRepository.install(apk)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        downloading = false,
                        status = L10n.t("下载失败：", "Download failed: ") + (e.message ?: "error").take(200),
                        isError = true,
                    )
                }
            }
        }
    }
}
