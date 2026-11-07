package com.comfyremote.app.data.cloud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMultiImageTest {

    @Test
    fun everyReferenceBecomesItsOwnInlineDataPart() {
        val body = JSONObject()
            .put("image", "data:image/png;base64,AA==")
            .put("image2", "data:image/png;base64,AQ==")
            .put("image3", "data:image/png;base64,Ag==")

        val parts = buildGeminiImageParts("gemini-3-pro-image", "combine the images", body) {
            CloudHttp.imagePayload(it)
        }

        val inline = (0 until parts.length()).count { index ->
            parts.optJSONObject(index)?.has("inlineData") == true
        }
        assertEquals(3, inline)
        assertEquals(4, parts.length())
        assertTrue(
            (0 until parts.length()).any { index ->
                parts.optJSONObject(index)?.optString("text") == "combine the images"
            }
        )
    }

    @Test
    fun aModelWithoutReferenceSupportOnlySendsText() {
        val body = JSONObject()
            .put("image", "data:image/png;base64,AA==")

        val parts = buildGeminiImageParts("imagen-4", "a fox", body) {
            CloudHttp.imagePayload(it)
        }

        assertEquals(1, parts.length())
        assertEquals("a fox", parts.getJSONObject(0).getString("text"))
    }
}
