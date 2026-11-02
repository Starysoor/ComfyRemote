package com.comfyremote.app.data.storyboard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** JSON-file-backed multi-shot projects. */
class StoryboardStore(context: Context) {
    private val file = File(context.filesDir, "storyboards.json")
    private val lock = Any()
    private val _projects = MutableStateFlow(load())
    val projects: StateFlow<List<StoryboardProject>> = _projects.asStateFlow()

    fun load(): List<StoryboardProject> = synchronized(lock) {
        runCatching {
            if (!file.isFile) emptyList() else StoryboardCodec.decode(file.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    fun create(title: String, workflowId: String, stylePrompt: String = ""): StoryboardProject =
        synchronized(lock) {
            val project = StoryboardProject(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "Storyboard" },
                workflowId = workflowId.trim(),
                stylePrompt = stylePrompt.trim(),
            )
            write(listOf(project) + _projects.value)
            project
        }

    fun delete(projectId: String) = synchronized(lock) {
        write(_projects.value.filterNot { it.id == projectId })
    }

    fun setStyle(projectId: String, style: String) = update(projectId) {
        it.copy(stylePrompt = style.trim(), updatedAt = System.currentTimeMillis())
    }

    fun addShot(projectId: String, prompt: String): StoryboardShot? = synchronized(lock) {
        var created: StoryboardShot? = null
        updateLocked(projectId) { project ->
            val shot = StoryboardShot(prompt = prompt.trim())
            created = shot
            project.copy(shots = project.shots + shot, updatedAt = System.currentTimeMillis())
        }
        created
    }

    fun updateShot(projectId: String, shotId: String, transform: (StoryboardShot) -> StoryboardShot) =
        synchronized(lock) {
            updateLocked(projectId) { project ->
                project.copy(
                    shots = project.shots.map { if (it.id == shotId) transform(it) else it },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

    fun deleteShot(projectId: String, shotId: String) = synchronized(lock) {
        updateLocked(projectId) { project ->
            project.copy(
                shots = project.shots.filterNot { it.id == shotId },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun attachTask(projectId: String, shotId: String, taskId: String) = updateShot(projectId, shotId) {
        it.copy(status = "running", taskId = taskId, error = null, resultPath = null)
    }

    fun markDone(projectId: String, shotId: String, resultPath: String, lastFramePath: String?) =
        updateShot(projectId, shotId) {
            it.copy(
                status = "done",
                resultPath = resultPath,
                lastFramePath = lastFramePath ?: it.lastFramePath,
                error = null,
            )
        }

    fun markFailed(projectId: String, shotId: String, message: String) = updateShot(projectId, shotId) {
        it.copy(status = "failed", error = message)
    }

    fun setReference(projectId: String, shotId: String, path: String?) = updateShot(projectId, shotId) {
        it.copy(referencePath = path)
    }

    fun nextShot(projectId: String): StoryboardShot? {
        val project = _projects.value.firstOrNull { it.id == projectId } ?: return null
        return project.shots.firstOrNull { it.status != "done" || it.resultPath.isNullOrBlank() }
    }

    fun project(projectId: String): StoryboardProject? = _projects.value.firstOrNull { it.id == projectId }

    private fun update(projectId: String, transform: (StoryboardProject) -> StoryboardProject) =
        synchronized(lock) { updateLocked(projectId, transform) }

    private fun updateLocked(
        projectId: String,
        transform: (StoryboardProject) -> StoryboardProject,
    ) {
        val next = _projects.value.map { if (it.id == projectId) transform(it) else it }
        write(next)
    }

    private fun write(projects: List<StoryboardProject>) {
        val sorted = projects.sortedByDescending { it.updatedAt }
        _projects.value = sorted
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.writeText(StoryboardCodec.encode(sorted))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }
}
