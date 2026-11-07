package com.comfyremote.app.data.project

import com.comfyremote.app.data.reference.AssetKind
import com.comfyremote.app.data.storyboard.StoryboardCodec
import com.comfyremote.app.data.storyboard.StoryboardProject
import com.comfyremote.app.data.storyboard.StoryboardShot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONArray

/**
 * One-way, lossless migration from the legacy storyboard index
 * (storyboards.json, unversioned) to the Studio project index
 * (projects.json, versioned).
 *
 * Safety semantics:
 * - storyboards.json is copied to a timestamped backup before anything else;
 *   the two most recent backups are kept.
 * - The commit point is the atomic publish of projects.json; a crash before
 *   that leaves the legacy index untouched and the next launch simply retries.
 * - storyboards.json itself is never modified or deleted: it stays frozen as
 *   the rollback anchor (it is NOT updated by later edits).
 * - Any decode/encode failure aborts the migration without writing
 *   projects.json, so the legacy path keeps working; the caller surfaces the
 *   error instead of silently serving an empty project list.
 */
object ProjectMigration {
    private const val PROJECTS_FILE = "projects.json"
    private const val LEGACY_FILE = "storyboards.json"
    private const val LEGACY_BACKUP_PREFIX = "storyboards.backup-"
    private const val BACKUPS_TO_KEEP = 2

    data class Result(
        val migrated: Boolean,
        val projectCount: Int,
        val error: String? = null,
    ) {
        val aborted: Boolean get() = error != null
    }

    fun migrateIfNeeded(
        filesDir: File,
        now: Long = System.currentTimeMillis(),
        writer: (File, String) -> Unit = ::atomicWriteJson,
    ): Result {
        val projectsFile = File(filesDir, PROJECTS_FILE)
        val legacyFile = File(filesDir, LEGACY_FILE)

        if (projectsFile.isFile) {
            val raw = runCatching { projectsFile.readText() }.getOrNull()
                ?: return Result(false, 0, "projects.json is unreadable")
            if (ProjectCodec.rawProjectCount(raw) != null) {
                // Healthy (or tolerantly decodable) index: migration already done.
                return Result(false, ProjectCodec.decode(raw).size)
            }
            // Corrupted new index: fall back to re-migrating from the frozen
            // legacy anchor instead of serving an empty list.
            if (!legacyFile.isFile) {
                return Result(false, 0, "projects.json is corrupt and no legacy index exists")
            }
        }

        if (!legacyFile.isFile) return Result(false, 0)

        val legacyRaw = runCatching { legacyFile.readText() }.getOrNull()
            ?: return Result(false, 0, "storyboards.json is unreadable")
        val legacyProjects = StoryboardCodec.decode(legacyRaw)
        val legacyRows = rawLegacyProjectCount(legacyRaw)
        if (legacyRows == null) {
            return Result(false, 0, "storyboards.json is not valid JSON")
        }
        if (legacyRows != legacyProjects.size) {
            // Rows the legacy codec had to drop: refuse to migrate a damaged
            // index; the user keeps the legacy behavior instead of data loss.
            return Result(false, 0, "storyboards.json contains unreadable projects")
        }

        return try {
            backupLegacy(legacyFile, filesDir, now)
            val migrated = legacyProjects.map { toVideoProject(it, now) }
            val encoded = ProjectCodec.encode(migrated)
            // Self-check before publishing: the encoded index must decode back
            // to the same project identities and shot counts.
            val redecoded = ProjectCodec.decode(encoded)
            val lossless = redecoded.size == migrated.size &&
                redecoded.map { it.id } == migrated.map { it.id } &&
                redecoded.map { it.shots.size } == migrated.map { it.shots.size } &&
                redecoded.map { it.assets.size } == migrated.map { it.assets.size }
            if (!lossless) {
                return Result(false, 0, "migration self-check failed")
            }
            writer(projectsFile, encoded)
            Result(true, migrated.size)
        } catch (error: Exception) {
            Result(false, 0, error.message ?: error.javaClass.simpleName)
        }
    }

