package com.comfyremote.app.feature.storyboard

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.data.storyboard.StoryboardProject
import com.comfyremote.app.data.storyboard.StoryboardShot
import com.comfyremote.app.feature.generate.isVideoPath
import com.comfyremote.app.R
import com.comfyremote.app.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StoryboardWorkflowOption(
    val path: String,
    val workflowId: String,
    val name: String,
    val providerId: String,
    val providerLabel: String,
    val kind: String,
)

class StoryboardViewModel(private val container: AppContainer) : ViewModel() {
    val projects: StateFlow<List<StoryboardProject>> = container.storyboardStore.projects
    private val _workflows = MutableStateFlow<List<StoryboardWorkflowOption>>(emptyList())
    val workflows: StateFlow<List<StoryboardWorkflowOption>> = _workflows.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val syncing = mutableSetOf<String>()

    /** Providers whose catalogue loaded at least once; lets us flag removed workflows. */
    private val catalogReadyProviders = mutableSetOf<String>()

    init {
        refreshWorkflows()
        viewModelScope.launch {
            container.generationManager.tasks.collect { tasks -> syncTasks(tasks) }
        }
    }

    fun refreshWorkflows() {
        viewModelScope.launch {
            catalogReadyProviders.clear()
            val (options, errors, ready) = withContext(Dispatchers.IO) {
                val options = mutableListOf<StoryboardWorkflowOption>()
                val errors = mutableListOf<String>()
                val ready = mutableSetOf<String>()
                container.cloudProviders.all.forEach { provider ->
                    // An unconfigured provider cannot contribute usable shot models.
                    if (!provider.configured()) return@forEach
                    runCatching { provider.catalog(force = false) }
                        .onSuccess { list ->
                            ready += provider.id
                            options += list.filter { it.kind == "video" }.map { summary ->
                                StoryboardWorkflowOption(
                                    path = provider.pathPrefix + summary.id,
                                    workflowId = summary.id,
                                    name = summary.name,
                                    providerId = provider.id,
                                    providerLabel = provider.label,
                                    kind = summary.kind,
                                )
                            }
                        }
                        .onFailure { error ->
                            errors += "${provider.label}: ${error.message ?: "error"}"
                        }
                }
                Triple(options, errors, ready)
            }
            catalogReadyProviders += ready
            _workflows.value = options.distinctBy { it.path }.sortedWith(
                compareBy<StoryboardWorkflowOption> { it.providerLabel.lowercase() }
                    .thenBy { it.name.lowercase() }
            )
            if (options.isEmpty() && errors.isNotEmpty()) _message.value = errors.first()
        }
    }

    fun createProject(title: String, workflowPath: String, stylePrompt: String): StoryboardProject? {
        val option = _workflows.value.firstOrNull { it.path == workflowPath }
        if (option == null) {
            _message.value = AppStrings.get(R.string.board_err_choose_workflow)
            return null
        }
        return container.storyboardStore.create(
            title = title,
            workflowId = option.workflowId,
            stylePrompt = stylePrompt,
            workflowPath = option.path,
            providerId = option.providerId,
        )
    }

    fun deleteProject(id: String): Boolean {
        val project = container.storyboardStore.project(id) ?: return false
        if (project.shots.any(::shotRunning)) {
            _message.value = AppStrings.get(R.string.board_err_delete_running_project)
            return false
        }
        container.storyboardStore.delete(id)
        return true
    }

    fun setStyle(projectId: String, style: String) = container.storyboardStore.setStyle(projectId, style)

    fun setAspectRatio(projectId: String, ratio: String) =
        container.storyboardStore.setAspectRatio(projectId, ratio)

    fun updateProjectSettings(
        projectId: String,
        title: String,
        workflowPath: String,
        aspectRatio: String,
        defaultValues: Map<String, String>,
    ) {
        val providerId = container.cloudProviders.forPath(workflowPath)?.id.orEmpty()
        container.storyboardStore.updateSettings(
            projectId, title, workflowPath, providerId, aspectRatio, defaultValues,
        )
    }

    fun addShot(projectId: String, prompt: String) {
        if (prompt.isBlank()) {
            _message.value = AppStrings.get(R.string.board_err_empty_prompt)
            return
        }
        container.storyboardStore.addShot(projectId, prompt)
    }

    fun deleteShot(projectId: String, shotId: String) {
        val shot = container.storyboardStore.project(projectId)
            ?.shots?.firstOrNull { it.id == shotId } ?: return
        if (shotRunning(shot)) {
            _message.value = AppStrings.get(R.string.board_err_delete_running_shot)
            return
        }
        container.storyboardStore.deleteShot(projectId, shotId)
    }

