package com.comfyremote.app.feature.storyboard

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
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

class StoryboardViewModel(private val container: AppContainer) : ViewModel() {
    val projects: StateFlow<List<StoryboardProject>> = container.storyboardStore.projects
    private val _workflows = MutableStateFlow<List<CloudWorkflowSummary>>(emptyList())
    val workflows: StateFlow<List<CloudWorkflowSummary>> = _workflows.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val syncing = mutableSetOf<String>()

    init {
        refreshWorkflows()
        viewModelScope.launch {
            container.generationManager.tasks.collect { tasks -> syncTasks(tasks) }
        }
    }

    fun refreshWorkflows() {
        viewModelScope.launch {
            runCatching { container.autoDlRepository.catalog(force = false) }
                .onSuccess { list ->
                    _workflows.value = list.filter { it.kind == "video" }
                        .sortedBy { it.name.lowercase() }
                }
                .onFailure { e -> _message.value = e.message }
        }
    }

    fun createProject(title: String, workflowId: String, stylePrompt: String): StoryboardProject? {
        val id = workflowId.trim()
        if (id.isBlank()) {
            _message.value = AppStrings.get(R.string.board_err_choose_workflow)
            return null
        }
        return container.storyboardStore.create(title, id, stylePrompt)
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

    fun assignAssetToShot(projectId: String, shotId: String, assetId: String) {
        val asset = container.storyboardStore.project(projectId)
            ?.assets?.firstOrNull { it.id == assetId } ?: return
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(
                referencePath = asset.path,
                referenceManual = true,
                referenceRole = asset.role,
            )
        }
    }

    fun clearShotReference(projectId: String, shotId: String) =
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(referencePath = null, referenceManual = true, referenceRole = ReferenceRole.AUTO)
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
        val reference = if (shot.referenceManual) shot.referencePath
        else shot.referencePath ?: previous?.lastFramePath
        val prompt = listOf(project.stylePrompt, shot.prompt)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val name = "${project.title} · ${index + 1}"
        val workflowId = shot.workflowId?.takeIf { it.isNotBlank() } ?: project.workflowId
        val values = project.defaultValues + shot.values +
            (if (prompt.isBlank()) emptyMap() else mapOf("prompt" to prompt))
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(status = if (it.status == "running") it.status else "draft", error = null)
        }
        container.draftTransferRepository.put(
            DraftTransfer(
                workflowPath = AutoDlConfig.PATH_PREFIX + workflowId,
                workflowName = name,
                values = values,
                randomSeed = true,
                serverUrl = null,
                referenceLocalPath = reference,
                storyboardProjectId = project.id,
                storyboardShotId = shot.id,
            )
        )
        return AutoDlConfig.PATH_PREFIX + workflowId to name
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
