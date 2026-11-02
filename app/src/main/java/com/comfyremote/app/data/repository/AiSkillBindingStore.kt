package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.AiSkillDef
import org.json.JSONArray
import org.json.JSONObject

/** Optional per-skill workflow-name/path patterns. Empty patterns mean the skill is available everywhere. */
class AiSkillBindingStore(private val prefs: SharedPreferences) {
    private val lock = Any()

    fun bindings(): Map<String, List<String>> = synchronized(lock) { read() }

    fun patterns(skillId: String): List<String> = bindings()[skillId].orEmpty()

    fun setPatterns(skillId: String, patterns: List<String>) = synchronized(lock) {
        val normalized = patterns.map(String::trim).filter(String::isNotBlank).distinct().take(12)
        val next = read().toMutableMap()
        if (normalized.isEmpty()) next.remove(skillId) else next[skillId] = normalized
        write(next)
    }

    fun delete(skillId: String) = synchronized(lock) {
        val next = read().toMutableMap()
        next.remove(skillId)
        write(next)
    }

    fun filter(skills: List<AiSkillDef>, workflowName: String, workflowPath: String): List<AiSkillDef> {
        val allBindings = bindings()
        val target = "$workflowName $workflowPath"
        return skills.filter { skill ->
            val patterns = allBindings[skill.id].orEmpty()
            patterns.isEmpty() || patterns.any { target.contains(it, ignoreCase = true) }
        }
    }

    private fun read(): Map<String, List<String>> = runCatching {
        val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        buildMap {
            root.keys().forEach { id ->
                val array = root.optJSONArray(id) ?: return@forEach
                val values = (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
                if (values.isNotEmpty()) put(id, values)
            }
        }
    }.getOrDefault(emptyMap())

    private fun write(map: Map<String, List<String>>) {
        val root = JSONObject()
        map.forEach { (id, patterns) ->
            root.put(id, JSONArray().apply { patterns.forEach(::put) })
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    companion object { private const val KEY = "aiSkillWorkflowBindingsV1" }
}
