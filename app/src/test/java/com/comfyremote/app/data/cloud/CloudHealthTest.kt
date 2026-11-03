package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CloudHealthTest {
    @Test
    fun authErrorsBecomeAuthFailed() {
        val health = classifyCloudFailure(ComfyException("HTTP 401: invalid api key"))
        assertEquals(CloudHealthStatus.AUTH_FAILED, health.status)
    }

    @Test
    fun serverErrorsStayGeneric() {
        val health = classifyCloudFailure(ComfyException("HTTP 500: internal error"))
        assertEquals(CloudHealthStatus.ERROR, health.status)
    }

    @Test
    fun networkFailuresBecomeUnreachable() {
        assertEquals(
            CloudHealthStatus.UNREACHABLE,
            classifyCloudFailure(UnknownHostException("api.example.com")).status,
        )
        assertEquals(
            CloudHealthStatus.UNREACHABLE,
            classifyCloudFailure(SocketTimeoutException("timeout")).status,
        )
    }

    @Test
    fun modelIdsAreReadFromModelsResponse() {
        val data = JSONArray()
            .put(JSONObject().put("id", "model-a"))
            .put(JSONObject().put("id", "model-b"))
            .put(JSONObject().put("name", "ignored"))

        assertEquals(listOf("model-a", "model-b"), modelIdsFromResponse(data))
        assertEquals(
            listOf("ignored"),
            modelIdsFromResponse(data, idKey = "name"),
        )
    }
}
