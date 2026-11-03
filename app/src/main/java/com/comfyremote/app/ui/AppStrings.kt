package com.comfyremote.app.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * String resources for code that has no composition: ViewModels, repositories,
 * notifications. Composables should use `stringResource` instead.
 *
 * Initialised once from [com.comfyremote.app.ComfyRemoteApp]. Resolution goes
 * through the application resources each call, so a system language change is
 * picked up without restarting. In plain JVM unit tests no resources exist and a
 * stable placeholder is returned instead of crashing.
 */
object AppStrings {
    @Volatile
    private var resources: Resources? = null

    fun init(context: Context) {
        resources = context.applicationContext.resources
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        // Without resources (JVM tests) keep the format args visible so callers'
        // output still carries the data it was built from.
        val res = resources ?: return if (args.isEmpty()) "#$id" else "#$id" + args.joinToString(prefix = "(", postfix = ")")
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val res = resources ?: return "#$id:$count"
        return res.getQuantityString(id, count, *args)
    }
}
