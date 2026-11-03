package com.comfyremote.app.data.discovery

import com.comfyremote.app.ServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** A ComfyUI server found on the local network. */
data class DiscoveredServer(val ip: String, val url: String) {
    fun asEntry(): ServerEntry = ServerEntry(ip, url)
}

sealed interface DiscoveryEvent {
    data class Progress(val scanned: Int, val total: Int) : DiscoveryEvent
    data class Found(val server: DiscoveredServer) : DiscoveryEvent
    data class Warning(val code: String) : DiscoveryEvent
    data class Error(val message: String) : DiscoveryEvent
    data object Finished : DiscoveryEvent
}

/** Returns the /24 prefix used by the current scanner, or null for malformed IPv4 text. */
internal fun ipv4SubnetPrefix(address: String): String? {
    val parts = address.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { it.toIntOrNull() ?: return null }
    if (octets.any { it !in 0..255 }) return null
    return "${octets[0]}.${octets[1]}.${octets[2]}."
}

/** Tailscale uses CGNAT 100.64.0.0/10, which is not a Java site-local address. */
internal fun isCgnatAddress(address: String): Boolean {
    val parts = address.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 100 && octets[1] in 64..127
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

    private data class Subnet(val prefix: String, val vpn: Boolean)

    fun discover(): Flow<DiscoveryEvent> = channelFlow {
        val subnet = withContext(Dispatchers.IO) { localSubnet() }
        if (subnet == null) {
            send(DiscoveryEvent.Error("not-on-wifi"))
            send(DiscoveryEvent.Finished)
            return@channelFlow
        }
        if (subnet.vpn) send(DiscoveryEvent.Warning("vpn-only"))
        val prefix = subnet.prefix
        val total = 254
        var scanned = 0
        coroutineScope {
            (1..total).map { n ->
                async(Dispatchers.IO) {
                    val ip = "$prefix$n"
                    if (isComfyAt(ip)) {
                        send(DiscoveryEvent.Found(DiscoveredServer(ip, "http://$ip:$port")))
                    }
                    val progress = synchronized(this@LanServerDiscovery) { ++scanned }
                    send(DiscoveryEvent.Progress(progress, total))
                }
            }.awaitAll()
        }
        send(DiscoveryEvent.Finished)
    }.flowOn(Dispatchers.IO)

    /** Best-effort: prefers Wi-Fi site-local addresses, then VPN/CGNAT interfaces. */
    private fun localSubnet(): Subnet? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        var siteFallback: Subnet? = null
        var vpnFallback: Subnet? = null
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val vpnInterface = network.name.lowercase().let {
                "tun" in it || "tailscale" in it || "ppp" in it || "vpn" in it
            }
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address !is Inet4Address) continue
                val host = address.hostAddress ?: continue
                val prefix = ipv4SubnetPrefix(host) ?: continue
                when {
                    address.isSiteLocalAddress -> {
                        val candidate = Subnet(prefix, vpn = false)
                        if (network.name.startsWith("wlan")) return candidate
                        siteFallback = siteFallback ?: candidate
                    }
                    isCgnatAddress(host) || vpnInterface -> {
                        vpnFallback = vpnFallback ?: Subnet(prefix, vpn = true)
                    }
                }
            }
        }
        return siteFallback ?: vpnFallback
    }

    private suspend fun isComfyAt(ip: String): Boolean = runCatching {
        val socket = java.net.Socket()
        try {
            socket.connect(InetSocketAddress(ip, port), probeTimeoutMs)
        } finally {
            runCatching { socket.close() }
        }
        verifyClient.newCall(
            okhttp3.Request.Builder().url("http://$ip:$port/system_stats").build()
        ).execute().use { response ->
            response.isSuccessful && JSONObject(response.body?.string() ?: "")
                .optJSONObject("system")?.has("comfyui_version") == true
        }
    }.getOrDefault(false)
}
