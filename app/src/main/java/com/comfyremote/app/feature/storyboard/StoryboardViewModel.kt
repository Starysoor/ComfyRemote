package com.comfyremote.app.feature.storyboard

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.data.storyboard.StoryboardProject
import com.comfyremote.app.data.storyboard.StoryboardShot
import com.comfyremote.app.feature.generate.isVideoPath
import com.comfyremote.app.ui.L10n
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
            _message.value = L10n.t("请选择云端视频工作流", "Choose a cloud video workflow")
            return null
        }
        return container.storyboardStore.create(title, id, stylePrompt)
    }

    fun deleteProject(id: String) = container.storyboardStore.delete(id)

    fun setStyle(projectId: String, style: String) = container.storyboardStore.setStyle(projectId, style)

    fun addShot(projectId: String, prompt: String) {
        if (prompt.isBlank()) {
            _message.value = L10n.t("镜头描述不能为空", "Shot prompt cannot be empty")
            return
        }
        container.storyboardStore.addShot(projectId, prompt)
    }

    fun deleteShot(projectId: String, shotId: String) =
        container.storyboardStore.deleteShot(projectId, shotId)

    fun updateShot(projectId: String, shotId: String, prompt: String) =
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(prompt = prompt.trim())
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
        val previous = project.shots.getOrNull(index - 1)
        val reference = shot.referencePath ?: previous?.lastFramePath
        val prompt = listOf(project.stylePrompt, shot.prompt)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val name = "${project.title} · ${index + 1}"
        container.storyboardStore.updateShot(projectId, shotId) {
            it.copy(status = if (it.status == "running") it.status else "draft", error = null)
        }
        container.draftTransferRepository.put(
            DraftTransfer(
                workflowPath = AutoDlConfig.PATH_PREFIX + project.workflowId,
                workflowName = name,
                values = if (prompt.isBlank()) emptyMap() else mapOf("prompt" to prompt),
                randomSeed = true,
                serverUrl = null,
                referenceLocalPath = reference,
                storyboardProjectId = project.id,
                storyboardShotId = shot.id,
            )
        )
        return AutoDlConfig.PATH_PREFIX + project.workflowId to name
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
                        container.storyboardStore.markFailed(project.id, shot.id, L10n.t("任务完成但没有结果", "Task finished without a result"))
                        return@forEach
                    }
                    if (!syncing.add(shot.id)) return@forEach
                    viewModelScope.launch {
                        val lastFrame = extractLastFrame(shot.id, resultPath)
                        container.storyboardStore.markDone(project.id, shot.id, resultPath, lastFrame)
                        val next = project.shots.getOrNull(project.shots.indexOfFirst { it.id == shot.id } + 1)
                        if (next != null && next.referencePath.isNullOrBlank() && lastFrame != null) {
                            container.storyboardStore.setReference(project.id, next.id, lastFrame)
                        }
                        syncing.remove(shot.id)
                    }
                } else if (task.phase == GenerationPhase.FAILED) {
                    container.storyboardStore.markFailed(
                        project.id,
                        shot.id,
                        task.error?.message ?: L10n.t("生成失败", "Generation failed"),
                    )
                } else if (task.phase == GenerationPhase.CANCELLED) {
                    container.storyboardStore.updateShot(project.id, shot.id) {
                        it.copy(status = "draft", error = L10n.t("已取消", "Cancelled"))
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
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val at = (duration * 1000L - 50_000L).coerceAtLeast(0L)
                retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
