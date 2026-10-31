package com.comfyremote.app.data.generation

import android.graphics.Bitmap
import com.comfyremote.app.PromptField
import org.json.JSONObject
import java.util.UUID

/** Immutable snapshot of what the user asked for. Editing after submit cannot affect it. */
data class GenerationRequest(
    val id: String = UUID.randomUUID().toString(),
    val serverUrl: String,
    val workflowPath: String,
    val workflowName: String,
    val fieldValues: Map<String, String>,
    val randomSeed: Boolean,
    val nodeTitles: Map<String, String>,
    val fields: List<PromptField>,
    val convertedPrompt: JSONObject,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class GenerationPhase {
    PREPARING, QUEUEING, QUEUED, RUNNING, CANCELLING, CANCELLED, SUCCEEDED, FAILED
}

/** A generated image, referenced by its on-device file (never a Bitmap in task state). */
data class ResultImage(
    val id: String,
    val localPath: String,
    val width: Int? = null,
    val height: Int? = null,
)

enum class ErrorKind { CONNECTION, PROMPT_VALIDATION, EXECUTION, TIMEOUT, DOWNLOAD, CANCELLED, UNKNOWN }

data class GenerationError(
    val kind: ErrorKind,
    val message: String,
    val nodeId: String? = null,
    val nodeType: String? = null,
)

/** One generation job with its full observable state. */
data class GenerationTask(
    val taskId: String,
    val request: GenerationRequest,
    val phase: GenerationPhase = GenerationPhase.PREPARING,
    val promptId: String? = null,
    val clientId: String? = null,
    val queueRemaining: Int? = null,
    val nodeId: String? = null,
    val nodeTitle: String? = null,
    val progress: Float? = null,
    val preview: Bitmap? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val results: List<ResultImage> = emptyList(),
    val error: GenerationError? = null,
    val seedInfo: String = "",
) {
    val isActive: Boolean
        get() = phase == GenerationPhase.PREPARING || phase == GenerationPhase.QUEUEING ||
                phase == GenerationPhase.QUEUED || phase == GenerationPhase.RUNNING ||
                phase == GenerationPhase.CANCELLING

    val isTerminal: Boolean
        get() = phase == GenerationPhase.CANCELLED || phase == GenerationPhase.SUCCEEDED ||
                phase == GenerationPhase.FAILED
}

/** Localized status label shared by the Tasks page and the generate screen. */
fun GenerationTask.label(): String = when (phase) {
    GenerationPhase.PREPARING -> com.comfyremote.app.ui.L10n.t("准备中", "Preparing")
    GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> com.comfyremote.app.ui.L10n.t("排队中", "Queued")
    GenerationPhase.RUNNING -> com.comfyremote.app.ui.L10n.t("生成中", "Running")
    GenerationPhase.CANCELLING -> com.comfyremote.app.ui.L10n.t("取消中", "Cancelling")
    GenerationPhase.CANCELLED -> com.comfyremote.app.ui.L10n.t("已取消", "Cancelled")
    GenerationPhase.SUCCEEDED -> com.comfyremote.app.ui.L10n.t("完成", "Done")
    GenerationPhase.FAILED -> com.comfyremote.app.ui.L10n.t("失败", "Failed")
}

/** Localized error kind label for UI display. */
fun com.comfyremote.app.data.generation.GenerationError.uiLabel(): String = when (kind) {
    ErrorKind.EXECUTION -> com.comfyremote.app.ui.L10n.t("执行错误", "Execution error")
    ErrorKind.CONNECTION -> com.comfyremote.app.ui.L10n.t("连接错误", "Connection error")
    ErrorKind.PROMPT_VALIDATION -> com.comfyremote.app.ui.L10n.t("参数校验失败", "Validation failed")
    ErrorKind.TIMEOUT -> com.comfyremote.app.ui.L10n.t("等待超时", "Timed out")
    ErrorKind.DOWNLOAD -> com.comfyremote.app.ui.L10n.t("下载失败", "Download failed")
    ErrorKind.CANCELLED -> com.comfyremote.app.ui.L10n.t("已取消", "Cancelled")
    ErrorKind.UNKNOWN -> com.comfyremote.app.ui.L10n.t("未知错误", "Unknown error")
}
