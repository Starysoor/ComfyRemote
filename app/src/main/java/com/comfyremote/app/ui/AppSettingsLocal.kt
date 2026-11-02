package com.comfyremote.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.comfyremote.app.data.repository.AppSettings

/**
 * App preferences available to every composable without threading a ViewModel
 * through the navigation graph. The root provider updates on every settings
 * change, so screens react immediately.
 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
