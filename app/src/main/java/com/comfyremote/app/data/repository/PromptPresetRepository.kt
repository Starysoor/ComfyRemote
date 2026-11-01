package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.LabeledText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved prompt snippets ("prompt library"). Migrates the legacy "bookmarks"
 * key transparently; existing bookmarks are never lost.
 */
class PromptPresetRepository(private val prefs: SharedPreferences) {
    private val lock = Any()
    private val _presets = MutableStateFlow(read())
    val presets: StateFlow<List<PromptPreset>> = _presets.asStateFlow()

    fun load(): List<PromptPreset> = _presets.value

    fun save(list: List<PromptPreset>) = synchronized(lock) {
        write(list)
        _presets.value = list.sortedWith(
            compareByDescending<PromptPreset> { it.lastUsedAt ?: 0L }
                .thenByDescending { it.createdAt }
        )
    }

    fun add(preset: PromptPreset): List<PromptPreset> = synchronized(lock) {
        val list = (listOf(preset) + _presets.value.filter { it.id != preset.id }).take(MAX_ITEMS)
        write(list)
        _presets.value = list
        list
    }

    fun delete(id: String): List<PromptPreset> = synchronized(lock) {
        val list = _presets.value.filter { it.id != id }
        write(list)
        _presets.value = list
        list
    }

    fun markUsed(id: String): List<PromptPreset> = synchronized(lock) {
        val list = _presets.value.map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        }.sortedWith(compareByDescending<PromptPreset> { it.lastUsedAt ?: 0L }.thenByDescending { it.createdAt })
        write(list)
        _presets.value = list
        list
    }

    private fun read(): List<PromptPreset> = runCatching {
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

    private fun write(list: List<PromptPreset>) {
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
        prefs.edit().putString(KEY, arr.toString()).remove(LEGACY_KEY).apply()
    }

    companion object {
        private const val KEY = "promptPresets"
        private const val LEGACY_KEY = "bookmarks"
        private const val MAX_ITEMS = 50
    }
}

data class PromptPreset(
    val id: String,
    val title: String,
    val prompts: List<LabeledText>,
    val workflowPath: String? = null,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)
