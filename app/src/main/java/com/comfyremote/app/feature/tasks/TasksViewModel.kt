package com.comfyremote.app.feature.tasks

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, ACTIVE, QUEUED, FAILED, DONE }

data class TasksUiState(
    val tasks: List<GenerationTask> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val serverUrl: String? = null,
    val localQueuePaused: Boolean = false,
) {
    val visible: List<GenerationTask>
        get() = tasks.asSequence()
            .filter { task ->
                when (filter) {
                    TaskFilter.ALL -> true
                    TaskFilter.ACTIVE -> task.isActive
                    TaskFilter.QUEUED -> task.localPending || task.phase == GenerationPhase.QUEUEING || task.phase == GenerationPhase.QUEUED
                    TaskFilter.FAILED -> task.phase == GenerationPhase.FAILED
                    TaskFilter.DONE -> task.isTerminal
                }
            }
            .filter { serverUrl == null || it.request.serverUrl == serverUrl }
            .sortedWith(compareByDescending<GenerationTask> { it.isActive }.thenByDescending { it.createdAt })
            .toList()

    val serverUrls: List<String>
        get() = tasks.map { it.request.serverUrl }.filter(String::isNotBlank).distinct()
}

class TasksViewModel(private val container: AppContainer) : ViewModel() {
    val tasks: StateFlow<List<GenerationTask>> = container.generationManager.tasks
    private val _filter = MutableStateFlow(TaskFilter.ALL)
    private val _serverUrl = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TasksUiState> = combine(
        tasks,
        _filter,
        _serverUrl,
        container.generationManager.localQueuePaused,
    ) { list, filter, serverUrl, paused ->
        TasksUiState(list, filter, serverUrl, paused)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TasksUiState(tasks.value, _filter.value, _serverUrl.value, container.generationManager.localQueuePaused.value),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun task(id: String): GenerationTask? = tasks.value.firstOrNull { it.taskId == id }

    fun setFilter(filter: TaskFilter) { _filter.value = filter }
    fun setServerFilter(url: String?) { _serverUrl.value = url }
    fun toggleLocalQueuePaused() = container.generationManager.toggleLocalQueuePaused()

    fun delete(id: String) { container.generationManager.deleteTask(id) }
    fun deleteFinished() { container.generationManager.deleteFinished() }
    fun cancel(id: String) { viewModelScope.launch { container.generationManager.cancel(id) } }

    fun cancelQueued() {
        viewModelScope.launch {
            container.generationManager.cancelQueued(_serverUrl.value)
            _message.value = L10n.t("已取消当前范围内的排队任务", "Queued tasks cancelled")
        }
    }

    fun retryFailed() {
        val ids = container.generationManager.retryFailed(_serverUrl.value)
        _message.value = if (ids.isEmpty()) L10n.t("没有可重试的失败任务", "No failed tasks to retry")
        else L10n.t("已加入 ${ids.size} 个重试任务", "Queued ${ids.size} retries")
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
        val hasActualSeed = task.seedValues.isNotEmpty()
        val transfer = DraftTransfer(
            workflowPath = task.request.workflowPath,
            workflowName = task.request.workflowName,
            values = if (hasActualSeed) task.request.fieldValues + task.seedValues else task.request.fieldValues,
            randomSeed = if (hasActualSeed) false else task.request.randomSeed,
            serverUrl = task.request.serverUrl,
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
            task.batchId?.let { appendLine("Batch: $it ${task.batchIndex ?: "?"}/${task.batchSize ?: "?"}") }
            task.promptId?.let { appendLine("Prompt: $it") }
            appendLine("Phase: ${task.phase}")
            appendLine("Local pending: ${task.localPending}")
            task.queuePosition?.let { appendLine("Queue position: $it") }
            task.nodeId?.let { appendLine("Node: #$it ${task.nodeTitle.orEmpty()}") }
            if (task.seedInfo.isNotBlank()) appendLine("Seed: ${task.seedInfo}")
            appendLine("Created: ${task.createdAt}")
            task.startedAt?.let { appendLine("Started: $it") }
            task.completedAt?.let { appendLine("Completed: $it") }
            appendLine("Elapsed ms: ${task.elapsedMs()}")
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

    fun saveMedia(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.galleryExporter.saveMediaFile(path) }
                .onSuccess { _message.value = container.galleryExporter.savedMessage() }
                .onFailure { _message.value = container.galleryExporter.failMessage(it) }
        }
    }

    fun clearMessage() { _message.value = null }
}
