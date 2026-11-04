package com.comfyremote.app.data.cloud

import android.content.SharedPreferences
import com.comfyremote.app.data.security.SecretVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val secrets: SecretVault,
) {
    private val _revision = MutableStateFlow(0)

    /** Emits whenever provider visibility, credentials or model selection changes. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        migratePresetCatalogs()
    }

    /**
     * Earlier builds filled official provider model lists from hard-coded
     * presets when settings were saved. Drop those lists once so every model in
     * the picker is genuinely pulled from the provider. Custom providers keep
     * their manually entered identifiers.
     */
    private fun migratePresetCatalogs() {
        val version = prefs.getInt(CATALOG_SCHEMA_KEY, 0)
        if (version >= CATALOG_SCHEMA_VERSION) return
        val editor = prefs.edit()
        listOf("openai", "gemini", "replicate", "siliconflow", "autodl_model").forEach { providerId ->
            editor.remove("cloud_${providerId}_models")
        }
        editor.putInt(CATALOG_SCHEMA_KEY, CATALOG_SCHEMA_VERSION).apply()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    fun account(providerId: String, defaultBaseUrl: String): CloudProviderAccount {
        val base = prefs.getString("cloud_${providerId}_base", null)
            ?.takeIf { it.isNotBlank() }
            ?: defaultBaseUrl
        // Models only ever come from discovery or an explicit save. A missing
        // value and a saved empty list both mean "no models", never a preset.
        val models = prefs.getString("cloud_${providerId}_models", null)
            ?.split(',', '\n')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        return CloudProviderAccount(
            providerId = providerId,
            baseUrl = base,
            token = secrets.get("cloud_${providerId}_token").orEmpty(),
            models = models,
        )
    }

    /** True once the user (or model discovery) has saved a model list, even an empty one. */
    fun hasSavedModels(providerId: String): Boolean =
        prefs.contains("cloud_${providerId}_models")

    fun save(providerId: String, baseUrl: String, token: String, models: List<String>) {
        secrets.put("cloud_${providerId}_token", token)
        prefs.edit()
            .putString("cloud_${providerId}_base", baseUrl.trim())
            .putString(
                "cloud_${providerId}_models",
                models.map(String::trim).filter(String::isNotBlank).distinct().joinToString(","),
            )
            .apply()
        bump()
    }

    // Provider visibility: hidden providers never reach Create, Storyboard or
    // the model picker. Null means "follow the configuration state".

    fun providerEnabledOverride(providerId: String): Boolean? =
        if (prefs.contains("cloud_${providerId}_enabled")) {
            prefs.getBoolean("cloud_${providerId}_enabled", true)
        } else {
            null
        }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        prefs.edit().putBoolean("cloud_${providerId}_enabled", enabled).apply()
        bump()
    }

    /** Removes credentials, endpoint overrides and model selection for one provider. */
    fun clear(providerId: String) {
        secrets.put("cloud_${providerId}_token", "")
        prefs.edit()
            .remove("cloud_${providerId}_base")
            .remove("cloud_${providerId}_models")
            .remove("cloud_${providerId}_extra")
            .remove("cloud_${providerId}_headers")
            .remove("cloud_${providerId}_disabled_models")
            .remove("cloud_${providerId}_default_model")
            .remove("cloud_${providerId}_enabled")
            .apply()
        bump()
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
        bump()
    }

    fun setAllModelsEnabled(providerId: String) {
        prefs.edit().remove("cloud_${providerId}_disabled_models").apply()
        bump()
    }

    fun enabledModels(providerId: String, models: List<String>): List<String> {
        val disabled = disabledModels(providerId)
        return models.filterNot { it in disabled }
    }

    fun defaultModel(providerId: String): String =
        prefs.getString("cloud_${providerId}_default_model", "").orEmpty()

    fun setDefaultModel(providerId: String, model: String) {
        prefs.edit().putString("cloud_${providerId}_default_model", model.trim()).apply()
        bump()
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
        private const val CATALOG_SCHEMA_KEY = "cloud_catalog_schema"
        private const val CATALOG_SCHEMA_VERSION = 3

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
