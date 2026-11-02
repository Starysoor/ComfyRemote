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

    suspend fun visionChat(
        systemPrompt: String,
        userText: String,
        imageBytes: ByteArray,
        mime: String = "image/jpeg",
    ): String {
        val s = settingsStore.load()
        require(s.configured) { "AI endpoint is not configured" }
        val encoded = java.util.Base64.getEncoder().encodeToString(imageBytes)
        return AiClient.chatWithImage(
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            model = s.visionModel.ifBlank { s.model },
            systemPrompt = systemPrompt,
            userText = userText,
            imageDataUrl = "data:$mime;base64,$encoded",
            t = s.asTunables(),
        )
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
