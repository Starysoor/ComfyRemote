package com.comfyremote.app.data.discovery

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleDeviceDirectoryTest {
    @Test
    fun unknownPeersFromDifferentTailnetSubnetsAreProbedByIpAndMagicDns() {
        val payload = JSONObject(
            """{"devices":[
              {"name":"studio.example-tail.ts.net","addresses":["100.111.2.3","fd7a:115c::abcd"],"authorized":true},
              {"name":"render.example-tail.ts.net","addresses":["100.121.45.67"],"authorized":true},
              {"name":"unapproved.example-tail.ts.net","addresses":["100.90.1.2"],"authorized":false}
            ]}"""
        )
        val urls = tailscaleDeviceProbeUrls(payload, 8188)
        assertEquals(4, urls.size)
        assertTrue(urls.contains("http://100.111.2.3:8188"))
        assertTrue(urls.contains("http://100.121.45.67:8188"))
        assertTrue(urls.contains("http://studio.example-tail.ts.net:8188"))
        assertFalse(urls.any { it.contains("100.90.1.2") })
    }

    @Test
    fun maliciousDeviceNamesAndPublicAddressesAreNotUsedAsProbes() {
        val payload = JSONObject(
            """{"devices":[
              {"name":"evil.com:443/steal","addresses":["8.8.8.8","100.128.0.1"]},
              {"name":"studio.tail.ts.net","addresses":["100.64.22.4","100.64.22.4"]}
            ]}"""
        )
        assertEquals(
            listOf("http://100.64.22.4:8188", "http://studio.tail.ts.net:8188"),
            tailscaleDeviceProbeUrls(payload),
        )
    }

    @Test
    fun aMissingDevicesCollectionDoesNotInventPeers() {
        assertTrue(tailscaleDeviceProbeUrls(JSONObject("{}")).isEmpty())
    }
}
