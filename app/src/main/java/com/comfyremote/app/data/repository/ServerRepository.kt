package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.ServerEntry
import org.json.JSONArray
import org.json.JSONObject

/** Owns the saved server list (aliases) and the last-connected address. */
class ServerRepository(private val prefs: SharedPreferences) {

    fun lastUrl(): String = prefs.getString("url", null) ?: "http://10.0.2.2:8188"

    fun saveLastUrl(url: String) {
        prefs.edit().putString("url", url).apply()
    }

    /** Loads saved servers; migrates the legacy string-array format, defaulting alias = url. */
    fun load(): List<ServerEntry> = runCatching {
        val v2 = prefs.getString(KEY, null)
        if (v2 != null) {
            val arr = JSONArray(v2)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ServerEntry(o.optString("name"), o.optString("url"))
            }
        }
        legacyList(prefs.getString("servers", null)).map { ServerEntry(it, it) }
    }.getOrDefault(emptyList())

    fun save(list: List<ServerEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun rename(url: String, alias: String): List<ServerEntry> {
        val list = load().map { if (it.url == url) it.copy(name = alias) else it }
        save(list)
        return list
    }

    fun remove(url: String): List<ServerEntry> {
        val list = load().filter { it.url != url }
        save(list)
        return list
    }

    /** Adds url if missing (keep at most 5), returning the updated list. */
    fun remember(url: String): List<ServerEntry> {
        val current = load()
        val list = if (current.any { it.url == url }) current
        else (current + ServerEntry(url, url)).takeLast(5)
        save(list)
        return list
    }

    private fun legacyList(raw: String?): List<String> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).map { a.optString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "serversV2"
    }
}
