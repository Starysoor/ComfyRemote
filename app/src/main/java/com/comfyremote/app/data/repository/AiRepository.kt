package com.comfyremote.app.data.repository

import com.comfyremote.app.AiClient
import com.comfyremote.app.AiImageInput
import com.comfyremote.app.AiImagePayload
import com.comfyremote.app.data.reference.ReferenceRole

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
        return visionChat(
            systemPrompt = systemPrompt,
            userText = userText,
            images = listOf(
                AiImagePayload(
                    bytes = imageBytes,
                    mime = mime,
                    role = ReferenceRole.AUTO,
                )
            ),
        )
    }

    /** Sends every enabled reference image in one vision request, preserving role/order. */
    suspend fun visionChat(
        systemPrompt: String,
        userText: String,
        images: List<AiImagePayload>,
    ): String {
        val s = settingsStore.load()
        require(s.configured) { "AI endpoint is not configured" }
        val encoded = images.sortedBy { it.order }.map { image ->
            AiImageInput(
                dataUrl = "data:${image.mime};base64," +
                    java.util.Base64.getEncoder().encodeToString(image.bytes),
                role = image.role,
                order = image.order,
                label = image.label,
            )
        }
        return AiClient.chatWithImages(
            baseUrl = s.baseUrl,
            apiKey = s.apiKey,
            model = s.visionModel.ifBlank { s.model },
            systemPrompt = systemPrompt,
            userText = userText,
            images = encoded,
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
