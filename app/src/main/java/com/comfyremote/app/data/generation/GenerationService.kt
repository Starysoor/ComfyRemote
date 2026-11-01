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

/** Android foreground lifecycle + generation notifications. Generation work stays in GenerationManager. */
class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var controller: GenerationNotificationController
    private val notifiedTerminal = mutableSetOf<String>()
    private var initialized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = GenerationNotificationController(this)
        controller.ensureChannels()
        val manager = (application as com.comfyremote.app.ComfyRemoteApp).container.generationManager
        scope.launch {
            manager.tasks.collect { tasks ->
                val terminal = tasks.filter { it.isTerminal }
                if (!initialized) {
                    // Do not replay completion notifications for tasks that predate this service instance.
                    notifiedTerminal += terminal.map { it.taskId }
                    initialized = true
                } else {
                    terminal.filter { notifiedTerminal.add(it.taskId) }.forEach(controller::notifyFinished)
                }

                val active = tasks.filter { it.isActive }
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@GenerationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val task = active.first()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this@GenerationService,
                            GenerationNotificationController.RUNNING_NOTIFICATION_ID,
                            controller.runningNotification(task),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                        )
                    } else {
                        ServiceCompat.startForeground(
                            this@GenerationService,
                            GenerationNotificationController.RUNNING_NOTIFICATION_ID,
                            controller.runningNotification(task),
                            0,
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
