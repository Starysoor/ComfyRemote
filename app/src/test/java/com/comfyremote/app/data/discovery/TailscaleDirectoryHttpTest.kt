package com.comfyremote.app.data.discovery

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises actual request construction without calling a real Tailscale account. */
class TailscaleDirectoryHttpTest {
    @Test
    fun readOnlyDirectoryRetrievesUnknownPeerIdsFromFixedOfficialHost() = runBlocking {
        var requestedHost = ""
        var requestedAuth = ""
        val fakeClient = OkHttpClient.Builder().addInterceptor { chain ->
            requestedHost = chain.request().url.host
            requestedAuth = chain.request().header("Authorization").orEmpty()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"devices":[{"name":"render.my-tailnet.ts.net","addresses":["100.109.4.7"]}]}"""
                        .toResponseBody()
                )
                .build()
        }.build()
        val discovered = TailscaleDeviceDirectory(fakeClient)
            .fetchProbeUrls("fake-read-token", 8188)
        assertEquals("api.tailscale.com", requestedHost)
        assertEquals("Bearer fake-read-token", requestedAuth)
        assertTrue(discovered.contains("http://100.109.4.7:8188"))
        assertTrue(discovered.contains("http://render.my-tailnet.ts.net:8188"))
    }

    @Test
    fun forbiddenTokenFailsWithoutUsingAnyUntrustedPeerURL() {
        val fakeClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("".toResponseBody())
                .build()
        }.build()
        assertThrows(TailscaleAccessDenied::class.java) {
            runBlocking {
                TailscaleDeviceDirectory(fakeClient).fetchProbeUrls("fake-token", 8188)
            }
        }
    }
}
