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
            .put("batch_size", 2)
            .put("num_inference_steps", 28)
            .put("guidance_scale", 7.5)
            .put("seed", 123456789L)

        val payload = buildSiliconFlowImagePayload("Kwai-Kolors/Kolors", body)

        assertEquals("a fox", payload.getString("prompt"))
        assertEquals("blurry", payload.getString("negative_prompt"))
        assertTrue(payload.get("prompt_enhancement") is Boolean)
        assertTrue(payload.getBoolean("prompt_enhancement"))
        assertEquals(2, payload.getInt("batch_size"))
        assertEquals(28, payload.getInt("num_inference_steps"))
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
    fun stringifiedBooleanCannotLeakIntoTheRequest() {
        val body = JSONObject()
            .put("prompt", "a fox")
            .put("prompt_enhancement", "false")

        val payload = buildSiliconFlowImagePayload("Qwen/Qwen-Image", body)

        assertTrue(payload.get("prompt_enhancement") is Boolean)
        assertFalse(payload.getBoolean("prompt_enhancement"))
    }
}
