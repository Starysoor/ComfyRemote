package com.comfyremote.app.data.generation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.comfyremote.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

    /** Jobs can finish on different Dispatchers.Default workers. */
    private val jobs = ConcurrentHashMap<String, Job>()

    fun submit(request: GenerationRequest): String {
        val task = GenerationTask(
            taskId = request.id,
            request = request,
            clientId = java.util.UUID.randomUUID().toString(),
        )
        _tasks.update { listOf(task) + it }

        // The service observes activeTasks and owns the foreground notification.
        // Start it for every newly submitted task; Android coalesces repeated starts.
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, GenerationService::class.java),
        )

        jobs[task.taskId] = scope.launch { execute(task) }
        return task.taskId
    }

    suspend fun cancel(taskId: String) {
        val t = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (!t.isActive) return
        patch(taskId) { it.copy(phase = GenerationPhase.CANCELLING) }
        when (t.phase) {
            GenerationPhase.QUEUED ->
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

    private suspend fun execute(task: GenerationTask) {
        try {
            val final = runner.run(task) { updated ->
                patch(updated.taskId) { updated }
            }
            patch(final.taskId) { final }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                )
            }
            throw e
        } catch (e: Exception) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.FAILED,
                    error = GenerationError(
                        kind = ErrorKind.CONNECTION,
                        message = e.message ?: e.toString(),
                    ),
                    completedAt = System.currentTimeMillis(),
                )
            }
        } finally {
            jobs.remove(task.taskId)
        }
    }

    /** Atomic CAS update avoids one task's update overwriting another concurrent task. */
    private fun patch(taskId: String, f: (GenerationTask) -> GenerationTask) {
        _tasks.update { list -> list.map { if (it.taskId == taskId) f(it) else it } }
    }
}
