package com.comfyremote.app.data.cloud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProviderRegistryTest {

    @Test
    fun enabledProvidersDriveDiscoveryAndVisibility() {
        val openai = fake("openai", "OpenAI", configured = true)
        val autodl = fake("autodl", "AutoDL.Art", configured = false)
        val registry = CloudProviderRegistry(
            providers = listOf(openai, autodl),
            isProviderEnabled = { provider -> provider.id != "autodl" },
        )

        assertEquals(listOf("openai"), registry.enabled.map { it.id })
        assertTrue(registry.isEnabled("openai"))
        assertFalse(registry.isEnabled("autodl"))
        assertTrue(registry.info().first { it.id == "autodl" }.let { !it.enabled })
    }

    @Test
    fun pathLookupNeverCrossesProviderPrefixes() {
        val model = fake("autodl_model", "AutoDL Models", configured = true, prefix = "autodl-model:")
        val workflow = fake("autodl", "AutoDL.Art", configured = true, prefix = "autodl:")
        val registry = CloudProviderRegistry(listOf(workflow, model))

        assertEquals("autodl", registry.forPath("autodl:workflow")?.id)
        assertEquals("autodl_model", registry.forPath("autodl-model:gpt-image-2")?.id)
    }

    private fun fake(
        id: String,
        label: String,
        configured: Boolean,
        prefix: String = "$id:",
    ): CloudProvider = object : CloudProvider {
        override val id = id
        override val label = label
        override val pathPrefix = prefix
        override val baseUrl = "https://example.com"
        override fun configured(): Boolean = configured
        override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> = emptyList()
        override suspend fun detail(workflowId: String): CloudWorkflowSpec =
            CloudWorkflowSpec(
                id = workflowId,
                name = workflowId,
                kind = "image",
                description = "",
                rules = emptyList(),
            )
        override suspend fun submit(workflowId: String, body: JSONObject): String = "task"
        override suspend fun result(taskId: String): CloudTaskResult =
            CloudTaskResult(taskId, "SUCCESS", emptyList(), "", 0)
        override suspend fun download(url: String): ByteArray = ByteArray(0)
    }
}
