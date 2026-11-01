package com.comfyremote.app.data.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
