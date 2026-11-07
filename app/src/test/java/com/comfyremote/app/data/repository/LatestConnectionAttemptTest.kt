package com.comfyremote.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestConnectionAttemptTest {
    @Test
    fun slowAutoProbeCannotOverrideAFasterManualConnection() {
        val guard = LatestConnectionAttempt()
        var active = ""
        val auto = guard.start()
        val manual = guard.start()
        assertTrue(guard.commit(manual) { active = "manual" })
        assertFalse(guard.commit(auto) { active = "stale auto" })
        assertEquals("manual", active)
    }

    @Test
    fun disconnectInvalidatesAnyInflightProbe() {
        val guard = LatestConnectionAttempt()
        var active = "old"
        val ticket = guard.start()
        guard.invalidate { active = "" }
        assertFalse(guard.commit(ticket) { active = "stale" })
        assertEquals("", active)
    }
}
