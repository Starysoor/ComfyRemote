package com.comfyremote.app.data.generation

import com.comfyremote.app.data.cloud.CloudProvider
import com.comfyremote.app.data.cloud.CloudResult
import com.comfyremote.app.data.cloud.CloudTaskResult
import com.comfyremote.app.data.cloud.CloudWorkflowSpec
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.historyCancelled
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudWorkflowBackendTest {
    @Test
    fun transientPollFailuresAreRetried() = runBlocking {
        val provider = FlakyProvider(failures = 2)
        val backend = CloudWorkflowBackend(
            repository = provider,
            workflowId = "workflow",
            retryCount = 2,
            retryDelayMs = 1,
        )

        val entry = backend.historyEntry("task-1")

        assertNotNull(entry)
        assertEquals(3, provider.calls)
    }

    @Test
    fun pollFailuresStopAtTheRetryBudget() = runBlocking {
        val provider = FlakyProvider(failures = 3)
        val backend = CloudWorkflowBackend(
            repository = provider,
            workflowId = "workflow",
            retryCount = 2,
            retryDelayMs = 1,
        )

        val error = runCatching { backend.historyEntry("task-1") }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(3, provider.calls)
    }

    @Test
    fun providerCancellationBecomesACancelledHistoryEntry() = runBlocking {
        val backend = CloudWorkflowBackend(
            repository = FlakyProvider(failures = 0, finalStatus = "CANCELED"),
            workflowId = "workflow",
            providerLabel = "Test Provider",
        )

        val entry = backend.historyEntry("task-1")

        assertNotNull(entry)
        assertTrue(historyCancelled(entry!!))
    }

    @Test
    fun providerErrorsUseTheRealProviderLabel() = runBlocking {
        val backend = CloudWorkflowBackend(
            repository = FlakyProvider(failures = 0, finalStatus = "FAILED", message = "boom"),
            workflowId = "workflow",
            providerLabel = "Test Provider",
        )

        val entry = backend.historyEntry("task-1")
        val data = entry!!.getJSONObject("status")
            .getJSONArray("messages")
            .getJSONArray(0)
            .getJSONObject(1)

        assertEquals("Test Provider", data.getString("node_type"))
        assertEquals("boom", data.getString("exception_message"))
        assertFalse(data.getString("exception_message").contains("AutoDL"))
    }

    private class FlakyProvider(
        private val failures: Int,
        private val finalStatus: String = "SUCCESS",
        private val message: String = "",
    ) : CloudProvider {
        var calls = 0
            private set

        override val id: String = "test"
        override val label: String = "Test"
        override val pathPrefix: String = "test:"
        override val baseUrl: String = "https://example.com"

        override fun configured(): Boolean = true

        override suspend fun catalog(force: Boolean): List<CloudWorkflowSummary> = emptyList()

        override suspend fun detail(workflowId: String): CloudWorkflowSpec =
            CloudWorkflowSpec(
                id = workflowId,
                name = workflowId,
                kind = "image",
                description = "",
                rules = emptyList(),
            )

        override suspend fun submit(workflowId: String, body: JSONObject): String = "task-1"

        override suspend fun result(taskId: String): CloudTaskResult {
            calls++
            if (calls <= failures) throw IOException("transient")
            return CloudTaskResult(
                taskId = taskId,
                status = finalStatus,
                results = if (finalStatus == "SUCCESS") {
                    listOf(CloudResult("https://example.com/out.png", "image", "png", "output"))
                } else {
                    emptyList()
                },
                message = message,
                durationSeconds = 0,
            )
        }

        override suspend fun download(url: String): ByteArray = ByteArray(0)
    }
}
