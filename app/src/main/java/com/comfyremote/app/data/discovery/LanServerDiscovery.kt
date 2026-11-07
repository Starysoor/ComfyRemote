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
import java.net.URI
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

/** Validates known server URLs before any direct HTTPS/HTTP health probes. */
internal fun savedComfyProbeUrls(saved: List<String>): List<String> =
    saved.mapNotNull { candidate ->
        val raw = candidate.trim().trimEnd('/')
        val uri = runCatching { URI(raw) }.getOrNull() ?: return@mapNotNull null
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null
        ) null else raw
    }.distinct().take(8)

/**
 * Discovers nearby LAN ComfyUI as well as explicitly remembered servers.
 * Tailnet peers can be on arbitrary CGNAT subnets, so remembered IP/MagicDNS
 * servers are probed directly rather than scanning the entire 100.64.0.0/10.
 */
class LanServerDiscovery(
    private val port: Int = 8188,
    private val probeTimeoutMs: Int = 400,
    private val verifyTimeoutMs: Long = 1500,
    private val knownUrls: () -> List<String> = { emptyList() },
) {
    private val verifyClient = OkHttpClient.Builder()
        .connectTimeout(probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(verifyTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private data class Subnet(val prefix: String, val vpn: Boolean)
    private data class Probe(val host: String, val url: String, val remembered: Boolean)

    fun discover(): Flow<DiscoveryEvent> = channelFlow {
        val subnets = withContext(Dispatchers.IO) { localSubnets() }
        val remembered = savedComfyProbeUrls(knownUrls())
        if (subnets.isEmpty() && remembered.isEmpty()) {
            send(DiscoveryEvent.Error("not-on-wifi"))
            send(DiscoveryEvent.Finished)
            return@channelFlow
        }
        // Even when VPN is connected, the subnet scanner cannot enumerate
        // unknown peers on arbitrary Tailnet IP ranges.
        if (subnets.any { it.vpn }) send(DiscoveryEvent.Warning("vpn-only"))
        val probes = (
            remembered.map { url ->
                Probe(URI(url).host, url, remembered = true)
            } + subnets.flatMap { subnet ->
                (1..254).map { n ->
                    val host = "${subnet.prefix}$n"
                    Probe(host, "http://$host:$port", remembered = false)
                }
            }
        ).distinctBy { it.url }
        var scanned = 0
        // Bound concurrency; 508 simultaneous connections can exhaust sockets.
        coroutineScope {
            probes.chunked(24).forEach { batch ->
                batch.map { probe ->
                    async(Dispatchers.IO) {
                        val found = if (probe.remembered) isComfyAtUrl(probe.url)
                            else isComfyAt(probe.host)
                        if (found) send(DiscoveryEvent.Found(DiscoveredServer(probe.host, probe.url)))
                        val progress = synchronized(this@LanServerDiscovery) { ++scanned }
                        send(DiscoveryEvent.Progress(progress, probes.size))
                    }
                }.awaitAll()
            }
        }
        send(DiscoveryEvent.Finished)
    }.flowOn(Dispatchers.IO)

    /** Keep one local LAN and one VPN subnet, while probing known Tailnet peers by URL. */
    private fun localSubnets(): List<Subnet> {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        var wifi: Subnet? = null
        var lan: Subnet? = null
        var vpn: Subnet? = null
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
                    vpnInterface || isCgnatAddress(host) -> vpn = vpn ?: Subnet(prefix, vpn = true)
                    address.isSiteLocalAddress && network.name.startsWith("wlan") ->
                        wifi = wifi ?: Subnet(prefix, vpn = false)
                    address.isSiteLocalAddress -> lan = lan ?: Subnet(prefix, vpn = false)
                }
            }
        }
        return listOfNotNull(wifi ?: lan, vpn).distinctBy { it.prefix }
    }

    /** Saved MagicDNS, private/VPN IP, and reverse-proxy URL health checks. */
    private suspend fun isComfyAtUrl(baseUrl: String): Boolean = runCatching {
        verifyClient.newCall(
            okhttp3.Request.Builder().url(baseUrl.trimEnd('/') + "/system_stats").build()
        ).execute().use { response ->
            response.isSuccessful &&
                JSONObject(response.body?.string() ?: "")
                    .optJSONObject("system")?.has("comfyui_version") == true
        }
    }.getOrDefault(false)

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
