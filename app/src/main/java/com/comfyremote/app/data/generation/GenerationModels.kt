package com.comfyremote.app.data.generation

import android.graphics.Bitmap
import com.comfyremote.app.ComfyException
import com.comfyremote.app.R
import com.comfyremote.app.ui.AppStrings
import com.comfyremote.app.PromptField
import org.json.JSONObject
import java.util.UUID

/** Immutable snapshot of what the user asked for. Editing after submit cannot affect it. */
data class GenerationRequest(
    val id: String = UUID.randomUUID().toString(),
    /** "comfyui" for a direct server, "autodl" for AutoDL's hosted workflow API. */
    val provider: String = "comfyui",
    val serverUrl: String,
    val workflowPath: String,
    val workflowName: String,
    val fieldValues: Map<String, String>,
    val randomSeed: Boolean,
    val nodeTitles: Map<String, String>,
    val fields: List<PromptField>,
    val convertedPrompt: JSONObject,
    val createdAt: Long = System.currentTimeMillis(),
    /** Best-effort platform price estimate captured at submit time. */
    val estimatedCloudCost: Int? = null,
)

enum class GenerationPhase { PREPARING, QUEUEING, QUEUED, RUNNING, CANCELLING, CANCELLED, SUCCEEDED, FAILED }

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
    val nodeTitle: String? = null,
)

class GenerationTimeoutException(message: String) : ComfyException(message)

data class GenerationTask(
    val taskId: String,
    val request: GenerationRequest,
    val phase: GenerationPhase = GenerationPhase.PREPARING,
    val promptId: String? = null,
    val clientId: String? = null,
    val queueRemaining: Int? = null,
    /** 1-based position among pending prompts when the server can report it. */
    val queuePosition: Int? = null,
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
    /** Actual seed values used by the submitted prompt, persisted before completion for recovery. */
    val seedValues: Map<String, String> = emptyMap(),
    /** True when this runtime task was restored from disk after process recreation. */
    val recovered: Boolean = false,
    val recoveryNote: String? = null,
    /** True while the request is intentionally waiting in the app-side queue and has not reached ComfyUI yet. */
    val localPending: Boolean = false,
    /** Optional batch identity shared by variation jobs created together. */
    val batchId: String? = null,
    /** 1-based position within a batch. */
    val batchIndex: Int? = null,
    val batchSize: Int? = null,
) {
    val isActive: Boolean
        get() = phase == GenerationPhase.PREPARING || phase == GenerationPhase.QUEUEING ||
            phase == GenerationPhase.QUEUED || phase == GenerationPhase.RUNNING || phase == GenerationPhase.CANCELLING

    val isTerminal: Boolean
        get() = phase == GenerationPhase.CANCELLED || phase == GenerationPhase.SUCCEEDED || phase == GenerationPhase.FAILED

    val isRemoteQueued: Boolean
        get() = !localPending && (phase == GenerationPhase.QUEUEING || phase == GenerationPhase.QUEUED)

    fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
        val start = startedAt ?: createdAt
        val end = completedAt ?: now
        return (end - start).coerceAtLeast(0L)
    }
}

fun GenerationTask.label(): String {
    if (localPending) return AppStrings.get(R.string.phase_local_pending)
    return when (phase) {
        GenerationPhase.PREPARING -> AppStrings.get(R.string.phase_preparing)
        GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> AppStrings.get(R.string.phase_queued)
        GenerationPhase.RUNNING -> AppStrings.get(R.string.phase_running)
        GenerationPhase.CANCELLING -> AppStrings.get(R.string.phase_cancelling)
        GenerationPhase.CANCELLED -> AppStrings.get(R.string.phase_cancelled)
        GenerationPhase.SUCCEEDED -> AppStrings.get(R.string.phase_succeeded)
        GenerationPhase.FAILED -> AppStrings.get(R.string.phase_failed)
    }
}

fun GenerationError.uiLabel(): String = when (kind) {
    ErrorKind.EXECUTION -> AppStrings.get(R.string.error_execution)
    ErrorKind.CONNECTION -> AppStrings.get(R.string.error_connection)
    ErrorKind.PROMPT_VALIDATION -> AppStrings.get(R.string.error_validation)
    ErrorKind.TIMEOUT -> AppStrings.get(R.string.error_timeout)
    ErrorKind.DOWNLOAD -> AppStrings.get(R.string.error_download)
    ErrorKind.CANCELLED -> AppStrings.get(R.string.error_cancelled)
    ErrorKind.UNKNOWN -> AppStrings.get(R.string.error_unknown)
}
