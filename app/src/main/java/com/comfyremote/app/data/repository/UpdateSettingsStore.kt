package com.comfyremote.app.data.repository

import android.content.SharedPreferences

/** GitHub update-check token + throttle timestamp. */
class UpdateSettingsStore(private val prefs: SharedPreferences) {

    fun ghToken(): String? = prefs.getString("ghToken", "")?.trim()?.ifBlank { null }

    fun saveGhToken(token: String?) {
        prefs.edit().putString("ghToken", token ?: "").apply()
    }

    fun lastUpdateCheck(): Long = prefs.getLong("lastUpdateCheck", 0L)

    fun markUpdateChecked() {
        prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
    }
}
