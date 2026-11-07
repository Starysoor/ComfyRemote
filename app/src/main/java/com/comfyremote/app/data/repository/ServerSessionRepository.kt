package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.SysInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** Stable facts about the currently connected ComfyUI server. */
data class ServerSession(
    val url: String,
    val version: String,
    val device: String,
    val vramGb: String,
)

/** An atomic latest-wins guard; stale probes must not commit sessions. */
internal class LatestConnectionAttempt {
    private val lock = Any()
    private var serial = 0L

    fun start(): Long = synchronized(lock) { ++serial }

    fun commit(ticket: Long, update: () -> Unit): Boolean = synchronized(lock) {
        if (ticket != serial) false else {
            update()
            true
        }
    }

    fun invalidate(update: () -> Unit) = synchronized(lock) {
        ++serial
        update()
    }
}

/** Holds the currently connected ComfyUI session (api client + server facts). */
class ServerSessionRepository {
    private val attempts = LatestConnectionAttempt()
    private val _session = MutableStateFlow<ServerSession?>(null)
    val session: StateFlow<ServerSession?> = _session.asStateFlow()

    @Volatile var api: ComfyApi? = null
        private set

    val connected: Boolean get() = api != null
    val version: String get() = _session.value?.version.orEmpty()
    val device: String get() = _session.value?.device.orEmpty()
    val url: String get() = _session.value?.url.orEmpty()

    suspend fun connect(url: String, auto: Boolean = false): SysInfo {
        val clean = url.trim().removeSuffix("/")
        val ticket = attempts.start()
        // Auto-recovery must not block the UI on a hung /system_stats request.
        val info = if (auto) {
            ComfyApi(clean, ComfyApi.probeClient).systemStats()
        } else {
            ComfyApi(clean).systemStats()
        }
        // A slow auto-probe must not overwrite a newer manual connection/disconnect.
        val committed = attempts.commit(ticket) {
            // Generations need the long-timeout client after the short probe.
            api = ComfyApi(clean)
            _session.value = ServerSession(
                url = clean,
                version = info.version,
                device = info.device,
                vramGb = info.vramGb,
            )
        }
        if (!committed) throw CancellationException("Connection superseded by a newer request")
        return info
    }

    fun require(): ComfyApi = api ?: throw ComfySessionClosedException()

    suspend fun deleteFromQueue(promptId: String) = require().deleteFromQueue(promptId)
    suspend fun interrupt() = require().interrupt()

    fun clear() {
        attempts.invalidate {
            api = null
            _session.value = null
        }
    }
}

class ComfySessionClosedException :
    com.comfyremote.app.ComfyException("Not connected to a ComfyUI server")
