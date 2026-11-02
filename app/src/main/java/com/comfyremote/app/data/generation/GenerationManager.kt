package com.comfyremote.app.data.generation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.comfyremote.app.AppContainer
import com.comfyremote.app.ComfyApi
import com.comfyremote.app.PromptValidationException
import com.comfyremote.app.data.cloud.AutoDlConfig
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
import java.util.UUID
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

    private val _localQueuePaused = MutableStateFlow(false)
    val localQueuePaused: StateFlow<Boolean> = _localQueuePaused.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    init {
        // Persist normalization before any recovery/local-queue work starts.
        taskStore.save(_tasks.value)
        val persistedActive = _tasks.value.filter { it.isActive }
        if (persistedActive.isNotEmpty()) startService()
        persistedActive
            .filter { !it.localPending && !it.promptId.isNullOrBlank() }
            .forEach(::launchRecovery)
        pumpLocalQueue()
    }

    /** Submits one request immediately to its bound server. */
    fun submit(request: GenerationRequest): String {
        val task = newTask(request)
        if (request.provider == AutoDlConfig.PROVIDER) {
            container.cloudSpendStore.record(request.workflowName, request.estimatedCloudCost)
        }
        mutateTasks { prune(listOf(task) + it) }
        startService()
        launchTracked(task.taskId) { execute(task) }
        return task.taskId
    }

    /**
     * Adds a batch. When [localQueue] is true, each server receives at most one app-managed
     * batch job at a time; the remaining requests stay durably queued on-device.
     */
    fun submitBatch(requests: List<GenerationRequest>, localQueue: Boolean = true): List<String> {
        if (requests.isEmpty()) return emptyList()
        val batchId = UUID.randomUUID().toString()
        val tasks = requests.mapIndexed { index, request ->
            newTask(request).copy(
                localPending = localQueue,
                batchId = batchId,
                batchIndex = index + 1,
                batchSize = requests.size,
                recoveryNote = if (localQueue) "Waiting in local queue" else null,
            )
        }
        requests.forEach { request ->
            if (request.provider == AutoDlConfig.PROVIDER) {
                container.cloudSpendStore.record(request.workflowName, request.estimatedCloudCost)
            }
        }
        mutateTasks { prune(tasks + it) }
        startService()
        if (localQueue) {
            pumpLocalQueue()
        } else {
            tasks.forEach { task -> launchTracked(task.taskId) { execute(task) } }
        }
        return tasks.map { it.taskId }
    }

    fun pauseLocalQueue() {
        _localQueuePaused.value = true
    }

    fun resumeLocalQueue() {
        _localQueuePaused.value = false
        pumpLocalQueue()
    }

    fun toggleLocalQueuePaused() {
        if (_localQueuePaused.value) resumeLocalQueue() else pauseLocalQueue()
    }

    suspend fun cancel(taskId: String) {
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (!task.isActive || task.phase == GenerationPhase.CANCELLING) return

        if (task.localPending) {
            patch(taskId) {
                it.copy(
                    localPending = false,
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    recoveryNote = "Removed from local queue",
                    queuePosition = null,
                    progress = null,
                    preview = null,
                )
            }
            pumpLocalQueue()
            return
        }

        patch(taskId) { it.copy(phase = GenerationPhase.CANCELLING, recoveryNote = null) }
        val cloud = task.request.provider == AutoDlConfig.PROVIDER
        when (task.phase) {
            GenerationPhase.PREPARING, GenerationPhase.QUEUEING -> jobs[taskId]?.cancel()
            GenerationPhase.QUEUED -> {
                if (!cloud) {
                    task.promptId?.let { promptId ->
                        runCatching { ComfyApi(task.request.serverUrl).deleteFromQueue(promptId) }
                    }
                }
                jobs[taskId]?.cancel()
            }
            GenerationPhase.RUNNING -> {
                if (cloud) {
                    // AutoDL's hosted API has no public per-task cancel endpoint.
                    // Stop tracking locally; the remote job may still finish/bill.
                    jobs[taskId]?.cancel()
                } else {
                    // ComfyUI interrupt is server-scoped. Always target this task's original server.
                    runCatching { ComfyApi(task.request.serverUrl).interrupt() }
                }
            }
            else -> Unit
        }
    }

    suspend fun cancelMany(taskIds: Collection<String>) {
        taskIds.distinct().forEach { cancel(it) }
    }

    suspend fun cancelQueued(serverUrl: String? = null) {
        val ids = _tasks.value
            .filter { task ->
                task.isActive &&
                    (serverUrl == null || task.request.serverUrl == serverUrl) &&
                    (task.localPending || task.phase == GenerationPhase.PREPARING ||
                        task.phase == GenerationPhase.QUEUEING || task.phase == GenerationPhase.QUEUED)
            }
            .map { it.taskId }
        cancelMany(ids)
    }

    /** Removes a finished task from the list (user-visible delete). */
    fun deleteTask(taskId: String) {
        val task = _tasks.value.firstOrNull { it.taskId == taskId } ?: return
        if (task.isActive) return
        jobs.remove(taskId)?.cancel()
        mutateTasks { list -> list.filter { it.taskId != taskId } }
    }

    /** Removes every non-active task (convenience "clear finished"). */
    fun deleteFinished() {
        val activeIds = _tasks.value.filter { it.isActive }.map { it.taskId }.toSet()
        mutateTasks { list -> list.filter { it.isActive } }
        jobs.keys.toList().forEach { id -> if (id !in activeIds) jobs.remove(id)?.cancel() }
    }

    fun retry(taskId: String): String? {
        val old = _tasks.value.firstOrNull { it.taskId == taskId } ?: return null
        return submit(old.request.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()))
    }

    /** Retries all failed/cancelled tasks through the durable local queue to avoid flooding a server. */
    fun retryFailed(serverUrl: String? = null): List<String> {
        val requests = _tasks.value
            .filter {
                (it.phase == GenerationPhase.FAILED || it.phase == GenerationPhase.CANCELLED) &&
                    (serverUrl == null || it.request.serverUrl == serverUrl)
            }
            .sortedBy { it.createdAt }
            .map { it.request.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()) }
        return submitBatch(requests, localQueue = true)
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
            localPending = false,
        )
        patch(taskId, allowTerminalExit = true) { recovering }
        startService()
        launchRecovery(recovering)
        return true
    }

    fun observe(taskId: String) = _tasks
        .map { list -> list.firstOrNull { it.taskId == taskId } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun newTask(request: GenerationRequest) = GenerationTask(
        taskId = request.id,
        request = request,
        clientId = UUID.randomUUID().toString(),
    )

    private suspend fun execute(task: GenerationTask) {
        val runner = runnerFor(task)
        try {
            val final = runner.run(task.copy(localPending = false, recoveryNote = null)) { updated ->
                patch(updated.taskId) { updated.copy(localPending = false) }
            }
            patch(final.taskId) { final.copy(localPending = false) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                    queuePosition = null,
                    localPending = false,
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
            localPending = false,
        )
        patch(task.taskId, allowTerminalExit = true) { task }
        val runner = runnerFor(task)
        try {
            val final = runner.recover(task) { updated -> patch(updated.taskId, allowTerminalExit = true) { updated.copy(localPending = false) } }
            patch(final.taskId, allowTerminalExit = true) { final.copy(localPending = false) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            patch(task.taskId, allowTerminalExit = true) {
                it.copy(
                    phase = GenerationPhase.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                    preview = null,
                    queuePosition = null,
                    localPending = false,
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
                    localPending = false,
                )
            }
        }
    }

    private fun runnerFor(task: GenerationTask): GenerationRunner {
        val request = task.request
        val history = RepositoryGenerationHistorySink(container.historyRepository, request.serverUrl)
        val resolver: (String) -> String? = { container.cloudInputStore.toRequestValue(it) }
        return if (request.provider != "comfyui") {
            val provider = container.cloudProviders.byId(request.provider)
                ?: error("Unknown cloud provider ${request.provider}")
            val workflowId = request.workflowPath.removePrefix(provider.pathPrefix)
            GenerationRunner(
                backend = CloudWorkflowBackend(provider, workflowId),
                history = history,
                timeoutMs = 60 * 60_000L,
                resolveCloudImage = resolver,
            )
        } else {
            GenerationRunner(
                backend = ComfyGenerationBackend(request.serverUrl),
                history = history,
                resolveCloudImage = resolver,
            )
        }
    }

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
                localPending = false,
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
                pumpLocalQueue()
            }
        }
        jobs[taskId] = job
        job.start()
    }

    /** Starts the oldest local-pending task for each idle server. */
    private fun pumpLocalQueue() {
        if (_localQueuePaused.value) return
        scope.launch {
            val snapshot = _tasks.value
            val remoteBusy = snapshot
                .filter { it.isActive && !it.localPending }
                .mapTo(hashSetOf()) { it.request.serverUrl }
            val candidates = snapshot
                .filter { it.localPending && it.isActive }
                .sortedBy { it.createdAt }
                .groupBy { it.request.serverUrl }
                .mapNotNull { (server, list) -> if (server in remoteBusy) null else list.firstOrNull() }

            candidates.forEach { candidate ->
                var launch: GenerationTask? = null
                synchronized(taskLock) {
                    val current = _tasks.value.firstOrNull { it.taskId == candidate.taskId }
                    val serverBusyNow = _tasks.value.any {
                        it.taskId != candidate.taskId && it.request.serverUrl == candidate.request.serverUrl &&
                            it.isActive && !it.localPending
                    }
                    if (current?.localPending == true && current.isActive && !serverBusyNow && !_localQueuePaused.value) {
                        val updated = current.copy(localPending = false, recoveryNote = null)
                        val next = prune(_tasks.value.map { if (it.taskId == current.taskId) updated else it })
                        _tasks.value = next
                        taskStore.save(next)
                        launch = updated
                    }
                }
                launch?.let { task -> launchTracked(task.taskId) { execute(task) } }
            }
        }
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
        when {
            task.localPending -> task.copy(
                phase = GenerationPhase.PREPARING,
                promptId = null,
                preview = null,
                queuePosition = null,
                progress = null,
                error = null,
                completedAt = null,
                recoveryNote = "Waiting in local queue",
            )
            task.phase == GenerationPhase.CANCELLING -> task.copy(
                phase = GenerationPhase.CANCELLED,
                completedAt = task.completedAt ?: System.currentTimeMillis(),
                preview = null,
                recoveryNote = "Cancellation persisted across restart",
            )
            task.isActive && task.promptId.isNullOrBlank() -> task.copy(
                phase = GenerationPhase.FAILED,
                error = GenerationError(ErrorKind.CONNECTION, "App restarted before the server returned a prompt id"),
                completedAt = System.currentTimeMillis(),
                preview = null,
                recoveryNote = "Submission interrupted before recovery became possible",
            )
            else -> task.copy(preview = null)
        }
    }

    private fun prune(list: List<GenerationTask>): List<GenerationTask> {
        val active = list.filter { it.isActive }
        val terminal = list.filterNot { it.isActive }
            .sortedByDescending { it.completedAt ?: it.createdAt }
            .take(MAX_TERMINAL_TASKS)
        return (active + terminal).distinctBy { it.taskId }.sortedByDescending { it.createdAt }
    }

    companion object { private const val MAX_TERMINAL_TASKS = 80 }
}
