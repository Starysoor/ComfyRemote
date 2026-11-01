package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight recent/favorite metadata for workflow presentation only. */
class WorkflowUsageRepository(private val prefs: SharedPreferences) {

    fun recentPaths(): List<String> = readRecent().sortedByDescending { it.second }.map { it.first }
    fun favorites(): Set<String> = readStringSet(FAVORITES_KEY)

    fun markOpened(path: String) {
        val now = System.currentTimeMillis()
        val next = (readRecent().filter { it.first != path } + (path to now))
            .sortedByDescending { it.second }
            .take(MAX_RECENT)
        val arr = JSONArray()
        next.forEach { (p, t) -> arr.put(JSONObject().put("path", p).put("time", t)) }
        prefs.edit().putString(RECENT_KEY, arr.toString()).apply()
    }

    fun toggleFavorite(path: String): Set<String> {
        val next = favorites().toMutableSet().apply {
            if (!add(path)) remove(path)
        }
        prefs.edit().putString(FAVORITES_KEY, JSONArray(next.toList()).toString()).apply()
        return next
    }

    private fun readRecent(): List<Pair<String, Long>> = runCatching {
        val arr = JSONArray(prefs.getString(RECENT_KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            o.optString("path").takeIf { it.isNotBlank() }?.let { it to o.optLong("time") }
        }
    }.getOrDefault(emptyList())

    private fun readStringSet(key: String): Set<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }.getOrDefault(emptySet())

    companion object {
        private const val RECENT_KEY = "workflowRecentV1"
        private const val FAVORITES_KEY = "workflowFavoritesV1"
        private const val MAX_RECENT = 20
    }
}
