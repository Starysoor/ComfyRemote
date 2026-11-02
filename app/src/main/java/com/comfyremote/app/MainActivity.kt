package com.comfyremote.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.comfyremote.app.data.generation.GenerationNotificationController
import com.comfyremote.app.navigation.ComfyRemoteRoot
import com.comfyremote.app.navigation.Routes
import com.comfyremote.app.ui.ComfyTheme
import com.comfyremote.app.ui.LocalAppSettings

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as ComfyRemoteApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleEntryIntent(intent)
        setContent {
            val settings by container.appSettingsStore.settings.collectAsState()
            ComfyTheme(settings.themeMode) {
                CompositionLocalProvider(LocalAppSettings provides settings) {
                    ComfyRemoteRoot(container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntryIntent(intent)
    }

    private fun handleEntryIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(GenerationNotificationController.EXTRA_TASK_ID)?.let { taskId ->
            container.navigationRequests.request(Routes.task(taskId))
        }
        if (container.incomingMedia.handleIntent(intent) && container.serverSession.connected) {
            container.navigationRequests.request(Routes.CREATE)
        }
    }
}
