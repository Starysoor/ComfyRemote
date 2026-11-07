package com.comfyremote.app.data.cloud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiliconFlowPayloadTest {

    @Test
    fun payloadKeepsBooleanAndNumericTypes() {
        val body = JSONObject()
            .put("prompt", "a fox")
            .put("negative_prompt", "blurry")
            .put("image_size", "1024x1024")
            .put("prompt_enhancement", true)
            .put("num_inference_steps", 20)
            .put("guidance_scale", 7.5)
            .put("seed", 123456789L)

        val payload = buildSiliconFlowImagePayload("Kwai-Kolors/Kolors", body)

        assertEquals("a fox", payload.getString("prompt"))
        assertEquals("blurry", payload.getString("negative_prompt"))
        assertTrue(payload.get("prompt_enhancement") is Boolean)
        assertTrue(payload.getBoolean("prompt_enhancement"))
        assertFalse(payload.has("batch_size"))
        assertEquals(20, payload.getInt("num_inference_steps"))
        assertEquals(7.5, payload.getDouble("guidance_scale"), 0.0)
        assertEquals(123456789L, payload.getLong("seed"))
    }

    @Test
    fun referenceSlotsAreRenumberedContiguously() {
        val body = JSONObject()
            .put("prompt", "combine")
            .put("image", "data:image/png;base64,AA==")
            .put("image3", "data:image/png;base64,Ag==")

        val payload = buildSiliconFlowImagePayload("Qwen/Qwen-Image-Edit-2509", body)

        assertTrue(payload.has("image"))
        assertTrue(payload.has("image2"))
        assertTrue(!payload.has("image3"))
        assertEquals("data:image/png;base64,Ag==", payload.getString("image2"))
    }

    @Test
    fun miniMaxH3UsesTheNewVideoContract() {
        val body = JSONObject()
            .put("prompt", "a fox running")
            .put("duration", 10)
            .put("resolution", "768p")
            .put("ratio", "16:9")
            .put("first_frame_image", "data:image/png;base64,AA==")
            .put("last_frame_image", "data:image/png;base64,AQ==")

        val payload = buildSiliconFlowVideoPayload("MiniMaxAI/MiniMax-H3", body)

        assertEquals("MiniMaxAI/MiniMax-H3", payload.getString("model"))
        assertEquals(10, payload.getInt("duration"))
        assertEquals("768p", payload.getString("resolution"))
        assertEquals("16:9", payload.getString("ratio"))
        val images = payload.getJSONArray("images")
        assertEquals(2, images.length())
        assertEquals("first_frame", images.getJSONObject(0).getString("role"))
        assertEquals("last_frame", images.getJSONObject(1).getString("role"))
        assertTrue(!payload.has("image_size"))
        assertTrue(!payload.has("batch_size"))
    }

    @Test
    fun stringifiedBooleanCannotLeakIntoTheRequest() {
        val body = JSONObject()
            .put("prompt", "a fox")
            .put("prompt_enhancement", "false")

        val payload = buildSiliconFlowImagePayload("Qwen/Qwen-Image", body)

        assertTrue(payload.get("prompt_enhancement") is Boolean)
        assertFalse(payload.getBoolean("prompt_enhancement"))
    }
}
