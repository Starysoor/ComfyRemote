package com.comfyremote.app.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.data.discovery.DiscoveryEvent
import com.comfyremote.app.data.repository.ServerSession
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val url: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    val session: ServerSession? = null,
    val servers: List<ServerEntry> = emptyList(),
    val scanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 254,
    val scanFound: List<ServerEntry> = emptyList(),
    val scanError: String? = null,
)

class ConnectViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        ConnectUiState(
            url = container.serverRepository.lastUrl(),
            session = container.serverSession.session.value,
            servers = container.serverRepository.load(),
        )
    )
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private val _connectedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connectedEvents: SharedFlow<Unit> = _connectedEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            container.serverRepository.servers.collect { servers ->
                _state.update { it.copy(servers = servers) }
            }
        }
        viewModelScope.launch {
            container.serverSession.session.collect { session ->
                _state.update { it.copy(session = session) }
            }
        }
    }

    fun setUrl(value: String) = _state.update { it.copy(url = value) }

    fun connect(url: String = _state.value.url) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isBlank() || _state.value.connecting) return
        _state.update { it.copy(url = clean, connecting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val info = container.serverSession.connect(clean)
                container.objectInfoCache.invalidate()
                container.serverRepository.saveLastUrl(clean)
                container.serverRepository.remember(clean)
                info
            }.onSuccess {
                _state.update { s -> s.copy(connecting = false, error = null) }
                _connectedEvents.tryEmit(Unit)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        connecting = false,
                        error = L10n.t("连接失败：", "Connection failed: ") + (e.message ?: "error")
                    )
                }
            }
        }
    }

    fun scanLan() {
        if (_state.value.scanning) return
        _state.update {
            it.copy(scanning = true, scanProgress = 0, scanFound = emptyList(), scanError = null)
        }
        viewModelScope.launch {
            container.serverDiscoveryRepository.discover().collect { event ->
                when (event) {
                    is DiscoveryEvent.Progress -> _state.update {
                        it.copy(scanning = true, scanProgress = event.scanned, scanTotal = event.total)
                    }
                    is DiscoveryEvent.Found -> _state.update {
                        val entry = event.server.asEntry()
                        it.copy(scanFound = (it.scanFound + entry).distinctBy(ServerEntry::url))
                    }
                    is DiscoveryEvent.Error -> _state.update {
                        it.copy(scanError = discoveryError(event.message))
                    }
                    DiscoveryEvent.Finished -> _state.update {
                        it.copy(
                            scanning = false,
                            scanError = it.scanError ?: if (it.scanFound.isEmpty()) {
                                L10n.t(
                                    "未发现 ComfyUI（确认手机与电脑在同一 Wi-Fi）",
                                    "No ComfyUI found - check that both devices are on the same Wi-Fi"
                                )
                            } else null
                        )
                    }
                }
            }
        }
    }

    fun renameServer(url: String, alias: String) {
        if (alias.isNotBlank()) container.serverRepository.rename(url, alias.trim())
    }

    fun removeServer(url: String) = container.serverRepository.remove(url)

    private fun discoveryError(code: String): String = when (code) {
        "not-on-wifi" -> L10n.t(
            "无法确定 Wi-Fi 网段（未连接或使用流量）",
            "Unknown subnet - not on Wi-Fi"
        )
        else -> code
    }
}
