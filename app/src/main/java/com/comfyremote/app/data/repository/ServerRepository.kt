package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.ServerEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Owns the saved server list (aliases) and the last-connected address. */
class ServerRepository(private val prefs: SharedPreferences) {
    private val _servers = MutableStateFlow(readServers())
    val servers: StateFlow<List<ServerEntry>> = _servers.asStateFlow()

    fun lastUrl(): String = savedLastUrl() ?: DEFAULT_URL
    fun savedLastUrl(): String? = prefs.getString("url", null)?.takeIf { it.isNotBlank() }

    fun saveLastUrl(url: String) {
        prefs.edit().putString("url", url).apply()
    }

    fun load(): List<ServerEntry> = _servers.value

    fun save(list: List<ServerEntry>) {
        val normalized = list.distinctBy { it.url }
        val arr = JSONArray()
        normalized.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _servers.value = normalized
    }

    fun rename(url: String, alias: String): List<ServerEntry> {
        val list = _servers.value.map { if (it.url == url) it.copy(name = alias) else it }
        save(list)
        return list
    }

    fun remove(url: String): List<ServerEntry> {
        val list = _servers.value.filter { it.url != url }
        save(list)
        return list
    }

    /** Adds url if missing (keep at most 8), returning the updated list. */
    fun remember(url: String): List<ServerEntry> {
        val current = _servers.value
        val existing = current.firstOrNull { it.url == url }
        val list = ((current - listOfNotNull(existing)) + (existing ?: ServerEntry(url, url))).takeLast(8)
        save(list)
        return list
    }

    private fun readServers(): List<ServerEntry> = runCatching {
        val v2 = prefs.getString(KEY, null)
        if (v2 != null) {
            val arr = JSONArray(v2)
            return@runCatching (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ServerEntry(o.optString("name"), o.optString("url"))
            }
        }
        legacyList(prefs.getString("servers", null)).map { ServerEntry(it, it) }
    }.getOrDefault(emptyList())

    private fun legacyList(raw: String?): List<String> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).map { a.optString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "serversV2"
        const val DEFAULT_URL = "http://10.0.2.2:8188"
    }
}
