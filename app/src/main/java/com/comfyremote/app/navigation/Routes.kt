package com.comfyremote.app.navigation

import android.net.Uri

object Routes {
    const val CONNECT = "connect"
    const val CREATE = "create"
    const val TASKS = "tasks"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SERVERS = "servers"
    const val AI_SETTINGS = "ai-settings"
    const val UPDATE = "update"

    const val GENERATE_PATTERN = "generate?path={path}&name={name}"
    const val TASK_PATTERN = "task/{taskId}"

    fun generate(path: String, name: String): String =
        "generate?path=${Uri.encode(path)}&name=${Uri.encode(name)}"

    fun task(taskId: String): String = "task/${Uri.encode(taskId)}"
}
