package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.LabeledText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Saved prompt/workflow presets. Legacy `bookmarks` are migrated transparently. */
class PromptPresetRepository(private val prefs: SharedPreferences) {
    private val lock = Any()
    private val _presets = MutableStateFlow(sort(read()))
    val presets: StateFlow<List<PromptPreset>> = _presets.asStateFlow()

    fun load(): List<PromptPreset> = _presets.value

    fun save(list: List<PromptPreset>) = synchronized(lock) {
        val sorted = sort(list.distinctBy { it.id })
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
        update { preset -> if (preset.id == id) preset.copy(lastUsedAt = System.currentTimeMillis()) else preset }
    }

    fun toggleFavorite(id: String): List<PromptPreset> = synchronized(lock) {
        update { preset -> if (preset.id == id) preset.copy(favorite = !preset.favorite) else preset }
    }

    fun setTags(id: String, tags: List<String>): List<PromptPreset> = synchronized(lock) {
        val normalized = normalizeTags(tags)
        update { preset -> if (preset.id == id) preset.copy(tags = normalized) else preset }
    }

    fun updateMeta(id: String, title: String, tags: List<String>, folder: String): List<PromptPreset> = synchronized(lock) {
        val cleanTitle = title.trim().take(80)
        val cleanFolder = folder.trim().take(40)
        val normalized = normalizeTags(tags)
        update { preset ->
            if (preset.id == id) preset.copy(
                title = cleanTitle.ifBlank { preset.title },
                tags = normalized,
                folder = cleanFolder,
            ) else preset
        }
    }

    fun exportJson(): String = synchronized(lock) { encode(_presets.value).toString(2) }

    /** Returns the number of imported presets. Existing ids are replaced by imported copies. */
    fun importJson(raw: String): Int = synchronized(lock) {
        val imported = parse(JSONArray(raw)).distinctBy { it.id }.take(MAX_ITEMS)
        if (imported.isEmpty()) return@synchronized 0
        val ids = imported.mapTo(hashSetOf()) { it.id }
        val merged = sort((imported + _presets.value.filterNot { it.id in ids }).take(MAX_ITEMS))
        write(merged)
        _presets.value = merged
        imported.size
    }

    private fun update(transform: (PromptPreset) -> PromptPreset): List<PromptPreset> {
        val list = sort(_presets.value.map(transform))
        write(list)
        _presets.value = list
        return list
    }

    private fun normalizeTags(tags: List<String>) = tags
        .map(String::trim).filter(String::isNotBlank).distinct().take(12)

    private fun sort(list: List<PromptPreset>): List<PromptPreset> = list.sortedWith(
        compareByDescending<PromptPreset> { it.favorite }
            .thenBy { it.folder.lowercase() }
            .thenByDescending { it.lastUsedAt ?: 0L }
            .thenByDescending { it.createdAt }
    )

    private fun read(): List<PromptPreset> = runCatching {
        val raw = prefs.getString(KEY, null) ?: prefs.getString(LEGACY_KEY, null)
            ?: return@runCatching emptyList()
        parse(JSONArray(raw)).distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun parse(arr: JSONArray): List<PromptPreset> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val prompts = mutableListOf<LabeledText>()
        val pa = o.optJSONArray("prompts")
        if (pa != null) for (j in 0 until pa.length()) {
            val po = pa.optJSONObject(j) ?: continue
            prompts.add(LabeledText(po.optString("label"), po.optString("value")))
        }
        val id = o.optString("id")
        if (id.isBlank()) return@mapNotNull null
        PromptPreset(
            id = id,
            title = o.optString("title").ifBlank { "Preset" },
            prompts = prompts,
            workflowPath = o.optString("workflowPath").ifEmpty { null },
            createdAt = o.optLong("time", o.optLong("createdAt", System.currentTimeMillis())),
            lastUsedAt = o.optLong("lastUsedAt").takeIf { it > 0 },
            values = strMap(o.optJSONObject("values")),
            randomSeed = if (o.has("randomSeed") && !o.isNull("randomSeed")) o.optBoolean("randomSeed") else null,
            tags = strList(o.optJSONArray("tags")),
            favorite = o.optBoolean("favorite", false),
            folder = o.optString("folder"),
        )
    }

    private fun encode(list: List<PromptPreset>): JSONArray = JSONArray().apply {
        list.forEach { preset ->
            put(JSONObject()
                .put("id", preset.id)
                .put("title", preset.title)
                .put("time", preset.createdAt)
                .put("workflowPath", preset.workflowPath ?: "")
                .put("lastUsedAt", preset.lastUsedAt ?: 0L)
                .put("values", JSONObject(preset.values))
                .put("randomSeed", preset.randomSeed ?: JSONObject.NULL)
                .put("tags", JSONArray().apply { preset.tags.forEach { put(it) } })
                .put("favorite", preset.favorite)
                .put("folder", preset.folder)
                .put("prompts", JSONArray().apply {
                    preset.prompts.forEach { p -> put(JSONObject().put("label", p.label).put("value", p.value)) }
                }))
        }
    }

    private fun write(list: List<PromptPreset>) {
        prefs.edit().putString(KEY, encode(list).toString()).remove(LEGACY_KEY).apply()
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
        private const val MAX_ITEMS = 150
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
    val folder: String = "",
) {
    val isWorkflowPreset: Boolean get() = workflowPath != null && values.isNotEmpty()
}
