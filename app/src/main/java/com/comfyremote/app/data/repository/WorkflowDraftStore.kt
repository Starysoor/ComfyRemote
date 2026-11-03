package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class WorkflowDraftSnapshot(
    val workflowPath: String,
    val serverUrl: String,
    val values: Map<String, String>,
    val randomSeed: Boolean,
    val updatedAt: Long,
)

/** Lightweight last-used workflow state. Image filenames are scoped to the server they came from. */
class WorkflowDraftStore(private val prefs: SharedPreferences) {
    private val lock = Any()

    fun load(workflowPath: String, serverUrl: String): WorkflowDraftSnapshot? = synchronized(lock) {
        read().firstOrNull { it.workflowPath == workflowPath && it.serverUrl == serverUrl }
    }

    fun all(): List<WorkflowDraftSnapshot> = synchronized(lock) { read() }

    fun save(workflowPath: String, serverUrl: String, values: Map<String, String>, randomSeed: Boolean) = synchronized(lock) {
        val next = listOf(
            WorkflowDraftSnapshot(workflowPath, serverUrl, values, randomSeed, System.currentTimeMillis())
        ) + read().filterNot { it.workflowPath == workflowPath && it.serverUrl == serverUrl }
        write(next.sortedByDescending { it.updatedAt }.take(MAX_ITEMS))
    }

    fun clear(workflowPath: String, serverUrl: String) = synchronized(lock) {
        write(read().filterNot { it.workflowPath == workflowPath && it.serverUrl == serverUrl })
    }

    private fun read(): List<WorkflowDraftSnapshot> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val path = o.optString("workflowPath")
            val url = o.optString("serverUrl")
            if (path.isBlank() || url.isBlank()) return@mapNotNull null
            WorkflowDraftSnapshot(
                workflowPath = path,
                serverUrl = url,
                values = stringMap(o.optJSONObject("values")),
                randomSeed = o.optBoolean("randomSeed", true),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(items: List<WorkflowDraftSnapshot>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("workflowPath", item.workflowPath)
                    .put("serverUrl", item.serverUrl)
                    .put("values", JSONObject(item.values))
                    .put("randomSeed", item.randomSeed)
                    .put("updatedAt", item.updatedAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        o.keys().forEach { key -> result[key] = o.optString(key) }
        return result
    }

    companion object {
        private const val KEY = "workflowDraftsV1"
        private const val MAX_ITEMS = 30
    }
}
