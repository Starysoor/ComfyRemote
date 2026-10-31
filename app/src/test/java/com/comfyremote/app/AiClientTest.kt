package com.comfyremote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {

    @Test
    fun parseChatCompletionExtractsContent() {
        val body = """{"choices":[{"message":{"role":"assistant","content":" 1girl, cafe, sunset "}}]}"""
        assertEquals("1girl, cafe, sunset", AiClient.parseChatCompletion(body))
    }

    @Test
    fun parseChatCompletionSurfacesProviderError() {
        val body = """{"error":{"message":"Incorrect API key"}}"""
        try {
            AiClient.parseChatCompletion(body)
            throw AssertionError("expected ComfyException")
        } catch (e: ComfyException) {
            assertTrue(e.message!!.contains("Incorrect API key"))
        }
    }

    @Test(expected = ComfyException::class)
    fun parseChatCompletionFailsOnEmptyContent() {
        AiClient.parseChatCompletion("""{"choices":[]}""")
    }

    @Test
    fun skillRecommendationFollowsWorkflowType() {
        assertEquals(AiSkill.TAGS, AiSkill.recommendedFor("SDXL-动漫文生图").first())
        assertEquals(AiSkill.TAGS, AiSkill.recommendedFor("Anima-Turbo极速文生图").first())
        assertEquals(AiSkill.NATURAL, AiSkill.recommendedFor("MOSS-A-加速文生图").first())
        assertEquals(AiSkill.NATURAL, AiSkill.recommendedFor("Qwen-2.1-图生图").first())
    }
}
