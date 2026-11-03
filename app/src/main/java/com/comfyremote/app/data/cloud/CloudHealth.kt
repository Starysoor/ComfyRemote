package com.comfyremote.app.data.cloud

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Coarse, user-facing health of one cloud provider. */
enum class CloudHealthStatus {
    UNKNOWN,
    CHECKING,
    READY,
    CONFIGURED_UNVERIFIED,
    NOT_CONFIGURED,
    AUTH_FAILED,
    UNREACHABLE,
    ERROR,
}

data class CloudHealth(
    val status: CloudHealthStatus,
    val modelCount: Int = 0,
    val message: String = "",
    val checkedAt: Long = 0L,
)

/**
 * Maps provider failures to a stable status instead of showing raw exceptions.
 * [com.comfyremote.app.ComfyException] embeds the HTTP status in its message.
 */
internal fun classifyCloudFailure(error: Throwable): CloudHealth {
    val message = error.message.orEmpty()
    val code = Regex("HTTP (\\d{3})").find(message)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
    val status = when {
        code == 401 || code == 403 -> CloudHealthStatus.AUTH_FAILED
        code != null -> CloudHealthStatus.ERROR
        error is UnknownHostException ||
            error is ConnectException ||
            error is SocketTimeoutException ||
            message.contains("unable to resolve host", ignoreCase = true) ||
            message.contains("failed to connect", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) -> CloudHealthStatus.UNREACHABLE
        else -> CloudHealthStatus.ERROR
    }
    return CloudHealth(status = status, message = message.take(300))
}

/** Extracts model ids from common `/models` response shapes. */
internal fun modelIdsFromResponse(
    dataArray: org.json.JSONArray?,
    idKey: String = "id",
): List<String> {
    if (dataArray == null) return emptyList()
    return (0 until dataArray.length()).mapNotNull { index ->
        dataArray.optJSONObject(index)?.optString(idKey)?.takeIf { it.isNotBlank() }
    }
}
