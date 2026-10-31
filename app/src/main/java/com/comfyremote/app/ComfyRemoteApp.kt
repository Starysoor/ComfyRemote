package com.comfyremote.app

import android.app.Application

/** Application entry: owns the lightweight dependency container. */
class ComfyRemoteApp : Application() {
    val container by lazy { AppContainer(this) }
}
