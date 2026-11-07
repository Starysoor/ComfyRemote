package com.comfyremote.app.data.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanServerDiscoveryTest {
    @Test
    fun validIpv4ProducesSlash24Prefix() {
        assertEquals("192.168.1.", ipv4SubnetPrefix("192.168.1.23"))
        assertEquals("10.0.2.", ipv4SubnetPrefix("10.0.2.15"))
    }

    @Test
    fun malformedOrOutOfRangeIpv4IsRejected() {
        assertNull(ipv4SubnetPrefix("192.168.1"))
        assertNull(ipv4SubnetPrefix("192.168.1.999"))
        assertNull(ipv4SubnetPrefix("hello"))
    }

    @Test
    fun cgnatRangeIsRecognisedForVpnDiscovery() {
        assertTrue(isCgnatAddress("100.64.0.1"))
        assertTrue(isCgnatAddress("100.127.255.254"))
        assertFalse(isCgnatAddress("100.63.0.1"))
        assertFalse(isCgnatAddress("100.128.0.1"))
        assertFalse(isCgnatAddress("192.168.1.2"))
    }
    @Test
    fun rememberedTailnetAndMagicDnsCanBeProbedAcrossSubnets() {
        val urls = savedComfyProbeUrls(
            listOf(
                "http://100.105.22.9:8188",
                "http://desktop.tail123.ts.net:8188/",
                "https://my-comfy.example.com/proxy/",
                "http://100.105.22.9:8188",
                "ftp://host:8188",
                "http://secret:password@server:8188",
                "not-a-url",
            )
        )
        assertEquals(
            listOf(
                "http://100.105.22.9:8188",
                "http://desktop.tail123.ts.net:8188",
                "https://my-comfy.example.com/proxy",
            ),
            urls,
        )
    }

    @Test
    fun savedTailnetHostsOutsideThePhoneVpnSubnetAreAlwaysIncluded() {
        val peers = listOf(
            "http://100.124.90.22:8188",
            "http://laptop.my-tailnet.ts.net:8188",
        )
        val probes = discoveryProbePlan(peers, listOf("192.168.2.", "100.65.10."), 8188)
        assertEquals(2 + 254 * 2, probes.size)
        assertTrue(probes.any { it.remembered && it.url == peers[0] })
        assertTrue(probes.any { it.remembered && it.url == peers[1] })
        assertFalse(probes.any { it.url == "http://100.64.0.1:8188" })
    }

}
