package com.comfyremote.app.data.cloud

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CloudSpendEntry(
    val id: String = UUID.randomUUID().toString(),
    val workflowName: String,
    /** Null when the provider did not expose a price for the selected options. */
    val points: Int?,
    val estimated: Boolean = true,
    /** Provider task id, used to avoid double-counting after recovery. */
    val remoteTaskId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Local, best-effort ledger of submitted cloud jobs. Never represents a bill. */
class CloudSpendStore(private val prefs: SharedPreferences) {
    private val lock = Any()
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<CloudSpendEntry>> = _entries.asStateFlow()

    fun total(): Int = _entries.value.sumOf { it.points ?: 0 }

    fun record(workflowName: String, points: Int?, remoteTaskId: String? = null) = synchronized(lock) {
        if (remoteTaskId != null && _entries.value.any { it.remoteTaskId == remoteTaskId }) {
            return@synchronized
        }
        val entry = CloudSpendEntry(
            workflowName = workflowName,
            points = points,
            remoteTaskId = remoteTaskId,
        )
        val list = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        write(list)
        _entries.value = list
    }

    fun clear() = synchronized(lock) {
        write(emptyList())
        _entries.value = emptyList()
    }

    private fun read(): List<CloudSpendEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            CloudSpendEntry(
                id = o.optString("id"),
                workflowName = o.optString("workflow"),
                points = if (o.has("points") && !o.isNull("points")) o.optInt("points") else null,
                estimated = o.optBoolean("estimated", true),
                remoteTaskId = o.optString("remoteTaskId").ifEmpty { null },
                createdAt = o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<CloudSpendEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("workflow", entry.workflowName)
                    .put("points", entry.points ?: JSONObject.NULL)
                    .put("estimated", entry.estimated)
                    .put("remoteTaskId", entry.remoteTaskId ?: "")
                    .put("at", entry.createdAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "cloudSpendV1"
        private const val MAX_ENTRIES = 100
    }
}
