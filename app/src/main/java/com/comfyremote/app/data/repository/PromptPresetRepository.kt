package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.LabeledText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved prompt/workflow presets. Legacy `bookmarks` are migrated transparently.
 * v1.9.3 keeps prompt-only presets compatible while allowing full workflow values,
 * tags and favorites without introducing a database dependency.
 */
class PromptPresetRepository(private val prefs: SharedPreferences) {
    private val lock = Any()
    private val _presets = MutableStateFlow(sort(read()))
    val presets: StateFlow<List<PromptPreset>> = _presets.asStateFlow()

    fun load(): List<PromptPreset> = _presets.value

    fun save(list: List<PromptPreset>) = synchronized(lock) {
        val sorted = sort(list)
        write(sorted)
        _presets.value = sorted
    }

    fun add(preset: PromptPreset): List<PromptPreset> = synchronized(lock) {
        val list = sort((listOf(preset) + _presets.value.filter { it.id != preset.id }).take(MAX_ITEMS))
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
        val list = sort(_presets.value.map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        })
        write(list)
        _presets.value = list
        list
    }

    fun toggleFavorite(id: String): List<PromptPreset> = synchronized(lock) {
        val list = sort(_presets.value.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
        write(list)
        _presets.value = list
        list
    }

    fun setTags(id: String, tags: List<String>): List<PromptPreset> = synchronized(lock) {
        val normalized = tags.map(String::trim).filter(String::isNotBlank).distinct().take(12)
        val list = sort(_presets.value.map { if (it.id == id) it.copy(tags = normalized) else it })
        write(list)
        _presets.value = list
        list
    }

    private fun sort(list: List<PromptPreset>): List<PromptPreset> = list.sortedWith(
        compareByDescending<PromptPreset> { it.favorite }
            .thenByDescending { it.lastUsedAt ?: 0L }
            .thenByDescending { it.createdAt }
    )

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
                values = strMap(o.optJSONObject("values")),
                randomSeed = if (o.has("randomSeed") && !o.isNull("randomSeed")) o.optBoolean("randomSeed") else null,
                tags = strList(o.optJSONArray("tags")),
                favorite = o.optBoolean("favorite", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<PromptPreset>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject()
                .put("id", b.id)
                .put("title", b.title)
                .put("time", b.createdAt)
                .put("workflowPath", b.workflowPath ?: "")
                .put("lastUsedAt", b.lastUsedAt ?: 0L)
                .put("values", JSONObject(b.values))
                .put("randomSeed", b.randomSeed ?: JSONObject.NULL)
                .put("tags", JSONArray().apply { b.tags.forEach { put(it) } })
                .put("favorite", b.favorite)
                .put("prompts", JSONArray().apply {
                    b.prompts.forEach { p -> put(JSONObject().put("label", p.label).put("value", p.value)) }
                }))
        }
        prefs.edit().putString(KEY, arr.toString()).remove(LEGACY_KEY).apply()
    }

    private fun strMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        o.keys().forEach { key -> result[key] = o.optString(key) }
        return result
    }

    private fun strList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }

    companion object {
        private const val KEY = "promptPresets"
        private const val LEGACY_KEY = "bookmarks"
        private const val MAX_ITEMS = 100
    }
}

data class PromptPreset(
    val id: String,
    val title: String,
    val prompts: List<LabeledText>,
    val workflowPath: String? = null,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    /** Full field snapshot for workflow presets. Empty means prompt-only legacy/prompt preset. */
    val values: Map<String, String> = emptyMap(),
    val randomSeed: Boolean? = null,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
) {
    val isWorkflowPreset: Boolean get() = workflowPath != null && values.isNotEmpty()
}
