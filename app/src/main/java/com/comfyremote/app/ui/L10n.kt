package com.comfyremote.app.ui

import java.util.Locale

/**
 * Lightweight i18n: returns the Chinese or the English string depending on the
 * system locale. Strings are declared inline at their use sites, which keeps
 * the single-activity Compose UI free of resource plumbing.
 */
object L10n {
    val zh: Boolean get() = Locale.getDefault().language.startsWith("zh")

    fun t(zhStr: String, enStr: String): String = if (zh) zhStr else enStr
}
