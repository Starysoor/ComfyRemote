package com.comfyremote.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.repository.DraftTransfer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TasksViewModel(private val container: AppContainer) : ViewModel() {
    val tasks: StateFlow<List<GenerationTask>> = container.generationManager.tasks

    fun task(id: String): GenerationTask? = tasks.value.firstOrNull { it.taskId == id }

    fun cancel(id: String) {
        viewModelScope.launch { container.generationManager.cancel(id) }
    }

    fun retry(id: String): String? = container.generationManager.retry(id)

    fun prepareReuse(id: String): DraftTransfer? {
        val task = task(id) ?: return null
        val transfer = DraftTransfer(
            workflowPath = task.request.workflowPath,
            workflowName = task.request.workflowName,
            values = task.request.fieldValues,
            randomSeed = task.request.randomSeed,
        )
        container.draftTransferRepository.put(transfer)
        return transfer
    }
}
