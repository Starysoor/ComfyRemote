package com.comfyremote.app.feature.tasks

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.repository.DraftTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TasksViewModel(private val container: AppContainer) : ViewModel() {
    val tasks: StateFlow<List<GenerationTask>> = container.generationManager.tasks
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
            values = if (task.seedValues.isEmpty()) task.request.fieldValues else task.request.fieldValues + task.seedValues,
            randomSeed = task.request.randomSeed,
        )
        container.draftTransferRepository.put(transfer)
        return transfer
    }

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.galleryExporter.savePng(bitmap) }
                .onSuccess { _message.value = container.galleryExporter.savedMessage() }
                .onFailure { _message.value = container.galleryExporter.failMessage(it) }
        }
    }

    fun clearMessage() { _message.value = null }
}
