package com.comfyremote.app.data.cloud

import com.comfyremote.app.ComfyException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/** One binary part of a multipart request (reference images, masks, audio). */
data class CloudFilePart(
    val fieldName: String,
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
)

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

    fun requestJson(
        method: String,
        url: String,
        body: JSONObject?,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(jsonType))
            "PUT" -> builder.put((body ?: JSONObject()).toString().toRequestBody(jsonType))
            else -> throw ComfyException("Unsupported HTTP method $method")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    /** Status-only GET probe for health endpoints that may not return JSON. */
    fun probe(url: String, headers: Map<String, String> = emptyMap()) {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            }
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

    /** Multipart POST used by OpenAI `/images/edits` and similar endpoints. */
    fun postMultipart(
        url: String,
        fields: Map<String, String>,
        files: List<CloudFilePart>,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
            files.forEach { file ->
                addFormDataPart(
                    file.fieldName,
                    file.fileName,
                    file.bytes.toRequestBody(file.mime.toMediaType()),
                )
            }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ComfyException("HTTP ${response.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    /** Decodes a `data:image/...;base64,...` value into MIME type and bytes. */
    fun decodeDataUrl(value: String): Pair<String, ByteArray>? {
        if (!value.startsWith("data:")) return null
        val meta = value.substringAfter("data:").substringBefore(',')
        val payload = value.substringAfter(',', "")
        if (payload.isBlank()) return null
        val mime = meta.substringBefore(';').ifBlank { "image/png" }
        return runCatching { mime to Base64.getDecoder().decode(payload) }.getOrNull()
    }

    /**
     * Resolves a reference image value to bytes. Local values are converted to
     * data URLs by CloudInputStore, but remote URLs are supported too.
     *
     * Remote media is fetched without provider credentials: the URL host is not
     * the API host and must never receive Authorization / API-key headers.
     */
    fun imagePayload(value: String): Pair<String, ByteArray>? {
        decodeDataUrl(value)?.let { return it }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            val ext = value.substringBefore('?').substringAfterLast('.', "").lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/png"
            }
            return mime to download(value)
        }
        return null
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
