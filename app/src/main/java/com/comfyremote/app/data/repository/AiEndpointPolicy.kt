package com.comfyremote.app.data.repository

/** Flags HTTP AI endpoints that would expose credentials/content beyond the local machine. */
object AiEndpointPolicy {

    fun isInsecureRemote(rawUrl: String): Boolean {
        val url = rawUrl.trim().lowercase()
        if (!url.startsWith("http://")) return false
        val authority = url.removePrefix("http://").substringBefore('/')
        val host = when {
            authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
            else -> authority.substringBefore(':')
        }
        if (host.isBlank()) return false
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return false
        if (host == "127.0.0.1" || host.startsWith("127.")) return false
        if (host.startsWith("10.") || host.startsWith("192.168.")) return false
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: return true
            if (second in 16..31) return false
        }
        return true
    }
}
