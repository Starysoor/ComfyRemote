package com.comfyremote.app.data.cloud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CloudProviderTaskStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = CloudProviderTaskStore(File(temp.root, "cloud_tasks"))

    @Test
    fun pendingContextSurvivesANewStoreInstance() {
        val first = store()
        first.savePending("autodl_model", "task-1", JSONObject().put("family", "kling"))

        val restored = store().pending("autodl_model", "task-1")

        assertNotNull(restored)
        assertEquals("kling", restored!!.getString("family"))
    }

    @Test
    fun completedResultsSurviveAndKeepDataUrls() {
        val result = CloudTaskResult(
            taskId = "local-1",
            status = "SUCCESS",
            results = listOf(
                CloudResult("https://example.com/out.png", "image", "png", "output"),
                CloudResult("data:image/png;base64,AA==", "image", "png", "output"),
            ),
            message = "",
            durationSeconds = 0,
        )
        store().saveResult("openai", "local-1", result)

        val restored = store().result("openai", "local-1")

        assertNotNull(restored)
        assertEquals("SUCCESS", restored!!.status)
        assertEquals(2, restored.results.size)
        assertEquals("https://example.com/out.png", restored.results[0].url)
        assertEquals("data:image/png;base64,AA==", restored.results[1].url)
    }

    @Test
    fun failedWriteIsReportedInsteadOfPretendingRecoveryWasSaved() {
        val blockedDirectory = temp.newFile("cloud_tasks")
        val store = CloudProviderTaskStore(blockedDirectory)
        val error = assertThrows(IOException::class.java) {
            store.savePending("replicate", "paid-task", JSONObject().put("endpoint", "https://example.test"))
        }
        assertTrue(error.message.orEmpty().contains("recovery directory"))
        assertNull(store.pending("replicate", "paid-task"))
    }

    @Test
    fun longRunningPaidTaskIsNotPrunedByAge() {
        val store = store()
        store.savePending("replicate", "old-task", JSONObject().put("endpoint", "https://old.test"))
        val target = File(temp.root, "cloud_tasks").listFiles()!!.single()
        assertTrue(target.setLastModified(System.currentTimeMillis() - 10L * 24 * 60 * 60_000L))
        store.savePending("replicate", "new-task", JSONObject().put("endpoint", "https://new.test"))
        assertNotNull(store.pending("replicate", "old-task"))
    }

    @Test
    fun providersAndClearAreIsolated() {
        val store = store()
        store.savePending("provider-a", "same-task", JSONObject().put("source", "a"))
        store.savePending("provider-b", "same-task", JSONObject().put("source", "b"))

        assertEquals("a", store.pending("provider-a", "same-task")!!.getString("source"))
        assertEquals("b", store.pending("provider-b", "same-task")!!.getString("source"))

        store.clear("provider-a", "same-task")
        assertNull(store.pending("provider-a", "same-task"))
        assertNotNull(store.pending("provider-b", "same-task"))
    }
    @Test
    fun acceptedPaidTaskWriteFailureIncludesRemoteIdForManualRecovery() {
        val blocked = temp.newFile("blocked-tasks")
        val store = CloudProviderTaskStore(blocked)
        val error = assertThrows(IOException::class.java) {
            store.saveAcceptedTask(
                "siliconflow",
                "real-remote-id-123",
                JSONObject().put("base", "https://provider.example"),
                "SiliconFlow",
            )
        }
        assertTrue(error.message.orEmpty().contains("real-remote-id-123"))
        assertTrue(error.message.orEmpty().contains("before submitting again"))
    }

}
