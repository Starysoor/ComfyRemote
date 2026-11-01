package com.comfyremote.app.data.repository

import com.comfyremote.app.AiClient

/** OpenAI-compatible AI operations kept out of feature ViewModels. */
class AiRepository(
    private val settingsStore: AiSettingsStore,
) {
    fun settings(): AiSettings = settingsStore.load()

    suspend fun chat(systemPrompt: String, userText: String): String {
        val s = settingsStore.load()
        require(s.configured) { "AI endpoint is not configured" }
        return AiClient.chat(s.baseUrl, s.apiKey, s.model, systemPrompt, userText, s.asTunables())
    }

    suspend fun test(settings: AiSettings): String {
        require(settings.configured) { "AI endpoint is not configured" }
        return AiClient.chat(
            settings.baseUrl,
            settings.apiKey,
            settings.model,
            "Connectivity test. Reply with exactly: OK",
            "ping",
            settings.asTunables(),
        )
    }
}
