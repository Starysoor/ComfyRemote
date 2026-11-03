package com.comfyremote.app.data.cloud

import android.content.SharedPreferences
import com.comfyremote.app.data.security.SecretStore
import org.json.JSONObject

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

    // Model management: hidden models never reach Create or Storyboard.

    fun disabledModels(providerId: String): Set<String> =
        prefs.getStringSet("cloud_${providerId}_disabled_models", emptySet()).orEmpty().toSet()

    fun setModelEnabled(providerId: String, model: String, enabled: Boolean) {
        val disabled = disabledModels(providerId).toMutableSet()
        if (enabled) disabled.remove(model) else disabled.add(model)
        prefs.edit().putStringSet("cloud_${providerId}_disabled_models", disabled).apply()
    }

    fun setAllModelsEnabled(providerId: String) {
        prefs.edit().remove("cloud_${providerId}_disabled_models").apply()
    }

    fun enabledModels(providerId: String, models: List<String>): List<String> {
        val disabled = disabledModels(providerId)
        return models.filterNot { it in disabled }
    }

    fun defaultModel(providerId: String): String =
        prefs.getString("cloud_${providerId}_default_model", "").orEmpty()

    fun setDefaultModel(providerId: String, model: String) {
        prefs.edit().putString("cloud_${providerId}_default_model", model.trim()).apply()
    }

    // Provider-specific advanced headers, merged into every HTTP request.

    fun customHeadersRaw(providerId: String): String =
        prefs.getString("cloud_${providerId}_headers", "{}").orEmpty()

    fun customHeaders(providerId: String): Map<String, String> = runCatching {
        val root = JSONObject(customHeadersRaw(providerId).ifBlank { "{}" })
        root.keys().asSequence()
            .associateWith { key -> root.optString(key) }
            .filterValues { value -> value.isNotBlank() }
    }.getOrDefault(emptyMap())

    /** Returns false when [raw] is not a JSON object of string values. */
    fun saveCustomHeaders(providerId: String, raw: String): Boolean {
        val normalized = runCatching {
            val root = JSONObject(raw.ifBlank { "{}" })
            val clean = JSONObject()
            root.keys().forEach { key ->
                if (key.isBlank()) throw IllegalArgumentException("Header name is required")
                val value = root.opt(key)
                if (value !is String) throw IllegalArgumentException("$key must be a string")
                clean.put(key, value)
            }
            clean
        }.getOrNull() ?: return false
        prefs.edit().putString("cloud_${providerId}_headers", normalized.toString()).apply()
        return true
    }

    fun mergeHeaders(providerId: String, base: Map<String, String>): Map<String, String> =
        base + customHeaders(providerId)

    // Runtime scheduling for hosted jobs. Defaults match the previous runner.

    fun pollIntervalSeconds(providerId: String): Int =
        prefs.getInt("cloud_${providerId}_poll_seconds", DEFAULT_POLL_SECONDS)
            .coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS)

    fun taskTimeoutMinutes(providerId: String): Int =
        prefs.getInt("cloud_${providerId}_timeout_minutes", DEFAULT_TIMEOUT_MINUTES)
            .coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES)

    fun pollRetryCount(providerId: String): Int =
        prefs.getInt("cloud_${providerId}_retry_count", DEFAULT_RETRY_COUNT)
            .coerceIn(0, MAX_RETRY_COUNT)

    fun saveRuntimeSettings(
        providerId: String,
        pollSeconds: Int,
        timeoutMinutes: Int,
        retryCount: Int,
    ) {
        prefs.edit()
            .putInt(
                "cloud_${providerId}_poll_seconds",
                pollSeconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS),
            )
            .putInt(
                "cloud_${providerId}_timeout_minutes",
                timeoutMinutes.coerceIn(MIN_TIMEOUT_MINUTES, MAX_TIMEOUT_MINUTES),
            )
            .putInt("cloud_${providerId}_retry_count", retryCount.coerceIn(0, MAX_RETRY_COUNT))
            .apply()
    }

    companion object {
        const val MIN_POLL_SECONDS = 2
        const val MAX_POLL_SECONDS = 60
        const val DEFAULT_POLL_SECONDS = 4
        const val MIN_TIMEOUT_MINUTES = 1
        const val MAX_TIMEOUT_MINUTES = 240
        const val DEFAULT_TIMEOUT_MINUTES = 60
        const val MAX_RETRY_COUNT = 5
        const val DEFAULT_RETRY_COUNT = 0
    }
}
