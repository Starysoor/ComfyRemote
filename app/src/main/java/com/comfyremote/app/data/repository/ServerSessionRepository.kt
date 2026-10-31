package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.SysInfo

/** Holds the currently connected ComfyUI session (api client + server facts). */
class ServerSessionRepository {

    var api: ComfyApi? = null
        private set
    var version: String = ""
        private set
    var device: String = ""
        private set
    var url: String = ""
        private set

    val connected: Boolean get() = api != null

    suspend fun connect(url: String): SysInfo {
        val a = ComfyApi(url)
        val info = a.systemStats()
        api = a
        version = info.version
        device = info.device
        this.url = url.trim().removeSuffix("/")
        return info
    }

    fun require(): ComfyApi =
        api ?: throw ComfySessionClosedException()

    fun clear() {
        api = null
        version = ""
        device = ""
        url = ""
    }
}

class ComfySessionClosedException :
    com.comfyremote.app.ComfyException("Not connected to a ComfyUI server")
