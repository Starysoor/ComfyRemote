package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.ServerEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class ServerHealth(
    val url: String,
    val online: Boolean,
    val version: String = "",
    val device: String = "",
    val vramGb: String = "",
    val running: Int = 0,
    val pending: Int = 0,
    val latencyMs: Long? = null,
    val error: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
) {
    val loadScore: Int get() = running * 100 + pending
}

/** Read-only health probes for saved ComfyUI servers. Generation still binds to the selected/original server. */
class ServerHealthRepository {
    suspend fun probe(url: String): ServerHealth {
        val start = System.nanoTime()
        return runCatching {
            val api = ComfyApi(url)
            val stats = api.systemStats()
            val queue = runCatching { api.queueSnapshot() }.getOrNull()
            ServerHealth(
                url = url,
                online = true,
                version = stats.version,
                device = stats.device,
                vramGb = stats.vramGb,
                running = queue?.runningPromptIds?.size ?: 0,
                pending = queue?.pendingPromptIds?.size ?: 0,
                latencyMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(0L),
            )
        }.getOrElse { error ->
            ServerHealth(
                url = url,
                online = false,
                latencyMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(0L),
                error = error.message ?: error.toString(),
            )
        }
    }

    suspend fun probeAll(servers: List<ServerEntry>): Map<String, ServerHealth> = coroutineScope {
        servers.distinctBy { it.url }.map { server ->
            async { server.url to probe(server.url) }
        }.awaitAll().toMap()
    }
}
