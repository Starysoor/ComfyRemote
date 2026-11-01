package com.comfyremote.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.comfyremote.app.feature.ai.AiSettingsViewModel
import com.comfyremote.app.feature.connect.ConnectViewModel
import com.comfyremote.app.feature.generate.GenerateViewModel
import com.comfyremote.app.feature.history.HistoryViewModel
import com.comfyremote.app.feature.tasks.TasksViewModel
import com.comfyremote.app.feature.update.UpdateViewModel
import com.comfyremote.app.feature.workflows.WorkflowsViewModel

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(container)
        modelClass.isAssignableFrom(ConnectViewModel::class.java) -> ConnectViewModel(container)
        modelClass.isAssignableFrom(WorkflowsViewModel::class.java) -> WorkflowsViewModel(container)
        modelClass.isAssignableFrom(GenerateViewModel::class.java) -> GenerateViewModel(container)
        modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(container)
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(container)
        modelClass.isAssignableFrom(AiSettingsViewModel::class.java) -> AiSettingsViewModel(container)
        modelClass.isAssignableFrom(UpdateViewModel::class.java) -> UpdateViewModel(container)
        else -> error("Unknown ViewModel ${modelClass.name}")
    } as T
}
