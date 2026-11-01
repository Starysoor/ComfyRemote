package com.comfyremote.app.feature.tasks

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.ui.L10n
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
    fun duplicate(id: String): String? = container.generationManager.retry(id)

    fun recover(id: String): Boolean {
        val ok = container.generationManager.recoverExisting(id)
        _message.value = if (ok) L10n.t("正在重新连接原任务", "Reconnecting to original task")
        else L10n.t("这个任务无法恢复", "This task cannot be recovered")
        return ok
    }

    fun serverLabel(url: String): String = container.serverRepository.load()
        .firstOrNull { it.url == url }
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: url

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

    fun diagnostics(id: String): String {
        val task = task(id) ?: return ""
        return buildString {
            appendLine("Workflow: ${task.request.workflowName}")
            appendLine("Server: ${serverLabel(task.request.serverUrl)}")
            appendLine("Task: ${task.taskId}")
            task.promptId?.let { appendLine("Prompt: $it") }
            appendLine("Phase: ${task.phase}")
            task.queuePosition?.let { appendLine("Queue position: $it") }
            task.nodeId?.let { appendLine("Node: #$it ${task.nodeTitle.orEmpty()}") }
            if (task.seedInfo.isNotBlank()) appendLine("Seed: ${task.seedInfo}")
            if (task.recovered) appendLine("Recovered: ${task.recoveryNote.orEmpty()}")
            task.error?.let { error ->
                appendLine("Error kind: ${error.kind}")
                error.nodeType?.let { appendLine("Node type: $it") }
                appendLine("Message: ${error.message}")
            }
        }.trim()
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
