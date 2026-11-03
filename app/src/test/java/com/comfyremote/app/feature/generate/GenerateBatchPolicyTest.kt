package com.comfyremote.app.feature.generate

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateBatchPolicyTest {

    @Test
    fun storyboardAlwaysSubmitsOneTask() {
        assertEquals(1, effectiveBatchCount(storyboard = true, configured = 4))
        assertEquals(1, effectiveBatchCount(storyboard = true, configured = 16))
        assertEquals(1, effectiveBatchCount(storyboard = true, configured = 0))
    }

    @Test
    fun normalWorkflowsKeepTheirClampedBatch() {
        assertEquals(4, effectiveBatchCount(storyboard = false, configured = 4))
        assertEquals(1, effectiveBatchCount(storyboard = false, configured = 0))
        assertEquals(16, effectiveBatchCount(storyboard = false, configured = 99))
    }
}
