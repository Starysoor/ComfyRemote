package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import org.json.JSONObject
import java.io.File

/**
 * Memory + disk cache of /object_info, keyed by server URL and invalidated
 * when the ComfyUI version changes. Callers never see the cache file.
 */
class ObjectInfoCache(private val cacheDir: File) {

    private var mem: JSONObject? = null
    private var memKey: String? = null

    suspend fun get(a: ComfyApi, serverVersion: String): JSONObject {
        val key = a.base
        mem?.let { if (memKey == key) return it }

        val cacheFile = File(cacheDir, "object_info_${key.hashCode()}.json")
        if (cacheFile.isFile) {
            runCatching {
                val o = JSONObject(cacheFile.readText())
                if (o.optString("version") == serverVersion) {
                    o.optJSONObject("data")?.let { data ->
                        mem = data; memKey = key
                        return data
                    }
                }
            }
        }
        val fresh = a.objectInfo()
        runCatching {
            cacheFile.writeText(JSONObject().put("version", serverVersion).put("data", fresh).toString())
        }
        mem = fresh; memKey = key
        return fresh
    }

    fun invalidate() {
        mem = null; memKey = null
    }
}
