package com.comfyremote.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelCapabilitiesTest {

    @Test
    fun glmShowsThinkingModes() {
        assertEquals(
            ReasoningKind.GLM_THINKING,
            AiModelCapabilities.reasoningKind("https://open.bigmodel.cn/api/paas/v4", "glm-4.6"),
        )
        assertEquals(
            ReasoningKind.REASONING_EFFORT,
            AiModelCapabilities.reasoningKind("https://open.bigmodel.cn/api/paas/v4", "glm-5.3"),
        )
    }

    @Test
    fun reasoningModelsShowEffort() {
        assertEquals(
            ReasoningKind.REASONING_EFFORT,
            AiModelCapabilities.reasoningKind("https://api.deepseek.com/v1", "deepseek-reasoner"),
        )
        assertEquals(
            ReasoningKind.REASONING_EFFORT,
            AiModelCapabilities.reasoningKind("https://api.openai.com/v1", "gpt-5.5"),
        )
        assertEquals(
            ReasoningKind.REASONING_EFFORT,
            AiModelCapabilities.reasoningKind("https://api.deepseek.com/v1", "deepseek-flash"),
        )
    }

    @Test
    fun ordinaryModelsHideReasoningControls() {
        assertEquals(
            ReasoningKind.NONE,
            AiModelCapabilities.reasoningKind("http://localhost:11434/v1", "qwen2.5:7b"),
        )
    }

    @Test
    fun inapplicableProtocolFieldsAreStripped() {
        val extra = org.json.JSONObject()
            .put("thinking", org.json.JSONObject().put("type", "enabled"))
            .put("reasoning_effort", "high")
            .put("top_p", 0.9)

        val glm = AiModelCapabilities.filterExtra(extra, "https://open.bigmodel.cn/api/paas/v4", "glm-4.6")
        assertEquals(false, glm!!.has("reasoning_effort"))
        assertEquals(true, glm.has("thinking"))

        val plain = AiModelCapabilities.filterExtra(extra, "http://localhost:11434/v1", "qwen2.5:7b")
        assertEquals(false, plain!!.has("thinking"))
        assertEquals(false, plain.has("reasoning_effort"))
        assertEquals(0.9, plain.optDouble("top_p"), 1e-9)
    }
}
