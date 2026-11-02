package com.comfyremote.app.data.cloud

import com.comfyremote.app.data.security.SecretStore

/** AutoDL token for the hosted ComfyUI workflow API (ComfyUI group). */
class AutoDlSettingsStore(
    private val secrets: SecretStore,
) {
    fun token(): String? = secrets.get(KEY_TOKEN)?.trim()?.takeIf { it.isNotBlank() }

    fun saveToken(token: String?) {
        secrets.put(KEY_TOKEN, token)
    }

    fun configured(): Boolean = token() != null

    companion object { private const val KEY_TOKEN = "autoDlToken" }
}
