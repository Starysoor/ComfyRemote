package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.AiTunables
import org.json.JSONObject

/** Everything the AI expansion feature needs to talk to its endpoint. */
data class AiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperatureText: String = "0.8",
    val maxTokensText: String = "",
    val extraJson: String = "",
    val thinkingMode: String = "default",
) {
    val configured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun asTunables(): AiTunables = AiTunables(
        temperature = temperatureText.trim().toDoubleOrNull(),
        maxTokens = maxTokensText.trim().toIntOrNull()?.takeIf { it > 0 },
        extraJson = runCatching { JSONObject(extraJson) }.getOrNull(),
    )
}

/** Persists AI expansion endpoint settings + the GitHub update token. */
class AiSettingsStore(private val prefs: SharedPreferences) {

    fun load(): AiSettings = AiSettings(
        baseUrl = prefs.getString("aiBase", "") ?: "",
        apiKey = prefs.getString("aiKey", "") ?: "",
        model = prefs.getString("aiModel", "") ?: "",
        temperatureText = prefs.getString("aiTemp", "0.8") ?: "0.8",
        maxTokensText = prefs.getString("aiMaxTok", "") ?: "",
        extraJson = prefs.getString("aiExtra", "") ?: "",
        thinkingMode = prefs.getString("aiThinking", "default") ?: "default",
    )

    fun save(s: AiSettings) {
        prefs.edit()
            .putString("aiBase", s.baseUrl.trim())
            .putString("aiKey", s.apiKey.trim())
            .putString("aiModel", s.model.trim())
            .putString("aiTemp", s.temperatureText.trim())
            .putString("aiMaxTok", s.maxTokensText.trim())
            .putString("aiExtra", s.extraJson.trim())
            .putString("aiThinking", s.thinkingMode)
            .apply()
    }
}
