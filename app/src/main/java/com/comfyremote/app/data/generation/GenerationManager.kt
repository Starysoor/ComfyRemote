package com.comfyremote.app.data.generation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.comfyremote.app.AppContainer
import com.comfyremote.app.ComfyApi
import com.comfyremote.app.PromptValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** App-scoped runtime owner for all generation jobs, including process-death recovery. */
class GenerationManager(
    private val container: AppContainer,
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val taskStore = container.generationTaskStore
    private val taskLock = Any()

    private val _tasks = MutableStateFlow(prune(normalizeLoaded(taskStore.load())))
    val tasks: StateFlow<List<GenerationTask>> = _tasks.asStateFlow()
    val activeTasks: StateFlow<List<GenerationTask>> = _tasks
        .map { list -> list.filter { it.isActive } }
        .stateIn(scope, SharingStarted.Eagerly, _tasks.value.filter { it.isActive })

    private val jobs = ConcurrentHashMap<String, Job>()

    init {
        // Persist normalization (notably CANCELLING -> CANCELLED) before any recovery work starts.
        taskStore.save(_tasks.value)
        val persistedActive = _tasks.value.filter { it.isActive }
        if (persistedActive.isNotEmpty()) startService()
        persistedActive.forEach(::launchRecovery)
    }

    fun submit(request: GenerationRequest): String {
        val task = GenerationTask(
            taskId = request.id,
            request = request,
            clientId = java.util.UUID.randomUUID().toString(),
        )
        mutateTasks { prune(listOf(task) + it) }
        startService()
        launchTracked(task.taskId) { execute(task) }
        return task.taskId
    }

    suspend fun cancel(taskId: String) {
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (!task.isActive || task.phase == GenerationPhase.CANCELLING) return
        patch(taskId) { it.copy(phase = GenerationPhase.CANCELLING, recoveryNote = null) }
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

    /** Re-attach a previously failed recovery without submitting a duplicate prompt. */
    fun recoverExisting(taskId: String): Boolean {
        val current = _tasks.value.firstOrNull { it.taskId == taskId } ?: return false
        if (current.promptId.isNullOrBlank() || current.isActive) return false
        val recovering = current.copy(
            phase = GenerationPhase.QUEUED,
            error = null,
            completedAt = null,
            recovered = true,
            recoveryNote = "Retrying recovery",
            preview = null,
            queuePosition = null,
        )
        patch(taskId, allowTerminalExit = true) { recovering }
        startService()
        launchRecovery(recovering)
        return true
    }

    fun observe(taskId: String) = _tasks
        .map { list -> list.firstOrNull { it.taskId == taskId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun execute(task: GenerationTask) {
        val runner = runnerFor(task)
        try {
            val final = runner.run(task) { updated -> patch(updated.taskId) { updated } }
            patch(final.taskId) { final }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                    queuePosition = null,
                )
            }
            throw e
        } catch (e: Exception) {
            patchFailure(task.taskId, e)
        }
    }

    private fun launchRecovery(task: GenerationTask) {
        if (jobs.containsKey(task.taskId)) return
        launchTracked(task.taskId) { recoverExecute(task) }
    }

    private suspend fun recoverExecute(original: GenerationTask) {
        val task = original.copy(
            phase = if (original.phase == GenerationPhase.RUNNING) GenerationPhase.RUNNING else GenerationPhase.QUEUED,
            preview = null,
            recovered = true,
            error = null,
            completedAt = null,
            recoveryNote = original.recoveryNote ?: "Recovering after restart",
        )
        patch(task.taskId, allowTerminalExit = true) { task }
        val runner = runnerFor(task)
        try {
            val final = runner.recover(task) { updated -> patch(updated.taskId, allowTerminalExit = true) { updated } }
            patch(final.taskId, allowTerminalExit = true) { final }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId, allowTerminalExit = true) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                    queuePosition = null,
                )
            }
            throw e
        } catch (e: Exception) {
            patch(task.taskId, allowTerminalExit = true) {
                it.copy(
                    phase = GenerationPhase.FAILED,
                    error = GenerationError(ErrorKind.CONNECTION, "Recovery failed: ${e.message ?: e}"),
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                    queuePosition = null,
                    recoveryNote = "Reconnect failed",
                )
            }
        }
    }

    private fun runnerFor(task: GenerationTask) = GenerationRunner(
        backend = ComfyGenerationBackend(task.request.serverUrl),
        history = RepositoryGenerationHistorySink(container.historyRepository, task.request.serverUrl),
    )

    private fun patchFailure(taskId: String, e: Exception) {
        patch(taskId) {
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
                queuePosition = null,
            )
        }
    }

    private fun launchTracked(taskId: String, block: suspend () -> Unit) {
        if (jobs.containsKey(taskId)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
        job.start()
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, GenerationService::class.java))
        }
    }

    private fun patch(
        taskId: String,
        allowTerminalExit: Boolean = false,
        transform: (GenerationTask) -> GenerationTask,
    ) {
        mutateTasks { list ->
            prune(list.map { current ->
                if (current.taskId != taskId) current
                else {
                    val next = transform(current)
                    if (current.isTerminal && !next.isTerminal && !allowTerminalExit) current else next
                }
            })
        }
    }

    private fun mutateTasks(transform: (List<GenerationTask>) -> List<GenerationTask>) {
        synchronized(taskLock) {
            val old = _tasks.value
            val next = transform(old)
            _tasks.value = next
            // Preview/progress can update many times per second; only durable recovery state hits disk.
            if (durable(old) != durable(next)) taskStore.save(next)
        }
    }

    private fun durable(list: List<GenerationTask>): List<GenerationTask> = list.map {
        it.copy(
            preview = null,
            progress = null,
            queueRemaining = null,
            nodeId = null,
            nodeTitle = null,
        )
    }

    private fun normalizeLoaded(list: List<GenerationTask>): List<GenerationTask> = list.map { task ->
        if (task.phase == GenerationPhase.CANCELLING) {
            task.copy(
                phase = GenerationPhase.CANCELLED,
                completedAt = task.completedAt ?: System.currentTimeMillis(),
                preview = null,
                recoveryNote = "Cancellation persisted across restart",
            )
        } else task.copy(preview = null)
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
