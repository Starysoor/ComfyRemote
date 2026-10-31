package com.comfyremote.app.data.discovery

import com.comfyremote.app.ServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit

/** A ComfyUI server found on the local network. */
data class DiscoveredServer(val ip: String, val url: String) {
    fun asEntry(): ServerEntry = ServerEntry(ip, url)
}

sealed interface DiscoveryEvent {
    data class Progress(val scanned: Int, val total: Int) : DiscoveryEvent
    data class Found(val server: DiscoveredServer) : DiscoveryEvent
    data class Error(val message: String) : DiscoveryEvent
    data object Finished : DiscoveryEvent
}

/**
 * Discovers ComfyUI servers reachable from this device. Today that is a /24
 * subnet scan of the primary Wi-Fi interface; the Flow interface leaves room
 * for mDNS, Tailscale, VPN or IPv6 strategies later.
 */
class LanServerDiscovery(
    private val port: Int = 8188,
    private val probeTimeoutMs: Int = 400,
    private val verifyTimeoutMs: Long = 1500,
) {

    private val verifyClient = OkHttpClient.Builder()
        .connectTimeout(probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(verifyTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    fun discover(): Flow<DiscoveryEvent> = channelFlow {
        val prefix = withContext(Dispatchers.IO) { localSubnetPrefix() }
        if (prefix == null) {
            send(DiscoveryEvent.Error("not-on-wifi"))
            send(DiscoveryEvent.Finished)
            return@channelFlow
        }
        val total = 254
        var scanned = 0
        coroutineScope {
            (1..total).map { n ->
                async(Dispatchers.IO) {
                    val ip = "$prefix$n"
                    if (isComfyAt(ip)) {
                        val s = DiscoveredServer(ip, "http://$ip:$port")
                        send(DiscoveryEvent.Found(s))
                    }
                    synchronized(this@LanServerDiscovery) { scanned++ }
                    send(DiscoveryEvent.Progress(scanned, total))
                }
            }.awaitAll()
        }
        send(DiscoveryEvent.Finished)
    }.flowOn(Dispatchers.IO)

    /** Best-effort: prefers wlan0-style site-local IPv4 addresses. */
    private fun localSubnetPrefix(): String? {
        val e = NetworkInterface.getNetworkInterfaces() ?: return null
        var fallback: String? = null
        while (e.hasMoreElements()) {
            val nif = e.nextElement()
            if (!nif.isUp || nif.isLoopback) continue
            val addrs = nif.inetAddresses
            while (addrs.hasMoreElements()) {
                val a = addrs.nextElement()
                if (a is Inet4Address && a.isSiteLocalAddress) {
                    val prefix = a.hostAddress!!.substringBeforeLast('.') + '.'
                    if (nif.name.startsWith("wlan")) return prefix
                    fallback = fallback ?: prefix
                }
            }
        }
        return fallback
    }

    private suspend fun isComfyAt(ip: String): Boolean = runCatching {
        val socket = java.net.Socket()
        socket.connect(InetSocketAddress(ip, port), probeTimeoutMs)
        socket.close()
        verifyClient.newCall(
            okhttp3.Request.Builder().url("http://$ip:$port/system_stats").build()
        ).execute().use { r ->
            r.isSuccessful && JSONObject(r.body?.string() ?: "").optJSONObject("system")
                ?.has("comfyui_version") == true
        }
    }.getOrDefault(false)
}
