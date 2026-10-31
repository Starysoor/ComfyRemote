package com.comfyremote.app.data.generation

import android.content.Context
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.repository.HistoryRepository
import com.comfyremote.app.data.repository.ServerSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime core for generation jobs. Owns the task list and runs every job in
 * an app-scoped coroutine, so tasks outlive the UI that submitted them.
 */
class GenerationManager(
    private val container: AppContainer,
    private val appContext: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runner = GenerationRunner(
        session = container.serverSession,
        historyRepository = container.historyRepository,
        onInterruptRequested = { runCatching { container.serverSession.interrupt() } },
    )

    private val _tasks = MutableStateFlow<List<GenerationTask>>(emptyList())
    val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()

    val activeTasks: StateFlow<List<GenerationTask>> = _tasks
        .map { list -> list.filter { it.isActive } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val jobs = mutableMapOf<String, Job>()

    fun submit(request: com.comfyremote.app.data.generation.GenerationRequest): String {
        val task = com.comfyremote.app.data.generation.GenerationTask(
            taskId = request.id,
            request = request,
            clientId = java.util.UUID.randomUUID().toString(),
        )
        _tasks.value = listOf(task) + _tasks.value
        jobs[task.taskId] = scope.launch { execute(task) }
        return task.taskId
    }

    suspend fun cancel(taskId: String) {
        val t = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (!t.isActive) return
        patch(taskId) { it.copy(phase = com.comfyremote.app.data.generation.GenerationPhase.CANCELLING) }
        when (t.phase) {
            com.comfyremote.app.data.generation.GenerationPhase.QUEUED ->
                runCatching { container.serverSession.deleteFromQueue(t.promptId ?: return) }
            else -> runCatching { container.serverSession.interrupt() }
        }
    }

    fun retry(taskId: String): String? {
        val old = _tasks.value.firstOrNull { it.taskId == taskId } ?: return null
        val request = old.request.copy(id = java.util.UUID.randomUUID().toString())
        return submit(request)
    }

    fun observe(taskId: String) = _tasks
        .map { list -> list.firstOrNull { it.taskId == taskId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun execute(task: com.comfyremote.app.data.generation.GenerationTask) {
        val atomic = AtomicReference(task)
        try {
            val final = runner.run(task) { updated ->
                atomic.set(updated)
                patch(updated.taskId) { updated }
            }
            patch(final.taskId) { final }
            if (final.phase == com.comfyremote.app.data.generation.GenerationPhase.SUCCEEDED) {
                runner.persistToHistory(final)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId) {
                it.copy(
                    phase = com.comfyremote.app.data.generation.GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                )
            }
            throw e
        } catch (e: Exception) {
            patch(task.taskId) {
                it.copy(
                    phase = com.comfyremote.app.data.generation.GenerationPhase.FAILED,
                    error = com.comfyremote.app.data.generation.GenerationError(
                        kind = com.comfyremote.app.data.generation.ErrorKind.CONNECTION,
                        message = e.message ?: e.toString(),
                    ),
                    completedAt = System.currentTimeMillis(),
                )
            }
        } finally {
            jobs.remove(task.taskId)
        }
    }

    private fun patch(taskId: String, f: (com.comfyremote.app.data.generation.GenerationTask) -> com.comfyremote.app.data.generation.GenerationTask) {
        _tasks.value = _tasks.value.map { if (it.taskId == taskId) f(it) else it }
    }

    private fun sessionOrThrow(): ServerSessionRepository = container.serverSession
}
