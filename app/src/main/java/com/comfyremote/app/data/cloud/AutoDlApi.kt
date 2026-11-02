package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for AutoDL.Art's hosted ComfyUI workflow API.
 *
 * Submission is asynchronous: POST returns a task id, then the result endpoint
 * is polled. The token is the raw value of a ComfyUI-group token (no Bearer
 * prefix), matching AutoDL's documentation.
 */
class AutoDlApi(private val base: String = AutoDlConfig.BASE_URL) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun listWorkflows(): List<CloudWorkflowSummary> = withContext(Dispatchers.IO) {
        val all = mutableListOf<CloudWorkflowSummary>()
        var page = 1
        var maxPage = 1
        do {
            val root = postJson(
                "/api/v1/comfyui/workflows",
                JSONObject().put("page_index", page).put("page_size", 100),
                token = null,
            )
            ensureSuccess(root)
            val (items, max) = CloudWorkflowParser.list(root)
            all += items
            maxPage = max
            page++
        } while (page <= maxPage)
        all.distinctBy { it.id }
    }

    suspend fun workflowDetail(workflowId: String): CloudWorkflowSpec = withContext(Dispatchers.IO) {
        val root = getJson("/api/v1/comfyui/workflows/${urlEnc(workflowId)}", token = null)
        ensureSuccess(root)
        CloudWorkflowParser.detail(root)
    }

    suspend fun submit(workflowId: String, body: JSONObject, token: String): String =
        withContext(Dispatchers.IO) {
            val root = postJson(
                "/api/v1/comfyui/comfyui_workflow/${urlEnc(workflowId)}",
                body,
                token,
            )
            val data = dataOrThrow(root)
            data.optString("task_id").takeIf { it.isNotBlank() }
                ?: throw ComfyException(root.optString("msg").ifBlank { "AutoDL 未返回 task_id" })
        }

    suspend fun result(taskId: String, token: String): CloudTaskResult = withContext(Dispatchers.IO) {
        val root = getJson(
            "/api/v1/comfyui/comfyui_workflow/result/${urlEnc(taskId)}",
            token,
        )
        ensureSuccess(root)
        CloudWorkflowParser.taskResult(root)
    }

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw ComfyException("下载云端结果失败 HTTP ${response.code}")
            response.body?.bytes() ?: throw ComfyException("下载云端结果失败：空响应")
        }
    }

    private fun getJson(path: String, token: String?): JSONObject {
        val builder = Request.Builder().url(base.trimEnd('/') + path).get()
        token?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", it) }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    private fun postJson(path: String, body: JSONObject, token: String?): JSONObject {
        val builder = Request.Builder()
            .url(base.trimEnd('/') + path)
            .post(body.toString().toRequestBody(jsonType))
        token?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", it) }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    private fun ensureSuccess(root: JSONObject) {
        val code = root.optString("code")
        if (!code.equals("Success", ignoreCase = true)) {
            val message = root.optString("msg")
                .ifBlank { root.optJSONObject("data")?.optString("message").orEmpty() }
                .ifBlank { code.ifBlank { "AutoDL 请求失败" } }
            throw ComfyException(message)
        }
    }

    private fun dataOrThrow(root: JSONObject): JSONObject {
        ensureSuccess(root)
        return root.optJSONObject("data") ?: JSONObject()
    }

    private fun urlEnc(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
