package com.comfyremote.app.data.discovery

import com.comfyremote.app.data.security.SecretVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscalePeerSettingsStoreTest {
    private class TestSecrets : SecretVault {
        val stored = mutableMapOf<String, String>()
        override fun get(key: String): String? = stored[key]
        override fun put(key: String, value: String?) {
            if (value.isNullOrBlank()) stored.remove(key) else stored[key] = value
        }
    }

    @Test
    fun optInAndDisableNeverExposeADeviceListTokenInOtherSettings() {
        val secrets = TestSecrets()
        val store = TailscalePeerSettingsStore(secrets)
        assertFalse(store.configured())
        assertNull(store.token())
        store.saveToken("  test_read_only_access  ")
        assertTrue(store.configured())
        assertEquals("test_read_only_access", store.token())
        assertEquals(1, secrets.stored.size)
        store.clear()
        assertFalse(store.configured())
        assertNull(store.token())
    }

    @Test
    fun tokenIsOnlySentToFixedOfficialHttpsApi() {
        val request = tailscaleDevicesRequest("sample-token")
        assertEquals("https", request.url.scheme)
        assertEquals("api.tailscale.com", request.url.host)
        assertEquals("/api/v2/tailnet/-/devices", request.url.encodedPath)
        assertEquals("Bearer sample-token", request.header("Authorization"))
        assertEquals("GET", request.method)
    }
}
