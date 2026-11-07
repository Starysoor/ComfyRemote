package com.comfyremote.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.data.repository.ServerSession
import com.comfyremote.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val ready: Boolean = false,
    val session: ServerSession? = null,
)

/** App bootstrap only: restore the last server session, then hand control to navigation. */
class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        // The shell is usable immediately; ComfyUI auto-recovery runs in the background.
        _state.value = AppUiState(
            ready = true,
            session = container.serverSession.session.value,
        )
        viewModelScope.launch {
            container.serverSession.session.collect { session ->
                _state.update { it.copy(session = session) }
            }
        }
        viewModelScope.launch {
            val saved = container.serverRepository.savedLastUrl() ?: return@launch
            val restored = runCatching {
                container.serverSession.connect(saved, auto = true)
                // A manual switch may have happened during the auto-probe.
                if (container.serverSession.url != saved) false else {
                    container.objectInfoCache.invalidate()
                    container.serverRepository.remember(saved)
                    true
                }
            }.getOrDefault(false)
            if (restored && container.serverSession.url == saved) {
                container.navigationRequests.request(Routes.CREATE)
            }
        }
    }
}
