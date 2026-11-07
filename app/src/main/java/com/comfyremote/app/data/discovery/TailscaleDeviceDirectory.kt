package com.comfyremote.app.data.discovery

import com.comfyremote.app.data.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Optional, opt-in Tailnet device discovery. Credentials remain in the existing
 * SecretVault; no Tailscale token is ever sent to a discovered ComfyUI host.
 * Only the official read-only GET /devices endpoint is called.
 */
class TailscalePeerSettingsStore(private val secrets: SecretVault) {
    fun configured(): Boolean = !secrets.get(TOKEN_KEY).isNullOrBlank()
    fun token(): String? = secrets.get(TOKEN_KEY)?.takeIf(String::isNotBlank)
    fun saveToken(token: String) {
        val clean = token.trim()
        require(clean.isNotBlank() && !clean.contains('\n') && !clean.contains('\r')) {
            "Invalid Tailscale token"
        }
        secrets.put(TOKEN_KEY, clean)
    }
    fun clear() = secrets.put(TOKEN_KEY, null)

    companion object { private const val TOKEN_KEY = "tailscaleDevicesReadToken" }
}

internal class TailscaleAccessDenied : IOException("Tailscale device-list access denied")

/** Turn the official tailnet device-list response into a bounded set of probe URLs. */
internal fun tailscaleDeviceProbeUrls(root: JSONObject, port: Int = 8188): List<String> {
    require(port in 1..65535)
    val devices = root.optJSONArray("devices") ?: return emptyList()
    val urls = LinkedHashSet<String>()
    for (index in 0 until minOf(devices.length(), 128)) {
        val device = devices.optJSONObject(index) ?: continue
        if (device.has("authorized") && !device.optBoolean("authorized")) continue
        val addresses = device.optJSONArray("addresses")
        if (addresses != null) {
            for (i in 0 until addresses.length()) {
                val address = addresses.optString(i)
                if (isCgnatAddress(address)) urls.add("http://$address:$port")
            }
        }
        val dns = device.optString("name").lowercase().trimEnd('.')
        if (dns.length in 8..253 && dns.endsWith(".ts.net") &&
            dns.matches(Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+"))
        ) {
            urls.add("http://$dns:$port")
        }
    }
    return urls.take(256)
}

/** Only the official HTTPS API receives the token, never an address from the directory. */
internal fun tailscaleDevicesRequest(token: String): Request =
    Request.Builder()
        .url("https://api.tailscale.com/api/v2/tailnet/-/devices?fields=default")
        .get()
        .header("Authorization", "Bearer $token")
        .build()

/**
 * Read-only endpoint on a fixed HTTPS authority. Redirects are disabled so a
 * bearer token can never follow a redirect to an attacker-controlled host.
 */
class TailscaleDeviceDirectory(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun fetchProbeUrls(token: String, port: Int): List<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext emptyList()
        val request = tailscaleDevicesRequest(token)
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401, 403 -> throw TailscaleAccessDenied()
            }
            if (!response.isSuccessful) throw IOException("Tailscale directory returned HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty Tailscale device list")
            val maxBytes = 2 * 1024 * 1024
            if (body.contentLength() > maxBytes) throw IOException("Tailscale device list is too large")
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("Tailscale device list is too large")
                    output.write(buffer, 0, n)
                }
            }
            tailscaleDeviceProbeUrls(JSONObject(output.toString("UTF-8")), port)
        }
    }
}
