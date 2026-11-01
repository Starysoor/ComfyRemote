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
import com.comfyremote.app.ui.L10n

/** Builds all generation-related notifications and channels. */
class GenerationNotificationController(private val context: Context) {

    companion object {
        const val CHANNEL_RUNNING = "gen_running"
        const val CHANNEL_DONE = "gen_done"
        const val RUNNING_NOTIFICATION_ID = 1
        private const val FINISHED_NOTIFICATION_BASE = 100
    }

    fun ensureChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, L10n.t("生成状态", "Generation status"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, L10n.t("生成完成", "Generation finished"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun runningNotification(task: GenerationTask): Notification {
        val text = when (task.phase) {
            GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> L10n.t("排队中…", "Queued…")
            GenerationPhase.CANCELLING -> L10n.t("正在取消…", "Cancelling…")
            else -> buildString {
                append(L10n.t("执行节点 ", "Node "))
                append("#${task.nodeId ?: "?"} ")
                append(task.nodeTitle ?: "")
                task.progress?.let { append("  ${(it * 100).toInt()}%") }
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(L10n.t("ComfyUI 生成中", "ComfyUI generating"))
            .setContentText(task.request.workflowName + " · " + text.trim())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (task.progress != null) setProgress(100, (task.progress * 100).toInt(), false)
                else setProgress(0, 0, true)
            }
            .setContentIntent(contentIntent())
            .build()
    }

    fun notifyFinished(task: GenerationTask) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (title, text, icon) = when (task.phase) {
            GenerationPhase.SUCCEEDED -> Triple(
                L10n.t("生成完成", "Generation finished"),
                "${task.request.workflowName} · ${task.results.size} " +
                    L10n.t("张图", "image(s)"),
                android.R.drawable.ic_menu_gallery,
            )
            GenerationPhase.CANCELLED -> Triple(
                L10n.t("已取消", "Cancelled"),
                task.request.workflowName,
                android.R.drawable.ic_menu_close_clear_cancel,
            )
            else -> Triple(
                L10n.t("生成失败", "Generation failed"),
                task.error?.message ?: task.request.workflowName,
                android.R.drawable.ic_dialog_alert,
            )
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(FINISHED_NOTIFICATION_BASE + (task.promptId?.hashCode()?.mod(1000) ?: 0), notif)
    }
}