    /** Maps a legacy project field-for-field; timestamps and ids stay identical. */
    internal fun toVideoProject(legacy: StoryboardProject, now: Long): VideoProject = VideoProject(
        id = legacy.id,
        title = legacy.title,
        synopsis = "",
        aspectRatio = legacy.aspectRatio,
        targetDurationSeconds = null,
        stylePrompt = legacy.stylePrompt,
        workflowId = legacy.workflowId,
        workflowPath = legacy.workflowPath,
        providerId = legacy.providerId,
        defaultValues = legacy.defaultValues,
        chainFrames = legacy.chainFrames,
        budget = null,
        shots = legacy.shots.map { toProjectShot(it, now) },
        assets = legacy.assets.map { asset -> asset.copy(kind = AssetKind.fromPath(asset.path)) },
        createdAt = legacy.createdAt,
        updatedAt = legacy.updatedAt,
    )

    /**
     * Maps a legacy shot: live fields carry over unchanged and one deterministic
     * take records the generation that produced the current result. Shots that
     * never generated get no take.
     */
    internal fun toProjectShot(legacy: StoryboardShot, now: Long): ProjectShot {
        val take = if (legacy.taskId != null || legacy.resultPath != null) {
            ShotTake(
                id = legacyTakeId(legacy.id),
                providerId = legacy.providerId.orEmpty(),
                workflowPath = legacy.workflowPath.orEmpty(),
                prompt = legacy.prompt,
                values = legacy.values,
                referenceAssetIds = legacy.referenceAssetIds,
                status = when (legacy.status) {
                    "done" -> "done"
                    "failed" -> "failed"
                    // Still marked running from an interrupted session: keep it
                    // running so orphan recovery closes it with a proper error.
                    "running" -> "running"
                    else -> "cancelled"
                },
                taskId = legacy.taskId,
                resultPath = legacy.resultPath,
                error = legacy.error,
                createdAt = now,
            )
        } else {
            null
        }
        return ProjectShot(
            id = legacy.id,
            prompt = legacy.prompt,
            cameraNotes = null,
            referencePath = legacy.referencePath,
            referenceManual = legacy.referenceManual,
            referenceRole = legacy.referenceRole,
            referenceAssetIds = legacy.referenceAssetIds,
            workflowPath = legacy.workflowPath,
            providerId = legacy.providerId,
            workflowId = legacy.workflowId,
            values = legacy.values,
            lastFramePath = legacy.lastFramePath,
            tailFrameStored = legacy.tailFrameStored,
            status = legacy.status,
            taskId = legacy.taskId,
            resultPath = legacy.resultPath,
            error = legacy.error,
            takes = listOfNotNull(take),
            selectedTakeId = take?.id,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Timestamped backup copy; re-running overwrites the same-named backup. */
    private fun backupLegacy(legacyFile: File, filesDir: File, now: Long) {
        val backup = File(filesDir, "$LEGACY_BACKUP_PREFIX$now.json")
        runCatching {
            legacyFile.copyTo(backup, overwrite = true)
            rotateBackups(filesDir)
        }.onFailure { error ->
            throw IOException("Cannot back up legacy index: ${error.message}", error)
        }
    }

    /** Keeps the newest [BACKUPS_TO_KEEP] backups; the timestamp sorts lexicographically. */
    private fun rotateBackups(filesDir: File) {
        filesDir.listFiles { file -> file.name.startsWith(LEGACY_BACKUP_PREFIX) && file.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?.drop(BACKUPS_TO_KEEP)
            ?.forEach { backup -> runCatching { backup.delete() } }
    }

    private fun rawLegacyProjectCount(raw: String): Int? = runCatching {
        JSONArray(raw).length()
    }.getOrNull()

    private fun atomicWriteJson(target: File, content: String) {
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create project directory: $parent")
        }
        val tmp = File(parent, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Exception) {
            tmp.delete()
            throw error
        }
    }
}
