package com.comfyremote.app.data.generation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.comfyremote.app.AppContainer
import com.comfyremote.app.ComfyApi
import com.comfyremote.app.PromptValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** App-scoped runtime owner for all generation jobs. */
class GenerationManager(
    private val container: AppContainer,
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val historySink = RepositoryGenerationHistorySink(container.historyRepository)

    private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())
    val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()
    val activeTasks: StateFlow<List<GenerationTask>> = _tasks
        .map { list -> list.filter { it.isActive } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val jobs = ConcurrentHashMap<String, Job>()

    fun submit(request: GenerationRequest): String {
        val task = GenerationTask(
            taskId = request.id,
            request = request,
            clientId = java.util.UUID.randomUUID().toString(),
        )
        _tasks.update { prune(listOf(task) + it) }
        ContextCompat.startForegroundService(appContext, Intent(appContext, GenerationService::class.java))
        jobs[task.taskId] = scope.launch { execute(task) }
        return task.taskId
    }

    suspend fun cancel(taskId: String) {
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (!task.isActive || task.phase == GenerationPhase.CANCELLING) return
        patch(taskId) { it.copy(phase = GenerationPhase.CANCELLING) }
        when (task.phase) {
            GenerationPhase.PREPARING, GenerationPhase.QUEUEING -> jobs[taskId]?.cancel()
            GenerationPhase.QUEUED -> {
                task.promptId?.let { promptId ->
                    runCatching { ComfyApi(task.request.serverUrl).deleteFromQueue(promptId) }
                }
                jobs[taskId]?.cancel()
            }
            GenerationPhase.RUNNING -> {
                // ComfyUI interrupt is server-scoped. Always target this task's original server.
                runCatching { ComfyApi(task.request.serverUrl).interrupt() }
            }
            else -> Unit
        }
    }

    fun retry(taskId: String): String? {
        val old = _tasks.value.firstOrNull { it.taskId == taskId } ?: return null
        return submit(old.request.copy(id = java.util.UUID.randomUUID().toString()))
    }

    fun observe(taskId: String) = _tasks
        .map { list -> list.firstOrNull { it.taskId == taskId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun execute(task: GenerationTask) {
        val runner = GenerationRunner(
            backend = ComfyGenerationBackend(task.request.serverUrl),
            history = historySink,
        )
        try {
            val final = runner.run(task) { updated -> patch(updated.taskId) { updated } }
            patch(final.taskId) { final }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                )
            }
            throw e
        } catch (e: Exception) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.FAILED,
                    error = GenerationError(
                        kind = when (e) {
                            is PromptValidationException -> ErrorKind.PROMPT_VALIDATION
                            is GenerationTimeoutException -> ErrorKind.TIMEOUT
                            else -> ErrorKind.CONNECTION
                        },
                        message = e.message ?: e.toString(),
                    ),
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                )
            }
        } finally {
            jobs.remove(task.taskId)
        }
    }

    private fun patch(taskId: String, transform: (GenerationTask) -> GenerationTask) {
        _tasks.update { list ->
            prune(list.map { current ->
                if (current.taskId != taskId) current
                else {
                    val next = transform(current)
                    if (current.isTerminal && !next.isTerminal) current else next
                }
            })
        }
    }

    private fun prune(list: List<GenerationTask>): List<GenerationTask> {
        val active = list.filter { it.isActive }
        val terminal = list.filterNot { it.isActive }
            .sortedByDescending { it.completedAt ?: it.createdAt }
            .take(MAX_TERMINAL_TASKS)
        return (active + terminal).distinctBy { it.taskId }.sortedByDescending { it.createdAt }
    }

    companion object { private const val MAX_TERMINAL_TASKS = 50 }
}
