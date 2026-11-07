package com.comfyremote.app.data.generation

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONArray

/**
 * Small JSON-backed persistence for runtime tasks. Active tasks survive process death;
 * terminal tasks remain available to the Task Center while long-term results live in History.
 */
class GenerationTaskStore internal constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "generation_tasks.json"))

    private val lock = Any()

    fun load(): List<GenerationTask> = synchronized(lock) {
        runCatching {
            if (!file.isFile) emptyList() else GenerationTaskCodec.decode(file.readText())
        }.getOrDefault(emptyList())
    }

    /**
     * Atomically persists tasks. Throws when the state cannot be written so the
     * caller can avoid publishing an unrecoverable in-memory queue.
     */
    @Throws(IOException::class)
    fun save(tasks: List<GenerationTask>) = synchronized(lock) {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create task state directory: $parent")
        }
        val tmp = File(parent, file.name + ".tmp")
        try {
            // If a prior crash damaged the index, never silently replace its
            // only recoverable bytes with an empty task list.
            if (file.isFile) {
                val raw = file.readText()
                val rows = JSONArray(raw)
                if (GenerationTaskCodec.decode(raw).size != rows.length()) {
                    throw IOException("Existing task index contains unreadable tasks")
                }
            }
            FileOutputStream(tmp).use { stream ->
                stream.write(GenerationTaskCodec.encode(tasks).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Never fall back to a direct write: a half-written file would lose
            // every active/paid task on the next process start.
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
        } catch (error: Exception) {
            tmp.delete()
            if (error is IOException) throw error
            throw IOException("Cannot persist task state: ${error.message}", error)
        }
    }
}
