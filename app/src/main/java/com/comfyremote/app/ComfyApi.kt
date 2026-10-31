package com.comfyremote.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okio.ByteString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

open class ComfyException(message: String) : IOException(message)

/** Thrown when ComfyUI rejects a queued prompt (node_errors present). */
class PromptValidationException(message: String) : ComfyException(message)

data class SysInfo(val version: String, val device: String, val vramGb: String)

data class WorkflowEntry(val path: String, val name: String, val sizeKb: String)

data class OutputImage(val filename: String, val subfolder: String, val type: String)

sealed class WsEvent {
    class Json(val obj: JSONObject) : WsEvent()
    class Preview(val bitmap: Bitmap) : WsEvent()
}

class ComfyApi(base: String) {
    val base = base.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun urlEnc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun httpGet(path: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(base + path).build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw ComfyException("HTTP ${resp.code}: ${text.take(200)}")
            text
        }
    }

    private suspend fun httpPost(path: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url(base + path)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        ).execute().use { resp -> Pair(resp.code, resp.body?.string() ?: "") }
    }

    suspend fun systemStats(): SysInfo {
        val o = JSONObject(httpGet("/system_stats"))
        val sys = o.optJSONObject("system")
        var device = ""
        var vram = ""
        val devs = o.optJSONArray("devices")
        if (devs != null && devs.length() > 0) {
            val d = devs.optJSONObject(0)
            if (d != null) {
                device = d.optString("name").substringAfter("cuda:0 ").ifEmpty { d.optString("name") }
                vram = String.format("%.1fGB", d.optLong("vram_total") / 1e9)
            }
        }
        return SysInfo(sys?.optString("comfyui_version") ?: "?", device, vram)
    }

    suspend fun listWorkflows(): List<WorkflowEntry> {
        val arr = JSONArray(httpGet("/userdata?dir=workflows&recurse=true&split=false&full_info=true"))
        val out = mutableListOf<WorkflowEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val p = o.optString("path")
            if (p.endsWith(".json")) {
                out.add(
                    WorkflowEntry(
                        p,
                        p.removeSuffix(".json").substringAfterLast('/'),
                        String.format("%.1f KB", o.optLong("size") / 1024.0)
                    )
                )
            }
        }
        return out.sortedBy { it.name }
    }

    suspend fun fetchWorkflow(path: String): String = httpGet("/userdata/" + urlEnc("workflows/$path"))

    suspend fun objectInfo(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(httpGet("/object_info"))
    }

    suspend fun queuePrompt(prompt: JSONObject, clientId: String): String {
        val payload = JSONObject().put("prompt", prompt).put("client_id", clientId)
        val (code, body) = httpPost("/prompt", payload.toString())
        if (code != 200) {
            val formatted = formatPromptError(code, body)
            if (body.contains("node_errors") || body.contains("prompt_outputs_failed_validation"))
                throw PromptValidationException(formatted)
            throw ComfyException(formatted)
        }
        val id = JSONObject(body).optString("prompt_id")
        if (id.isEmpty()) throw ComfyException("服务器未返回 prompt_id")
        return id
    }

    private fun formatPromptError(code: Int, body: String): String = try {
        val o = JSONObject(body)
        val sb = StringBuilder(o.optJSONObject("error")?.optString("message") ?: "HTTP $code")
        val nodeErrors = o.optJSONObject("node_errors")
        nodeErrors?.let { ne ->
            ne.keys().forEach { nodeId ->
                val e = ne.optJSONObject(nodeId) ?: return@forEach
                val errs = e.optJSONArray("errors") ?: return@forEach
                for (i in 0 until errs.length()) {
                    val err = errs.optJSONObject(i) ?: continue
                    sb.append("\n节点 #").append(nodeId).append(" ").append(e.optString("class_type"))
                        .append(": ").append(err.optString("message"))
                    val d = err.optString("details")
                    if (d.isNotEmpty()) sb.append("（").append(d).append("）")
                }
            }
        }
        sb.toString()
    } catch (ex: Exception) {
        "HTTP $code: ${body.take(300)}"
    }

    /** Full history entry for one prompt (null while not finished / unknown). */
    suspend fun historyEntry(promptId: String): JSONObject? = withContext(Dispatchers.IO) {
        val root = JSONObject(httpGet("/history/$promptId"))
        root.optJSONObject(promptId)
    }

    suspend fun downloadImage(img: OutputImage): ByteArray = withContext(Dispatchers.IO) {
        val url = "$base/view?filename=${urlEnc(img.filename)}&subfolder=${urlEnc(img.subfolder)}&type=${urlEnc(img.type)}"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ComfyException("下载图片失败 HTTP ${resp.code}")
            resp.body?.bytes() ?: throw ComfyException("下载图片失败：空响应")
        }
    }

    suspend fun uploadImage(data: ByteArray, filename: String): String = withContext(Dispatchers.IO) {
        val ext = filename.substringAfterLast('.', "png").lowercase()
        val mime = (when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }).toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", filename, data.toRequestBody(mime))
            .build()
        client.newCall(Request.Builder().url("$base/upload/image?overwrite=true").post(body).build())
            .execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ComfyException("上传失败 HTTP ${resp.code}: ${text.take(200)}")
                val o = JSONObject(text)
                val name = o.optString("name", filename)
                val sub = o.optString("subfolder", "")
                if (sub.isNotEmpty()) "$sub/$name" else name
            }
    }

    /** Loads an image from the ComfyUI input directory (for reference thumbnails). */
    suspend fun fetchInputImage(name: String): Bitmap = withContext(Dispatchers.IO) {
        val url = "$base/view?filename=${urlEnc(name)}&type=input"
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ComfyException("HTTP ${resp.code}")
            BitmapFactory.decodeStream(resp.body?.byteStream()) ?: throw ComfyException("decode failed")
        }
    }

    suspend fun interrupt() {
        httpPost("/interrupt", "{}")
    }

    /** Removes a queued (not yet running) prompt from the server queue. */
    suspend fun deleteFromQueue(promptId: String) {
        httpPost("/queue", JSONObject().put("delete", org.json.JSONArray().put(promptId)).toString())
    }

    fun wsEvents(clientId: String): Flow<WsEvent> = callbackFlow {
        val req = Request.Builder()
            .url(base.replaceFirst("http", "ws") + "/ws?clientId=" + clientId)
            .build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    trySend(WsEvent.Json(JSONObject(text)))
                } catch (_: Exception) {
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // [0..3]=event type, [4..7]=image format, [8..]=jpeg/png data
                if (bytes.size > 8) {
                    try {
                        val bmp = BitmapFactory.decodeByteArray(bytes.toByteArray(), 8, bytes.size - 8)
                        if (bmp != null) trySend(WsEvent.Preview(bmp))
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })
        awaitClose { ws.cancel() }
    }.buffer(256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
