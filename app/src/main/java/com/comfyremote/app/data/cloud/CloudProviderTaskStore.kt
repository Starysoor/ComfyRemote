package com.comfyremote.app.data.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * File-backed provider task state so hosted jobs survive process death.
 *
 * Two kinds of records are kept:
 * - `pending`: provider-specific recovery context (family, endpoint, template snapshot)
 * - `result`: a completed result that was already paid for but may not have reached History
 */
class CloudProviderTaskStore internal constructor(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "cloud_tasks"))

    private val lock = Any()

    fun savePending(providerId: String, taskId: String, payload: JSONObject) =
        write(providerId, taskId, PENDING, payload)

    /**
     * Use only after an asynchronous paid API has returned its remote task ID.
     * If this write fails the task may still be billable: expose its ID and
     * prohibit an automatic resubmission that could double-charge the user.
     */
    fun saveAcceptedTask(providerId: String, taskId: String, payload: JSONObject, providerLabel: String) {
        try {
            savePending(providerId, taskId, payload)
        } catch (error: Exception) {
            throw IOException(
                "$providerLabel accepted remote task $taskId, but local recovery could not be saved; " +
                    "check this task with the provider before submitting again",
                error,
            )
        }
    }

    fun pending(providerId: String, taskId: String): JSONObject? =
        read(providerId, taskId, PENDING)

    fun saveResult(providerId: String, taskId: String, result: CloudTaskResult) =
        write(providerId, taskId, RESULT, resultToJson(result))

    fun result(providerId: String, taskId: String): CloudTaskResult? =
        read(providerId, taskId, RESULT)?.let(::resultFromJson)

    fun clear(providerId: String, taskId: String) = synchronized(lock) {
        file(providerId, taskId, PENDING).delete()
        file(providerId, taskId, RESULT).delete()
    }

    private fun write(providerId: String, taskId: String, kind: String, payload: JSONObject) {
        synchronized(lock) {
            // A swallowed write error would make a paid remote task impossible to recover.
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("Cannot create cloud task recovery directory: $dir")
            }
            val target = file(providerId, taskId, kind)
            val tmp = File.createTempFile(target.name + ".", ".tmp", dir)
            try {
                FileOutputStream(tmp).use { stream ->
                    stream.write(payload.toString().toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                // Do not fall back to writing the target directly: it must remain valid on failure.
                if (!tmp.renameTo(target)) {
                    throw IOException("Cannot atomically store cloud task recovery data: ${target.name}")
                }
                pruneTemporaryFilesLocked()
            } finally {
                tmp.delete()
            }
        }
    }

    private fun read(providerId: String, taskId: String, kind: String): JSONObject? =
        synchronized(lock) {
            runCatching {
                val file = file(providerId, taskId, kind)
                if (!file.isFile) return@runCatching null
                JSONObject(file.readText())
            }.getOrNull()
        }

    private fun file(providerId: String, taskId: String, kind: String): File =
        File(dir, "${digest("$providerId\u0000$taskId")}.$kind.json")

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(bytes.size * 2) {
            bytes.forEach { byte -> append("%02x".format(byte.toInt() and 0xFF)) }
        }
    }

    private fun pruneTemporaryFilesLocked() {
        // Only the owning task can decide when its pending/result state is no longer needed.
        // Pruning .json by age/count silently orphaned long-running and recovered cloud jobs.
        val now = System.currentTimeMillis()
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".tmp") && now - it.lastModified() > TEMP_MAX_AGE_MS }
            ?.forEach { it.delete() }
    }

    private fun resultToJson(result: CloudTaskResult): JSONObject = JSONObject()
        .put("taskId", result.taskId)
        .put("status", result.status)
        .put("message", result.message)
        .put("durationSeconds", result.durationSeconds)
        .put(
            "results",
            JSONArray().apply {
                result.results.forEach { item ->
                    put(
                        JSONObject()
                            .put("url", item.url)
                            .put("type", item.type)
                            .put("fileType", item.fileType)
                            .put("outputType", item.outputType),
                    )
                }
            },
        )

    private fun resultFromJson(o: JSONObject): CloudTaskResult? = runCatching {
        val array = o.optJSONArray("results") ?: JSONArray()
        val results = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CloudResult(
                url = url,
                type = item.optString("type", "image"),
                fileType = item.optString("fileType"),
                outputType = item.optString("outputType", "output"),
            )
        }
        CloudTaskResult(
            taskId = o.optString("taskId"),
            status = o.optString("status", "FAILED"),
            results = results,
            message = o.optString("message"),
            durationSeconds = o.optLong("durationSeconds"),
        )
    }.getOrNull()

    companion object {
        private const val PENDING = "pending"
        private const val RESULT = "result"
        private const val TEMP_MAX_AGE_MS = 24L * 60 * 60_000L
    }
}