    private fun shotRunning(shot: StoryboardShot): Boolean =
        shot.status == "running" || shot.taskId?.let { taskId ->
            container.generationManager.tasks.value.firstOrNull { it.taskId == taskId }?.isActive
        } == true

    fun updateShot(projectId: String, shotId: String, prompt: String) =
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(prompt = prompt.trim())
        }

    fun moveShot(projectId: String, shotId: String, delta: Int) =
        container.storyboardStore.moveShot(projectId, shotId, delta)

    fun duplicateShot(projectId: String, shotId: String) =
        container.storyboardStore.duplicateShot(projectId, shotId)

    fun insertShotAfter(projectId: String, shotId: String, prompt: String) =
        container.storyboardStore.insertShotAfter(projectId, shotId, prompt)

    fun assignAssetToShot(projectId: String, shotId: String, assetId: String) =
        assignAssetsToShot(projectId, shotId, listOf(assetId))

    fun assignAssetsToShot(projectId: String, shotId: String, assetIds: List<String>) {
        val project = container.storyboardStore.project(projectId) ?: return
        val assets = assetIds.mapNotNull { id -> project.assets.firstOrNull { it.id == id } }
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(
                referenceAssetIds = assetIds,
                referencePath = assets.firstOrNull()?.path,
                referenceManual = true,
                referenceRole = assets.firstOrNull()?.role ?: ReferenceRole.AUTO,
            )
        }
    }

    fun clearShotReference(projectId: String, shotId: String) =
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(
                referencePath = null,
                referenceAssetIds = emptyList(),
                referenceManual = true,
                referenceRole = ReferenceRole.AUTO,
            )
        }

    /**
     * Sets a provider workflow for one shot. A null path restores the project default.
     * Cached parameter values belong to the previous workflow, so they are dropped.
     */
    fun setShotWorkflow(projectId: String, shotId: String, workflowPath: String?) {
        val shot = shot(projectId, shotId) ?: return
        if (shot.workflowPath == workflowPath?.takeIf { it.isNotBlank() }) return
        if (workflowPath.isNullOrBlank()) {
            container.storyboardStore.updateShot(projectId, shotId) {
                it.copy(workflowId = null, workflowPath = null, providerId = null, values = emptyMap())
            }
            return
        }
        val option = _workflows.value.firstOrNull { it.path == workflowPath }
        if (option == null) {
            _message.value = AppStrings.get(R.string.board_err_choose_workflow)
            return
        }
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(
                workflowId = option.workflowId,
                workflowPath = option.path,
                providerId = option.providerId,
                values = emptyMap(),
            )
        }
    }

    /** Copies a picked image into project-owned storage so it survives gallery changes. */
    fun addAsset(projectId: String, role: ReferenceRole, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.storyboardAssetStore.importUri(uri, role) }
                .onSuccess { asset -> container.storyboardStore.addAsset(projectId, asset) }
                .onFailure { e ->
                    _message.value = AppStrings.get(R.string.board_err_add_asset, e.message ?: "error")
                }
        }
    }

    fun removeAsset(projectId: String, assetId: String) {
        container.storyboardStore.removeAsset(projectId, assetId)
    }

    fun project(projectId: String): StoryboardProject? =
        projects.value.firstOrNull { it.id == projectId }

    fun shot(projectId: String, shotId: String): StoryboardShot? =
        project(projectId)?.shots?.firstOrNull { it.id == shotId }

    /**
     * Prepares a cloud-video draft for one shot. The previous shot's last frame
     * is used as this shot's reference so the sequence stays visually coherent.
     */
    fun openShot(projectId: String, shotId: String): Pair<String, String>? {
        val project = project(projectId) ?: return null
        val index = project.shots.indexOfFirst { it.id == shotId }
        if (index < 0) return null
        val shot = project.shots[index]
        if (shotRunning(shot)) return null
        val previous = project.shots.getOrNull(index - 1)
        val assetReferences = shot.referenceAssetIds.mapNotNull { id ->
            project.assets.firstOrNull { it.id == id }?.path
        }
        val reference = if (shot.referenceManual) shot.referencePath
        else shot.referencePath ?: previous?.lastFramePath
        val prompt = listOf(project.stylePrompt, shot.prompt)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val name = "${project.title} · ${index + 1}"
        val workflowPath = shot.workflowPath?.takeIf { it.isNotBlank() }
            ?: project.workflowPath.takeIf { it.isNotBlank() }
            ?: AutoDlConfig.PATH_PREFIX + (shot.workflowId ?: project.workflowId)
        val provider = container.cloudProviders.forPath(workflowPath)
        if (provider == null) {
            _message.value = AppStrings.get(R.string.board_err_unknown_provider, workflowPath)
            return null
        }
        if (!provider.configured()) {
            _message.value = AppStrings.get(R.string.board_err_provider_not_configured, provider.label)
            return null
        }
        if (provider.id in catalogReadyProviders && _workflows.value.none { it.path == workflowPath }) {
            _message.value = AppStrings.get(R.string.board_err_workflow_missing, workflowPath)
            return null
        }
        val values = project.defaultValues + shot.values +
            (if (prompt.isBlank()) emptyMap() else mapOf("prompt" to prompt))
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(status = if (it.status == "running") it.status else "draft", error = null)
        }
        container.draftTransferRepository.put(
            DraftTransfer(
                workflowPath = workflowPath,
                workflowName = name,
                values = values,
                randomSeed = true,
                serverUrl = null,
                referenceLocalPath = if (assetReferences.isEmpty()) reference else null,
                referenceLocalPaths = assetReferences,
                storyboardProjectId = project.id,
                storyboardShotId = shot.id,
            )
        )
        return workflowPath to name
    }

    fun nextShot(projectId: String): StoryboardShot? = container.storyboardStore.nextShot(projectId)

    fun setReference(projectId: String, shotId: String, path: String?) =
        container.storyboardStore.setReference(projectId, shotId, path)

    fun clearMessage() { _message.value = null }

    private fun syncTasks(tasks: List<GenerationTask>) {
        val byId = tasks.associateBy { it.taskId }
        projects.value.forEach { project ->
            project.shots.forEach { shot ->
                val taskId = shot.taskId ?: return@forEach
                val task = byId[taskId] ?: return@forEach
                if (task.isActive) {
                    if (shot.status != "running") {
                        container.storyboardStore.updateShot(project.id, shot.id) {
                            it.copy(status = "running", error = null)
                        }
                    }
                    return@forEach
                }
                if (task.phase == GenerationPhase.SUCCEEDED) {
                    if (shot.status == "done" && shot.resultPath == task.results.firstOrNull()?.localPath) {
                        return@forEach
                    }
                    val resultPath = task.results.firstOrNull()?.localPath
                    if (resultPath == null) {
                        container.storyboardStore.markFailed(project.id, shot.id, AppStrings.get(R.string.board_err_no_result))
                        return@forEach
                    }
                    if (!syncing.add(shot.id)) return@forEach
                    viewModelScope.launch {
                        val previousLastFrame = shot.lastFramePath
                        val lastFrame = extractLastFrame(shot.id, resultPath)
                        container.storyboardStore.markDone(project.id, shot.id, resultPath, lastFrame)
                        val next = project.shots.getOrNull(project.shots.indexOfFirst { it.id == shot.id } + 1)
                        if (next != null && lastFrame != null && !next.referenceManual &&
                            (next.referencePath.isNullOrBlank() ||
                                next.referencePath == previousLastFrame ||
                                next.referencePath == shot.resultPath)
                        ) {
                            container.storyboardStore.setReference(project.id, next.id, lastFrame, manual = false)
                        }
                        syncing.remove(shot.id)
                    }
                } else if (task.phase == GenerationPhase.FAILED) {
                    container.storyboardStore.markFailed(
                        project.id,
                        shot.id,
                        task.error?.message ?: AppStrings.get(R.string.board_err_generation_failed),
                    )
                } else if (task.phase == GenerationPhase.CANCELLED) {
                    container.storyboardStore.updateShot(project.id, shot.id) {
                        it.copy(status = "draft", error = AppStrings.get(R.string.phase_cancelled))
                    }
                }
            }
        }
    }

    /** Extracts a frame near the end of a video; images are used directly. */
    private suspend fun extractLastFrame(shotId: String, path: String): String? = withContext(Dispatchers.IO) {
        if (!isVideoPath(path)) return@withContext path.takeIf { File(it).isFile }
        val dir = File(container.app.filesDir, "storyboards").apply { mkdirs() }
        val target = File(dir, "${shotId}_last.png")
        val frame = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val at = (durationMs * 1000L - 1L).coerceAtLeast(0L)
                retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            } finally {
                retriever.release()
            }
        }.getOrNull() ?: return@withContext null
        runCatching {
            target.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            target.absolutePath
        }.getOrNull()
    }
}
