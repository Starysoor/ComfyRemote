package com.comfyremote.app.data.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSpendIdentityTest {
    @Test
    fun equalRemoteIdsOnDifferentProvidersOrEndpointsAreNotDuplicates() {
        val entry = CloudSpendEntry(
            workflowName = "first", providerId = "siliconflow",
            points = 2, remoteTaskId = "job-001", endpoint = "https://one.example",
        )
        assertTrue(isSameBilledTask(entry, "job-001", "siliconflow", "https://one.example"))
        assertFalse(isSameBilledTask(entry, "job-001", "autodl-model", "https://one.example"))
        assertFalse(isSameBilledTask(entry, "job-001", "siliconflow", "https://two.example"))
        assertFalse(isSameBilledTask(entry, "job-002", "siliconflow", "https://one.example"))
    }
}
