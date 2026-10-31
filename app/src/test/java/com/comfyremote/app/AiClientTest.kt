package com.comfyremote.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val skills = AiSkillDef.defaults()
        assertEquals(AiSkillDef.DEFAULT_TAGS_ID, AiSkillDef.recommendedOrder(skills, "SDXL-动漫文生图").first().id)
        assertEquals(AiSkillDef.DEFAULT_TAGS_ID, AiSkillDef.recommendedOrder(skills, "Anima-Turbo极速文生图").first().id)
        assertEquals(AiSkillDef.DEFAULT_NATURAL_ID, AiSkillDef.recommendedOrder(skills, "MOSS-A-加速文生图").first().id)
        assertEquals(AiSkillDef.DEFAULT_NATURAL_ID, AiSkillDef.recommendedOrder(skills, "Qwen-2.1-图生图").first().id)
        // custom skills follow the recommended built-in
        val withCustom = skills + AiSkillDef("custom_1", "我的风格", "prompt", false)
        val ordered = AiSkillDef.recommendedOrder(withCustom, "SDXL-动漫文生图")
        assertEquals(AiSkillDef.DEFAULT_TAGS_ID, ordered.first().id)
        assertEquals("custom_1", ordered.last().id)
    }

    @Test
    fun buildChatBodyDefaultsOmitTunables() {
        val body = AiClient.buildChatBody("m", "sys", "user text", AiTunables())
        assertEquals("m", body.optString("model"))
        assertFalse(body.has("temperature"))
        assertFalse(body.has("max_tokens"))
        assertEquals(2, body.optJSONArray("messages")!!.length())
        assertEquals("sys", body.optJSONArray("messages")!!.optJSONObject(0)!!.optString("content"))
        assertEquals("user text", body.optJSONArray("messages")!!.optJSONObject(1)!!.optString("content"))
    }

    @Test
    fun buildChatBodyAppliesTunablesAndExtraOverrides() {
        val extra = JSONObject().put("reasoning_effort", "high").put("top_p", 0.9)
        val body = AiClient.buildChatBody(
            "m", "sys", "u",
            AiTunables(temperature = 1.2, maxTokens = 2048, extraJson = extra),
        )
        assertEquals(1.2, body.optDouble("temperature"), 1e-9)
        assertEquals(2048, body.optInt("max_tokens"))
        assertEquals("high", body.optString("reasoning_effort"))
        assertEquals(0.9, body.optDouble("top_p"), 1e-9)
    }

    @Test
    fun applyThinkingModeWritesExpectedFields() {
        // mirrors MainViewModel.applyThinkingMode mapping via the same JSON contract
        val extra = JSONObject()
        extra.put("thinking", JSONObject().put("type", "disabled"))
        assertTrue(extra.optJSONObject("thinking")!!.optString("type") == "disabled")
        extra.remove("thinking")
        extra.put("reasoning_effort", "medium")
        assertEquals("medium", extra.optString("reasoning_effort"))
        assertNull(extra.optJSONObject("thinking"))
    }
}
