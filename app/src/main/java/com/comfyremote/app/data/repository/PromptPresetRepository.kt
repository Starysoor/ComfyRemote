package com.comfyremote.app.data.repository

import com.comfyremote.app.LabeledText
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved prompt snippets ("prompt library"). Migrates the legacy "bookmarks"
 * key transparently; existing bookmarks are never lost.
 */
class PromptPresetRepository(private val prefs: SharedPreferences) {

    fun load(): List<PromptPreset> = runCatching {
        val raw = prefs.getString(KEY, null) ?: prefs.getString(LEGACY_KEY, null)
            ?: return@runCatching emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val prompts = mutableListOf<LabeledText>()
            val pa = o.optJSONArray("prompts")
            if (pa != null) for (j in 0 until pa.length()) {
                val po = pa.optJSONObject(j) ?: continue
                prompts.add(LabeledText(po.optString("label"), po.optString("value")))
            }
            PromptPreset(
                id = o.optString("id"),
                title = o.optString("title"),
                prompts = prompts,
                workflowPath = o.optString("workflowPath").ifEmpty { null },
                createdAt = o.optLong("time", o.optLong("createdAt", 0L)),
                lastUsedAt = o.optLong("lastUsedAt").takeIf { it > 0 },
            )
        }
    }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

    fun save(list: List<PromptPreset>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject()
                .put("id", b.id)
                .put("title", b.title)
                .put("time", b.createdAt)
                .put("workflowPath", b.workflowPath ?: "")
                .put("lastUsedAt", b.lastUsedAt ?: 0L)
                .put("prompts", JSONArray().apply {
                    b.prompts.forEach { p -> put(JSONObject().put("label", p.label).put("value", p.value)) }
                }))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    fun add(preset: PromptPreset): List<PromptPreset> {
        val list = (listOf(preset) + load()).take(30)
        save(list)
        return list
    }

    fun delete(id: String): List<PromptPreset> {
        val list = load().filter { it.id != id }
        save(list)
        return list
    }

    fun markUsed(id: String): List<PromptPreset> {
        val list = load().map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        save(list)
        return list
    }

    companion object {
        private const val KEY = "promptPresets"
        private const val LEGACY_KEY = "bookmarks"
    }
}

/** Saved prompt snippet (from history or the current editor). */
data class PromptPreset(
    val id: String,
    val title: String,
    val prompts: List<LabeledText>,
    val workflowPath: String? = null,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)
