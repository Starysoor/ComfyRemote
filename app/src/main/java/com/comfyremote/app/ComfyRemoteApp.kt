package com.comfyremote.app

import android.app.Application
import com.comfyremote.app.ui.AppStrings

/** Application entry: owns the lightweight dependency container. */
class ComfyRemoteApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
    }
}
