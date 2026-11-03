package com.comfyremote.app.navigation

import android.net.Uri

object Routes {
    const val CONNECT = "connect"
    const val CREATE = "create"
    const val TASKS = "tasks"
    const val HISTORY = "history"
    const val STORYBOARD = "storyboard"
    const val SETTINGS = "settings"
    const val APP_SETTINGS = "app-settings"
    const val CLOUD_SETTINGS = "cloud-settings"
    const val CLOUD_SERVICES = "cloud-services"
    const val CLOUD_PROVIDER_PATTERN = "cloud-provider/{providerId}"
    const val SERVERS = "servers"
    const val AI_SETTINGS = "ai-settings"
    const val UPDATE = "update"

    const val GENERATE_PATTERN = "generate?path={path}&name={name}"
    const val TASK_PATTERN = "task/{taskId}"
    const val STORYBOARD_DETAIL_PATTERN = "storyboard/{projectId}"

    fun generate(path: String, name: String): String =
        "generate?path=${Uri.encode(path)}&name=${Uri.encode(name)}"

    fun task(taskId: String): String = "task/${Uri.encode(taskId)}"

    fun storyboard(projectId: String): String = "storyboard/${Uri.encode(projectId)}"

    fun cloudProvider(providerId: String): String = "cloud-provider/${Uri.encode(providerId)}"
}
