package com.comfyremote.app.data.cloud

import android.content.SharedPreferences
import com.comfyremote.app.data.security.SecretStore
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Local HTTP integration tests: no provider credentials, no billable API requests.
 * Exercise actual ReplicateRepository submit/result and file-backed recovery.
 */
class ReplicateHttpRecoveryIntegrationTest {
    @get:Rule val temp = TemporaryFolder()

    /** Model ids are normally pulled from Replicate; tests seed the pulled list explicitly. */
    private val replicateModels = listOf(
        "minimax/video-01",
        "black-forest-labs/flux-schnell",
    )

    @Test
    fun submittedPredictionUsesOriginalEndpointAfterSettingsChangeAndRestart() {
        val original = MockWebServer()
        val replacement = MockWebServer()
        original.start()
        replacement.start()
        try {
            original.enqueue(json("""{"id":"pred-123"}"""))
            original.enqueue(json("""{"status":"processing"}"""))
            original.enqueue(json("""{"status":"succeeded","model":"minimax/video-01","output":["https://media.invalid/result.mp4"]}"""))

            val settings = settings()
            settings.save(ReplicateRepository.PROVIDER, base(original), "fake-token", replicateModels)
            assertEquals("fake-token", settings.account(ReplicateRepository.PROVIDER, "").token)
            val path = File(temp.root, "cloud_tasks")
            val taskId = runBlocking {
                ReplicateRepository(settings, CloudProviderTaskStore(path)).submit(
                    "minimax/video-01", JSONObject().put("prompt", "verification only"),
                )
            }
            assertEquals("pred-123", taskId)
            val submitRequest = original.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("POST", submitRequest.method)
            assertEquals("/v1/models/minimax/video-01/predictions", submitRequest.path)
            assertEquals("Bearer fake-token", submitRequest.getHeader("Authorization"))
            assertEquals("verification only", JSONObject(submitRequest.body.readUtf8())
                .getJSONObject("input").getString("prompt"))

            // Simulate a provider settings edit before app process is recreated.
            settings.save(ReplicateRepository.PROVIDER, base(replacement), "fake-token", replicateModels)
            val restoredStore = CloudProviderTaskStore(path)
            val restoredRepository = ReplicateRepository(settings, restoredStore)

            val running = runBlocking { restoredRepository.result(taskId) }
            assertEquals("RUNNING", running.status)
            val firstPoll = original.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/v1/predictions/pred-123", firstPoll.path)
            assertEquals("Bearer fake-token", firstPoll.getHeader("Authorization"))
            assertEquals(0, replacement.requestCount)

            val complete = runBlocking { restoredRepository.result(taskId) }
            assertEquals("SUCCESS", complete.status)
            assertEquals("video", complete.results.single().type)
            assertEquals("https://media.invalid/result.mp4", complete.results.single().url)
            val lastPoll = original.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/v1/predictions/pred-123", lastPoll.path)
            assertEquals("Bearer fake-token", lastPoll.getHeader("Authorization"))
            assertEquals(0, replacement.requestCount)

            // The result is durable even if another process starts with no HTTP response available.
            val newProcess = ReplicateRepository(settings, CloudProviderTaskStore(path))
            assertEquals("SUCCESS", runBlocking { newProcess.result(taskId) }.status)
            assertEquals(3, original.requestCount)
            newProcess.forgetTask(taskId)
            assertNull(CloudProviderTaskStore(path).pending(ReplicateRepository.PROVIDER, taskId))
            assertNull(CloudProviderTaskStore(path).result(ReplicateRepository.PROVIDER, taskId))
        } finally {
            original.shutdown()
            replacement.shutdown()
        }
    }

    @Test
    fun failedRecoveryWriteSurfacesRemoteTaskIdInsteadOfReportingSuccessfulSubmit() {
        val original = MockWebServer()
        original.start()
        try {
            original.enqueue(json("""{"id":"paid-but-unpersisted-123"}"""))
            val blocked = temp.newFile("blocked-cloud-tasks")
            val settings = settings()
            settings.save(ReplicateRepository.PROVIDER, base(original), "fake-token", replicateModels)
            val exception = assertThrows(IOException::class.java) {
                runBlocking {
                    ReplicateRepository(settings, CloudProviderTaskStore(blocked)).submit(
                        "black-forest-labs/flux-schnell", JSONObject().put("prompt", "not paid"),
                    )
                }
            }
            assertTrue(exception.message.orEmpty().contains("paid-but-unpersisted-123"))
            assertNotNull(exception.cause)
            assertEquals(1, original.requestCount)
        } finally {
            original.shutdown()
        }
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun base(server: MockWebServer) = server.url("/v1/").toString().trimEnd('/')

    /**
     * Android SharedPreferences stand-in for local JVM tests, including SecretStore's
     * expected legacy-token fallback when Android Keystore isn't available.
     */
    private fun settings(): CloudProviderSettingsStore {
        val entries = mutableMapOf<String, Any>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putInt", "putBoolean", "putLong", "putFloat", "putStringSet" -> {
                    entries[args!![0] as String] = args[1] ?: ""
                    proxy
                }
                "remove" -> {
                    entries.remove(args!![0] as String)
                    proxy
                }
                "clear" -> {
                    entries.clear()
                    proxy
                }
                "apply" -> null
                "commit" -> true
                else -> throw UnsupportedOperationException("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor

        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> entries[args!![0] as String] as? String ?: args[1]
                "edit" -> editor
                "getStringSet" -> entries[args!![0] as String] as? Set<*> ?: args[1]
                "getInt" -> entries[args!![0] as String] as? Int ?: args[1]
                "getBoolean" -> entries[args!![0] as String] as? Boolean ?: args[1]
                "getLong" -> entries[args!![0] as String] as? Long ?: args[1]
                "getFloat" -> entries[args!![0] as String] as? Float ?: args[1]
                "contains" -> entries.containsKey(args!![0] as String)
                "getAll" -> entries.toMap()
                else -> throw UnsupportedOperationException("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
        return CloudProviderSettingsStore(prefs, SecretStore(prefs))
    }
}
