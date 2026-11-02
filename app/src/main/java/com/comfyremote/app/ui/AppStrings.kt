package com.comfyremote.app.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * String resources for code that has no composition: ViewModels, repositories,
 * notifications. Composables should use `stringResource` instead.
 *
 * Initialised once from [com.comfyremote.app.ComfyRemoteApp]. Resolution goes
 * through the application context each call, so a system language change is
 * picked up without restarting. In plain JVM unit tests the context is absent and
 * a stable placeholder is returned instead of crashing.
 */
object AppStrings {
    @Volatile
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        // Without a context (JVM tests) keep the format args visible so callers'
        // output still carries the data it was built from.
        val ctx = context ?: return if (args.isEmpty()) "#$id" else "#$id" + args.joinToString(prefix = "(", postfix = ")")
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val ctx = context ?: return "#$id:$count"
        return ctx.resources.getQuantityString(id, count, *args)
    }
}
