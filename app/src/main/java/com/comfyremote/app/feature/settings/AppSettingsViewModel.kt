package com.comfyremote.app.feature.settings

import androidx.lifecycle.ViewModel
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.repository.AppSettings
import kotlinx.coroutines.flow.StateFlow

class AppSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.appSettingsStore.settings

    fun update(transform: (AppSettings) -> AppSettings) = container.appSettingsStore.update(transform)

    fun clearCloudInputs() = container.cloudInputStore.clearAll()
}
