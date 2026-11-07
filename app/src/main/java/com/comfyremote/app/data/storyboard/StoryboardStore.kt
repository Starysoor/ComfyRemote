package com.comfyremote.app.data.storyboard

import android.content.Context
import com.comfyremote.app.data.reference.ProjectAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONArray

/** JSON-file-backed multi-shot projects. */
class StoryboardStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "storyboards.json"))

    private val lock = Any()
    private val _projects = MutableStateFlow(load())
    val projects: StateFlow<List<StoryboardProject>> = _projects.asStateFlow()

    /** Last durable-state write failure; surfaced by the Board screen. */
    private val _persistenceError = MutableStateFlow<String?>(null)
    val persistenceError: StateFlow<String?> = _persistenceError.asStateFlow()

    fun clearPersistenceError() {
        _persistenceError.value = null
    }

    fun load(): List<StoryboardProject> = synchronized(lock) {
        runCatching {
            if (!file.isFile) emptyList() else StoryboardCodec.decode(file.readText())
        }.getOrDefault(emptyList())
            // Keep the persisted chain consistent with the project's relay setting,
            // including projects created before the optional relay existed.
            .map { project -> project.copy(shots = rebaseAutoReferences(project.shots, project.chainFrames)) }
            .sortedByDescending { it.updatedAt }
    }

    fun create(
        title: String,
        workflowId: String,
        stylePrompt: String = "",
        workflowPath: String = "",
        providerId: String = "",
        chainFrames: Boolean = true,
    ): StoryboardProject? = synchronized(lock) {
        val path = workflowPath.trim().ifBlank {
            workflowId.trim().takeIf { it.isNotBlank() }
                ?.let { com.comfyremote.app.data.cloud.AutoDlConfig.PATH_PREFIX + it }
                .orEmpty()
        }
        val project = StoryboardProject(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Storyboard" },
            workflowId = workflowId.trim().ifBlank { path.substringAfter(':') },
            workflowPath = path,
            providerId = providerId.trim(),
            stylePrompt = stylePrompt.trim(),
            chainFrames = chainFrames,
        )
        if (write(listOf(project) + _projects.value)) project else null
    }

    fun delete(projectId: String) = synchronized(lock) {
        if (write(_projects.value.filterNot { it.id == projectId })) {
            gcOrphanMediaLocked()
            true
        } else {
            false
        }
    }

    fun setStyle(projectId: String, style: String): Boolean = update(projectId) {
        it.copy(stylePrompt = style.trim(), updatedAt = System.currentTimeMillis())
    }

    fun setAspectRatio(projectId: String, ratio: String): Boolean = update(projectId) {
        it.copy(aspectRatio = ratio.trim(), updatedAt = System.currentTimeMillis())
    }

    /** Toggles the optional tail-frame relay. Turning it off clears automatic refs only. */
    fun setChainFrames(projectId: String, enabled: Boolean): Boolean = update(projectId) { project ->
        project.copy(
            chainFrames = enabled,
            shots = rebaseAutoReferences(project.shots, enabled),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun updateSettings(
        projectId: String,
        title: String,
        workflowPath: String,
        providerId: String,
        aspectRatio: String,
        defaultValues: Map<String, String>,
    ): Boolean = update(projectId) { project ->
        val path = workflowPath.trim()
        val workflowChanged = project.workflowPath != path
        project.copy(
            title = title.trim().ifBlank { "Storyboard" },
            workflowId = path.substringAfter(':'),
            workflowPath = path,
            providerId = providerId.trim(),
            aspectRatio = aspectRatio.trim(),
            defaultValues = defaultValues,
            shots = if (workflowChanged) {
                project.shots.map {
                    it.copy(workflowId = null, workflowPath = null, providerId = null, values = emptyMap())
                }
            } else {
                project.shots
            },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun addShot(projectId: String, prompt: String): StoryboardShot? = synchronized(lock) {
        var created: StoryboardShot? = null
        val persisted = updateLocked(projectId) { project ->
            val shot = StoryboardShot(prompt = prompt.trim())
            created = shot
            project.copy(
                shots = rebaseAutoReferences(project.shots + shot, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        created.takeIf { persisted }
    }

    fun insertShotAfter(projectId: String, shotId: String, prompt: String): StoryboardShot? = synchronized(lock) {
        var created: StoryboardShot? = null
        val persisted = updateLocked(projectId) { project ->
            val index = project.shots.indexOfFirst { it.id == shotId }
            if (index < 0) return@updateLocked project
            val shot = StoryboardShot(prompt = prompt.trim())
            created = shot
            val shots = project.shots.toMutableList().apply { add(index + 1, shot) }
            project.copy(
                shots = rebaseAutoReferences(shots, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        created.takeIf { persisted }
    }

    fun duplicateShot(projectId: String, shotId: String): StoryboardShot? = synchronized(lock) {
        var created: StoryboardShot? = null
        val persisted = updateLocked(projectId) { project ->
            val index = project.shots.indexOfFirst { it.id == shotId }
            if (index < 0) return@updateLocked project
            val copy = project.shots[index].copy(
                id = UUID.randomUUID().toString(),
                status = "draft",
                taskId = null,
                resultPath = null,
                lastFramePath = null,
                tailFrameStored = false,
                error = null,
            )
            created = copy
            val shots = project.shots.toMutableList().apply { add(index + 1, copy) }
            project.copy(
                shots = rebaseAutoReferences(shots, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        created.takeIf { persisted }
    }

    fun moveShot(projectId: String, shotId: String, delta: Int): Boolean = synchronized(lock) {
        var moved = false
        val persisted = updateLocked(projectId) { project ->
            val index = project.shots.indexOfFirst { it.id == shotId }
            val target = index + delta
            if (index < 0 || target !in project.shots.indices) return@updateLocked project
            val shots = project.shots.toMutableList()
            val shot = shots.removeAt(index)
            shots.add(target, shot)
            moved = true
            project.copy(
                shots = rebaseAutoReferences(shots, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        moved && persisted
    }

    /** Adds or replaces an asset by id (tail frames reuse one stable asset id per shot). */
    fun addAsset(projectId: String, asset: ProjectAsset): Boolean = synchronized(lock) {
        var added = false
        val persisted = update(projectId) { project ->
            added = true
            val existing = project.assets.any { it.id == asset.id }
            val assets = if (existing) {
                project.assets.map { if (it.id == asset.id) asset else it }
            } else {
                project.assets + asset
            }
            project.copy(assets = assets, updatedAt = System.currentTimeMillis())
        }
        added && persisted
    }

    fun removeAsset(projectId: String, assetId: String): Boolean = synchronized(lock) {
        var removed = false
        var removedPath: String? = null
        val persisted = update(projectId) { project ->
            val asset = project.assets.firstOrNull { it.id == assetId }
                ?: return@update project
            removed = true
            removedPath = asset.path
            val remaining = project.assets.filterNot { it.id == assetId }
            val cleaned = project.shots.map { shot ->
                val referencedById = assetId in shot.referenceAssetIds
                val referencedByPath = shot.referencePath == asset.path
                // A tail frame assets the next shot's automatic reference by path,
                // not by id. Removing it from the asset strip must not break the
                // relay or turn the automatic reference into a manual empty one.
                val referencedByAutoChain = referencedByPath &&
                    !shot.referenceManual && shot.referenceAssetIds.isEmpty()
                if (!referencedById && (!referencedByPath || referencedByAutoChain)) return@map shot

                val ids = shot.referenceAssetIds - assetId
                val nextAsset = ids.firstNotNullOfOrNull { id ->
                    remaining.firstOrNull { it.id == id }
                }
                shot.copy(
                    referenceAssetIds = ids,
                    referencePath = when {
                        !referencedByPath -> shot.referencePath
                        nextAsset != null -> nextAsset.path
                        else -> null
                    },
                    referenceRole = if (referencedByPath && nextAsset != null) {
                        nextAsset.role
                    } else {
                        shot.referenceRole
                    },
                    // Removing an explicit asset must not silently fall back to an auto frame.
                    referenceManual = true,
                )
            }
            project.copy(
                assets = remaining,
                shots = rebaseAutoReferences(cleaned, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (persisted && removed) {
            // An explicitly removed asset no longer needs the GC grace period.
            removedPath?.takeIf { path -> path.isNotBlank() && path !in referencedMediaPaths() }
                ?.let { path -> runCatching { File(path).delete() } }
            gcOrphanMediaLocked()
        }
        removed && persisted
    }

    fun updateShot(projectId: String, shotId: String, transform: (StoryboardShot) -> StoryboardShot): Boolean =
        synchronized(lock) {
            updateLocked(projectId) { project ->
                project.copy(
                    shots = project.shots.map { if (it.id == shotId) transform(it) else it },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

    fun deleteShot(projectId: String, shotId: String): Boolean = synchronized(lock) {
        val persisted = updateLocked(projectId) { project ->
            val index = project.shots.indexOfFirst { it.id == shotId }
            if (index < 0) return@updateLocked project
            val remaining = project.shots.toMutableList().apply { removeAt(index) }
            project.copy(
                shots = rebaseAutoReferences(remaining, project.chainFrames),
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (persisted) gcOrphanMediaLocked()
        persisted
    }

    fun attachTask(projectId: String, shotId: String, taskId: String): Boolean {
        var attached = false
        val persisted = updateShot(projectId, shotId) { shot ->
            if (shot.status == "running" && !shot.taskId.isNullOrBlank()) {
                shot
            } else {
                attached = true
                shot.copy(status = "running", taskId = taskId, error = null, resultPath = null)
            }
        }
        return attached && persisted
    }

    fun markDone(projectId: String, shotId: String, resultPath: String, lastFramePath: String?): Boolean =
        synchronized(lock) {
            updateLocked(projectId) { project ->
                val shots = project.shots.map { shot ->
                    if (shot.id != shotId) shot
                    else shot.copy(
                        status = "done",
                        resultPath = resultPath,
                        lastFramePath = lastFramePath ?: shot.lastFramePath,
                        // A newly extracted frame still needs to enter assets/results.
                        tailFrameStored = if (lastFramePath != null) false else shot.tailFrameStored,
                        error = null,
                    )
                }
                project.copy(
                    shots = rebaseAutoReferences(shots, project.chainFrames),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

    fun markFailed(projectId: String, shotId: String, message: String): Boolean =
        updateShot(projectId, shotId) {
            it.copy(status = "failed", error = message)
        }

    fun setReference(
        projectId: String,
        shotId: String,
        path: String?,
        manual: Boolean = true,
    ): Boolean = updateShot(projectId, shotId) {
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
    fun referencedMediaPaths(): Set<String> {
        val shots = _projects.value.asSequence()
            .flatMap { it.shots.asSequence() }
            .flatMap { shot -> listOfNotNull(shot.resultPath, shot.lastFramePath, shot.referencePath).asSequence() }
        val assets = _projects.value.asSequence()
            .flatMap { it.assets.asSequence() }
            .map { it.path }
        return (shots + assets).filter { it.isNotBlank() }.toSet()
    }

    /**
     * Rebuilds automatic frame inheritance in timeline order. Manual references and
     * project-asset references always win; the most recent preceding last frame is
     * carried forward until a shot produces a newer one.
     */
    private fun rebaseAutoReferences(
        shots: List<StoryboardShot>,
        chainFrames: Boolean,
    ): List<StoryboardShot> {
        if (!chainFrames) {
            var cleared = false
            val result = shots.map { shot ->
                if (!shot.referenceManual && shot.referenceAssetIds.isEmpty() &&
                    shot.referencePath != null
                ) {
                    cleared = true
                    shot.copy(referencePath = null)
                } else {
                    shot
                }
            }
            return if (cleared) result else shots
        }
        var inherited: String? = null
        var changed = false
        val result = shots.map { shot ->
            val next = if (!shot.referenceManual && shot.referenceAssetIds.isEmpty() &&
                shot.referencePath != inherited
            ) {
                changed = true
                shot.copy(referencePath = inherited)
            } else {
                shot
            }
            if (shot.lastFramePath != null) inherited = shot.lastFramePath
            next
        }
        return if (changed) result else shots
    }

    private fun update(projectId: String, transform: (StoryboardProject) -> StoryboardProject): Boolean =
        synchronized(lock) { updateLocked(projectId, transform) }

    private fun updateLocked(
        projectId: String,
        transform: (StoryboardProject) -> StoryboardProject,
    ): Boolean {
        val next = _projects.value.map { if (it.id == projectId) transform(it) else it }
        return write(next)
    }

    /** Atomic write first, then publish the in-memory project list. */
    private fun write(projects: List<StoryboardProject>): Boolean {
        val sorted = projects.sortedByDescending { it.updatedAt }
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            _persistenceError.value = "Cannot create storyboard directory: $parent"
            return false
        }
        val tmp = File(parent, file.name + ".tmp")
        return try {
            // decode() intentionally tolerates damaged files for read-only UI;
            // writes must not overwrite that damage and erase recoverable work.
            if (file.isFile) {
                val raw = file.readText()
                val rows = JSONArray(raw)
                if (StoryboardCodec.decode(raw).size != rows.length()) {
                    throw IOException("Existing storyboard index contains unreadable projects")
                }
            }
            FileOutputStream(tmp).use { stream ->
                stream.write(StoryboardCodec.encode(sorted).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
            _projects.value = sorted
            true
        } catch (error: Exception) {
            tmp.delete()
            _persistenceError.value = error.message ?: error.javaClass.simpleName
            false
        }
    }

    /** Deletes app-owned storyboard frames that no shot references anymore. */
    private fun gcOrphanMediaLocked() {
        val mediaDir = File(file.parentFile, "storyboards")
        val referenced = referencedMediaPaths()
        val cutoff = System.currentTimeMillis() - MEDIA_GC_GRACE_MS
        mediaDir.walkTopDown().filter { it.isFile }.forEach { candidate ->
            if (candidate.absolutePath !in referenced && candidate.lastModified() < cutoff) {
                runCatching { candidate.delete() }
            }
        }
    }

    companion object {
        private const val MEDIA_GC_GRACE_MS = 10 * 60_000L
    }
}
