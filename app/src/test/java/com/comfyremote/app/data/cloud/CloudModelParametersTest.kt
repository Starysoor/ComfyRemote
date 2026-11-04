package com.comfyremote.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelParametersTest {

    @Test
    fun gptImageModelsExposeAReferenceImageAndRealQualityOptions() {
        val rules = CloudModelParameters.openAiRules("gpt-image-2.5-sunburst")
        val image = rules.firstOrNull { it.name == "image" }
        val quality = rules.firstOrNull { it.name == "quality" }

        assertTrue(image?.isImage == true)
        assertEquals(4, rules.count { it.isImage })
        assertTrue(rules.any { it.name == "image4" && it.isImage })
        assertTrue(quality?.options.orEmpty().contains("xhigh"))
        assertTrue(quality?.options.orEmpty().contains("max"))
    }

    @Test
    fun dalleThreeDoesNotPretendToAcceptAReferenceImage() {
        val rules = CloudModelParameters.openAiRules("dall-e-3")

        assertTrue(rules.none { it.isImage })
        assertTrue(rules.any { it.name == "style" })
    }

    @Test
    fun geminiImageModelsExposeInlineReferenceImages() {
        val flash = CloudModelParameters.geminiRules("gemini-3.1-flash-image")
        val pro = CloudModelParameters.geminiRules("gemini-3-pro-image")

        assertEquals(4, flash.count { it.isImage })
        assertTrue(flash.any { it.name == "image3" && it.isImage })
        assertFalse(flash.any { it.name == "image_size" })
        assertTrue(pro.any { it.isImage })
        assertTrue(pro.first { it.name == "image_size" }.options.contains("4K"))
        assertTrue(pro.first { it.name == "aspect_ratio" }.options.contains("8:1"))
    }

    @Test
    fun siliconFlowEditModelsRequireAnImageWhileTextToImageDoesNot() {
        val edit = CloudModelParameters.siliconFlowRules("Qwen/Qwen-Image-Edit-2509")
        val singleEdit = CloudModelParameters.siliconFlowRules("Qwen/Qwen-Image-Edit")
        val text = CloudModelParameters.siliconFlowRules("Qwen/Qwen-Image")

        assertEquals(3, edit.count { it.isImage })
        assertTrue(edit.first { it.name == "image" }.required)
        assertFalse(edit.first { it.name == "image2" }.required)
        assertTrue(edit.none { it.name == "image_size" })
        assertTrue(edit.none { it.name == "guidance_scale" })
        assertEquals(20.0, edit.first { it.name == "num_inference_steps" }.max!!, 0.0)
        assertEquals(1, singleEdit.count { it.isImage })
        assertTrue(singleEdit.none { it.name == "image_size" })
        assertTrue(text.none { it.isImage })
        assertEquals(9_999_999_999.0, edit.first { it.name == "seed" }.max!!, 0.0)
        assertEquals(9_999_999_999.0, text.first { it.name == "seed" }.max!!, 0.0)
    }

    @Test
    fun referenceImageInputsAreOrderedAndIgnoreBlankSlots() {
        val body = org.json.JSONObject()
            .put("image3", "three")
            .put("image", "one")
            .put("image2", "")
            .put("prompt", "ignored")

        assertEquals(
            listOf("image" to "one", "image3" to "three"),
            CloudModelParameters.referenceImageInputs(body),
        )
    }

    @Test
    fun mediaFilterDropsTextModelsButKeepsImageAndVideoModels() {
        assertFalse(CloudModelParameters.looksLikeMediaModel("claude-fable-5"))
        assertFalse(CloudModelParameters.looksLikeMediaModel("DeepSeek-V4-Pro"))
        assertFalse(CloudModelParameters.looksLikeMediaModel("MiniMax-M3.1-Flash"))
        assertTrue(CloudModelParameters.looksLikeMediaModel("gpt-image-2"))
        assertTrue(CloudModelParameters.looksLikeMediaModel("Qwen-Image"))
        assertTrue(CloudModelParameters.looksLikeMediaModel("nano-banana-2"))
        assertTrue(CloudModelParameters.looksLikeMediaModel("MiniMax-H3"))
        assertTrue(CloudModelParameters.looksLikeMediaModel("Wan-AI/Wan2.2-I2V-A14B"))
    }

    @Test
    fun capabilityTagsFollowTheRealInputContract() {
        val editTags = CloudModelCapabilities.of("gpt-image-2.5-flare")
        val seedreamTags = CloudModelCapabilities.of("doubao-seedream-5-0-flash")
        val plainTags = CloudModelCapabilities.of("Z-Image-Turbo")

        assertTrue(CloudModelCapabilities.EDIT in editTags)
        assertTrue(CloudModelCapabilities.EDIT in seedreamTags)
        assertFalse(CloudModelCapabilities.EDIT in plainTags)
    }
}
