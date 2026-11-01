package com.comfyremote.app.data.generation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Holds the foreground notification while any generation task is active.
 * Starts itself into foreground when tasks exist, stops when they finish.
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var controller: GenerationNotificationController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = GenerationNotificationController(this)
        controller.ensureChannels()
        val container = (application as com.comfyremote.app.ComfyRemoteApp).container
        scope.launch {
            // Observe the source task StateFlow directly. The derived activeTasks flow
            // has an empty initial state and can make a freshly started service stop
            // itself before the first mapped value arrives.
            container.generationManager.tasks.collect { tasks ->
                val active = tasks.filter { it.isActive }
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val t = active.first()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this@GenerationService, GenerationNotificationController.RUNNING_NOTIFICATION_ID,
                            controller.runningNotification(t), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        ServiceCompat.startForeground(
                            this@GenerationService, GenerationNotificationController.RUNNING_NOTIFICATION_ID,
                            controller.runningNotification(t), 0
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
