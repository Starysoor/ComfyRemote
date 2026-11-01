package com.comfyremote.app.data.generation

import android.content.Context
import java.io.File

/**
 * Small JSON-backed persistence for runtime tasks. Active tasks survive process death;
 * terminal tasks remain available to the Task Center while long-term results live in History.
 */
class GenerationTaskStore(context: Context) {
    private val file = File(context.filesDir, "generation_tasks.json")
    private val lock = Any()

    fun load(): List<GenerationTask> = synchronized(lock) {
        runCatching {
            if (!file.isFile) emptyList() else GenerationTaskCodec.decode(file.readText())
        }.getOrDefault(emptyList())
    }

    fun save(tasks: List<GenerationTask>) = synchronized(lock) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.writeText(GenerationTaskCodec.encode(tasks))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { tmp.delete() }
    }
}
