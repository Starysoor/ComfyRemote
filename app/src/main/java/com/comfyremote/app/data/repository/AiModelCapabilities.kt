package com.comfyremote.app.data.repository

import org.json.JSONObject

enum class ReasoningKind { NONE, GLM_THINKING, REASONING_EFFORT }

/** Shows only the reasoning controls the current provider/model actually understands. */
object AiModelCapabilities {
    fun reasoningKind(baseUrl: String, model: String): ReasoningKind {
        val url = baseUrl.lowercase()
        val name = model.lowercase()
        return when {
            "glm-5.3" in name || "glm-5.2" in name -> ReasoningKind.REASONING_EFFORT
            "glm" in name || "bigmodel" in url -> ReasoningKind.GLM_THINKING
            listOf("o1", "o3", "o4", "r1", "qwq", "reason", "gpt-5", "thinking", "deepseek-flash", "deepseek-v4")
                .any { it in name } -> ReasoningKind.REASONING_EFFORT
            else -> ReasoningKind.NONE
        }
    }

    /** Drops protocol fields the active model does not understand. */
    fun filterExtra(extra: JSONObject?, baseUrl: String, model: String): JSONObject? {
        extra ?: return null
        when (reasoningKind(baseUrl, model)) {
            ReasoningKind.GLM_THINKING -> extra.remove("reasoning_effort")
            ReasoningKind.REASONING_EFFORT -> extra.remove("thinking")
            ReasoningKind.NONE -> {
                extra.remove("thinking")
                extra.remove("reasoning_effort")
            }
        }
        return extra
    }
}
