package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.SysInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable facts about the currently connected ComfyUI server. */
data class ServerSession(
    val url: String,
    val version: String,
    val device: String,
    val vramGb: String,
)

/** Holds the currently connected ComfyUI session (api client + server facts). */
class ServerSessionRepository {
    private val _session = MutableStateFlow<ServerSession?>(null)
    val session: StateFlow<ServerSession?> = _session.asStateFlow()

    var api: ComfyApi? = null
        private set

    val connected: Boolean get() = api != null
    val version: String get() = _session.value?.version.orEmpty()
    val device: String get() = _session.value?.device.orEmpty()
    val url: String get() = _session.value?.url.orEmpty()

    suspend fun connect(url: String): SysInfo {
        val clean = url.trim().removeSuffix("/")
        val a = ComfyApi(clean)
        val info = a.systemStats()
        api = a
        _session.value = ServerSession(
            url = clean,
            version = info.version,
            device = info.device,
            vramGb = info.vramGb,
        )
        return info
    }

    fun require(): ComfyApi = api ?: throw ComfySessionClosedException()

    suspend fun deleteFromQueue(promptId: String) = require().deleteFromQueue(promptId)
    suspend fun interrupt() = require().interrupt()

    fun clear() {
        api = null
        _session.value = null
    }
}

class ComfySessionClosedException :
    com.comfyremote.app.ComfyException("Not connected to a ComfyUI server")
