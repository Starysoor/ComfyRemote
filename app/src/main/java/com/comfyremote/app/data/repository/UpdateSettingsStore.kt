package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.data.security.SecretStore

/** GitHub update-check token + throttle timestamp. */
class UpdateSettingsStore(
    private val prefs: SharedPreferences,
    private val secrets: SecretStore,
) {

    fun ghToken(): String? = secrets.get("ghToken")?.trim()?.ifBlank { null }

    fun saveGhToken(token: String?) {
        secrets.put("ghToken", token)
    }

    fun lastUpdateCheck(): Long = prefs.getLong("lastUpdateCheck", 0L)

    fun markUpdateChecked() {
        prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
    }
}
