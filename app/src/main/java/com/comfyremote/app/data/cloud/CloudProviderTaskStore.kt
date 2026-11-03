package com.comfyremote.app.data.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    private fun write(providerId: String, taskId: String, kind: String, payload: JSONObject) =
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                val target = file(providerId, taskId, kind)
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(payload.toString())
                if (!tmp.renameTo(target)) {
                    target.writeText(tmp.readText())
                    tmp.delete()
                }
                pruneLocked()
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

    private fun pruneLocked() {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        val now = System.currentTimeMillis()
        files.filter { now - it.lastModified() > MAX_AGE_MS }.forEach { it.delete() }
        val remaining = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
        if (remaining.size > MAX_FILES) {
            remaining.take(remaining.size - MAX_FILES).forEach { it.delete() }
        }
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
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60_000L
        private const val MAX_FILES = 400
    }
}
