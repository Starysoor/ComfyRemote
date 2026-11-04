package com.comfyremote.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelCapabilitiesTest {
    @Test
    fun imageEditModelsReportEdit() {
        val tags = CloudModelCapabilities.of("Qwen/Qwen-Image-Edit-2509")
        assertTrue(CloudModelCapabilities.IMAGE in tags)
        assertTrue(CloudModelCapabilities.EDIT in tags)
    }

    @Test
    fun videoModelsReportVideoAndMotionDirections() {
        val i2v = CloudModelCapabilities.of("Wan-AI/Wan2.2-I2V-A14B")
        assertTrue(CloudModelCapabilities.VIDEO in i2v)
        assertTrue(CloudModelCapabilities.IMAGE_TO_VIDEO in i2v)

        val t2v = CloudModelCapabilities.of("Wan-AI/Wan2.2-T2V-A14B")
        assertTrue(CloudModelCapabilities.TEXT_TO_VIDEO in t2v)
    }

    @Test
    fun unprefixedVideoFamiliesStillReportVideo() {
        val tags = CloudModelCapabilities.of("MiniMax-Hailuo-02")
        assertEquals(listOf(CloudModelCapabilities.VIDEO), tags)
    }

    @Test
    fun textModelsAreNotSilentlyTaggedAsVideo() {
        val tags = CloudModelCapabilities.of("tencent/Hunyuan-MT-7B")
        assertEquals(listOf(CloudModelCapabilities.IMAGE), tags)
    }

    @Test
    fun seedreamIsAnImageModelThatAlsoAcceptsReferences() {
        val tags = CloudModelCapabilities.of("doubao-seedream-5-0-flash")
        assertEquals(listOf(CloudModelCapabilities.IMAGE, CloudModelCapabilities.EDIT), tags)
    }

    @Test
    fun audioModelsReportAudio() {
        assertTrue(CloudModelCapabilities.AUDIO in CloudModelCapabilities.of("index-tts2"))
    }
}
