package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Shared HTTP plumbing for official/custom cloud providers. */
object CloudHttp {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonType))
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    fun download(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        if (url.startsWith("data:")) {
            return Base64.getDecoder().decode(url.substringAfter(','))
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code} while downloading result")
            return response.body?.bytes() ?: throw ComfyException("Empty result download")
        }
    }

    fun downloadTo(url: String, target: File, headers: Map<String, String> = emptyMap()) {
        if (url.startsWith("data:")) {
            target.parentFile?.mkdirs()
            target.writeBytes(Base64.getDecoder().decode(url.substringAfter(',')))
            return
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ComfyException("HTTP ${response.code} while downloading result")
                val body = response.body ?: throw ComfyException("Empty result download")
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun errorMessage(root: JSONObject, fallback: String): String =
        root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
            ?: fallback
}
