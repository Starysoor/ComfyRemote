package com.comfyremote.app.data.storyboard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** JSON-file-backed multi-shot projects. */
class StoryboardStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "storyboards.json"))

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
        gcOrphanMediaLocked()
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
            val index = project.shots.indexOfFirst { it.id == shotId }
            val removed = project.shots.getOrNull(index) ?: return@updateLocked project
            val remaining = project.shots.toMutableList().apply { removeAt(index) }
            // Shots wired to the deleted shot's media now fall back to the new previous shot.
            val stale = listOfNotNull(removed.lastFramePath, removed.resultPath).toSet()
            if (stale.isNotEmpty()) {
                remaining.indices.forEach { i ->
                    if (!remaining[i].referenceManual && remaining[i].referencePath in stale) {
                        remaining[i] = remaining[i].copy(referencePath = remaining.getOrNull(i - 1)?.lastFramePath)
                    }
                }
            }
            project.copy(shots = remaining, updatedAt = System.currentTimeMillis())
        }.also { gcOrphanMediaLocked() }
    }

    fun attachTask(projectId: String, shotId: String, taskId: String): Boolean {
        var attached = false
        updateShot(projectId, shotId) { shot ->
            if (shot.status == "running" && !shot.taskId.isNullOrBlank()) {
                shot
            } else {
                attached = true
                shot.copy(status = "running", taskId = taskId, error = null, resultPath = null)
            }
        }
        return attached
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

    fun setReference(projectId: String, shotId: String, path: String?, manual: Boolean = true) =
        updateShot(projectId, shotId) {
            it.copy(referencePath = path, referenceManual = manual)
        }

    fun nextShot(projectId: String): StoryboardShot? {
        val project = _projects.value.firstOrNull { it.id == projectId } ?: return null
        // Do not skip over a running shot: its last frame feeds the next reference.
        val firstPending = project.shots.firstOrNull { it.status != "done" } ?: return null
        return firstPending.takeIf { it.status == "draft" || it.status == "failed" }
    }

    fun project(projectId: String): StoryboardProject? = _projects.value.firstOrNull { it.id == projectId }

    /** Media History must never prune while a storyboard still references it. */
    fun referencedMediaPaths(): Set<String> = _projects.value.asSequence()
        .flatMap { it.shots.asSequence() }
        .flatMap { shot -> listOfNotNull(shot.resultPath, shot.lastFramePath, shot.referencePath).asSequence() }
        .filter { it.isNotBlank() }
        .toSet()

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

    /** Deletes app-owned storyboard frames that no shot references anymore. */
    private fun gcOrphanMediaLocked() {
        val mediaDir = File(file.parentFile, "storyboards")
        val referenced = referencedMediaPaths()
        val cutoff = System.currentTimeMillis() - MEDIA_GC_GRACE_MS
        mediaDir.listFiles()?.forEach { candidate ->
            if (candidate.isFile && candidate.absolutePath !in referenced && candidate.lastModified() < cutoff) {
                runCatching { candidate.delete() }
            }
        }
    }

    companion object {
        private const val MEDIA_GC_GRACE_MS = 10 * 60_000L
    }
}
