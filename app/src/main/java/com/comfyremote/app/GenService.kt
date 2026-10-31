package com.comfyremote.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.comfyremote.app.ui.L10n

/**
 * Empty foreground service whose only job is to keep the app process alive
 * (and immune to background freezing) while a generation is running.
 * All actual work lives in MainViewModel; the service just holds the notification.
 */
class GenService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, MainViewModel.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(L10n.t("ComfyUI 生成中", "ComfyUI generating"))
            .setContentText(L10n.t("已提交到服务器，完成后会通知你。请勿强制关闭应用。", "Queued on the server; you will be notified when it finishes."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ServiceCompat.startForeground(this, 1, notif, 0)
        }
        return START_NOT_STICKY
    }
}
