package com.comfyremote.app.data.cloud

import android.content.SharedPreferences
import com.comfyremote.app.data.security.SecretStore

data class CloudProviderAccount(
    val providerId: String,
    val baseUrl: String,
    val token: String,
    val models: List<String>,
)

/** Per-provider endpoint, credential and model list for the multi-cloud settings pages. */
class CloudProviderSettingsStore(
    private val prefs: SharedPreferences,
    private val secrets: SecretStore,
) {
    fun account(providerId: String, defaultBaseUrl: String, defaultModels: List<String> = emptyList()): CloudProviderAccount {
        val base = prefs.getString("cloud_${providerId}_base", null)
            ?.takeIf { it.isNotBlank() }
            ?: defaultBaseUrl
        val models = prefs.getString("cloud_${providerId}_models", null)
            ?.split(',', '\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.takeIf { it.isNotEmpty() }
            ?: defaultModels
        return CloudProviderAccount(
            providerId = providerId,
            baseUrl = base,
            token = secrets.get("cloud_${providerId}_token").orEmpty(),
            models = models,
        )
    }

    fun save(providerId: String, baseUrl: String, token: String, models: List<String>) {
        secrets.put("cloud_${providerId}_token", token)
        prefs.edit()
            .putString("cloud_${providerId}_base", baseUrl.trim())
            .putString(
                "cloud_${providerId}_models",
                models.map(String::trim).filter(String::isNotBlank).distinct().joinToString(","),
            )
            .apply()
    }

    fun extra(providerId: String): String =
        prefs.getString("cloud_${providerId}_extra", "").orEmpty()

    fun saveExtra(providerId: String, value: String) {
        prefs.edit().putString("cloud_${providerId}_extra", value).apply()
    }
}
