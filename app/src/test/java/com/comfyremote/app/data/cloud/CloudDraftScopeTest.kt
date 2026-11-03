package com.comfyremote.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDraftScopeTest {
    @Test
    fun draftScopeUsesProviderAndConfiguredEndpoint() {
        assertEquals(
            "cloud:custom:https://example.com/v1",
            cloudDraftScope("custom", "https://example.com/v1/"),
        )
    }

    @Test
    fun draftScopeDoesNotFallBackToALocalServer() {
        assertEquals("cloud:template", cloudDraftScope("template", ""))
    }
}
