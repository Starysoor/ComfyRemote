package com.comfyremote.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.data.repository.ServerSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AppUiState(
    val ready: Boolean = false,
    val session: ServerSession? = null,
)

/** App bootstrap only: restore the last server session, then hand control to navigation. */
class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.serverRepository.savedLastUrl()
            if (saved != null) {
                withTimeoutOrNull(4_000) {
                    runCatching {
                        container.serverSession.connect(saved)
                        container.objectInfoCache.invalidate()
                        container.serverRepository.remember(saved)
                    }
                }
            }
            _state.value = AppUiState(
                ready = true,
                session = container.serverSession.session.value,
            )
            launch {
                container.serverSession.session.collect { session ->
                    _state.value = _state.value.copy(session = session)
                }
            }
        }
    }
}
