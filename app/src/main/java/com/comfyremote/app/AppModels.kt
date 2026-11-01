package com.comfyremote.app

import org.json.JSONObject

/** One labeled prompt text kept in history and prompt presets. */
data class LabeledText(val label: String, val value: String)

/** A saved server address with a user-editable alias. */
data class ServerEntry(val name: String, val url: String)

/** True when ComfyUI marked this prompt as user-interrupted (not a real error). */
fun historyCancelled(entry: JSONObject): Boolean {
    val msgs = entry.optJSONObject("status")?.optJSONArray("messages") ?: return false
    for (i in 0 until msgs.length()) {
        val m = msgs.optJSONArray(i) ?: continue
        if (m.optString(0) == "execution_interrupted") return true
    }
    return false
}
