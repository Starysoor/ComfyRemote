package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.AiTunables
import com.comfyremote.app.data.security.SecretStore
import org.json.JSONObject

/** Everything the AI expansion feature needs to talk to its endpoint. */
data class AiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** Optional multimodal model for 看图改写; blank falls back to [model]. */
    val visionModel: String = "",
    val temperatureText: String = "0.8",
    val maxTokensText: String = "",
    val extraJson: String = "",
    val thinkingMode: String = "default",
    /** AUTO resolves from the workflow name (Danbooru/Illustrious/Pony/native). */
    val tagProfile: String = "AUTO",
) {
    val configured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun asTunables(): AiTunables {
        return AiTunables(
            temperature = temperatureText.trim().toDoubleOrNull(),
            maxTokens = maxTokensText.trim().toIntOrNull()?.takeIf { it > 0 },
            extraJson = AiModelCapabilities.filterExtra(
                runCatching { JSONObject(extraJson) }.getOrNull(),
                baseUrl,
                model,
            ),
        )
    }
}

/** Persists AI expansion endpoint settings + the GitHub update token. */
class AiSettingsStore(
    private val prefs: SharedPreferences,
    private val secrets: SecretStore,
) {

    fun load(): AiSettings = AiSettings(
        baseUrl = prefs.getString("aiBase", "") ?: "",
        apiKey = secrets.get("aiKey") ?: "",
        model = prefs.getString("aiModel", "") ?: "",
        visionModel = prefs.getString("aiVisionModel", "") ?: "",
        temperatureText = prefs.getString("aiTemp", "0.8") ?: "0.8",
        maxTokensText = prefs.getString("aiMaxTok", "") ?: "",
        extraJson = prefs.getString("aiExtra", "") ?: "",
        thinkingMode = prefs.getString("aiThinking", "default") ?: "default",
        tagProfile = prefs.getString("aiTagProfile", "AUTO") ?: "AUTO",
    )

    fun save(s: AiSettings) {
        secrets.put("aiKey", s.apiKey)
        prefs.edit()
            .putString("aiBase", s.baseUrl.trim())
            .putString("aiModel", s.model.trim())
            .putString("aiVisionModel", s.visionModel.trim())
            .putString("aiTemp", s.temperatureText.trim())
            .putString("aiMaxTok", s.maxTokensText.trim())
            .putString("aiExtra", s.extraJson.trim())
            .putString("aiThinking", s.thinkingMode)
            .putString("aiTagProfile", s.tagProfile)
            .apply()
    }
}
