package com.comfyremote.app.data.generation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.comfyremote.app.MainActivity
import com.comfyremote.app.R

/** Builds all generation-related notifications and task deep links. */
class GenerationNotificationController(private val context: Context) {
    companion object {
        const val CHANNEL_RUNNING = "gen_running"
        const val CHANNEL_DONE = "gen_done"
        const val RUNNING_NOTIFICATION_ID = 1
        const val EXTRA_TASK_ID = "generation_task_id"
        private const val FINISHED_NOTIFICATION_BASE = 100
    }

    fun ensureChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.notif_channel_running), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, context.getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun contentIntent(task: GenerationTask): PendingIntent = PendingIntent.getActivity(
        context,
        task.taskId.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TASK_ID, task.taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun runningNotification(task: GenerationTask): Notification {
        val text = when (task.phase) {
            GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> context.getString(R.string.notif_queued)
            GenerationPhase.CANCELLING -> context.getString(R.string.notif_cancelling)
            else -> buildString {
                append(context.getString(R.string.notif_node, "#${task.nodeId ?: "?"}"))
                append(" ")
                append(task.nodeTitle.orEmpty())
                task.progress?.let { append("  ${(it * 100).toInt()}%") }
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.notif_generating))
            .setContentText(task.request.workflowName + " · " + text.trim())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                task.progress?.let { setProgress(100, (it * 100).toInt(), false) } ?: setProgress(0, 0, true)
            }
            .setContentIntent(contentIntent(task))
            .build()
    }

    fun notifyFinished(task: GenerationTask) {
        val settings = (context.applicationContext as? com.comfyremote.app.ComfyRemoteApp)
            ?.container?.appSettingsStore?.settings?.value
        if (settings?.notifyOnFinish == false) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (title, text, icon) = when (task.phase) {
            GenerationPhase.SUCCEEDED -> Triple(
                context.getString(R.string.notif_done_title),
                context.resources.getQuantityString(
                    R.plurals.notif_done_text,
                    task.results.size,
                    task.request.workflowName,
                    task.results.size,
                ),
                android.R.drawable.ic_menu_gallery,
            )
            GenerationPhase.CANCELLED -> Triple(
                context.getString(R.string.notif_cancelled),
                task.request.workflowName,
                android.R.drawable.ic_menu_close_clear_cancel,
            )
            else -> Triple(
                context.getString(R.string.notif_failed),
                task.error?.message ?: task.request.workflowName,
                android.R.drawable.ic_dialog_alert,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(task))
            .setAutoCancel(true)
            .build()
        nm.notify(FINISHED_NOTIFICATION_BASE + (task.taskId.hashCode() and 0x3ff), notification)
    }
}
