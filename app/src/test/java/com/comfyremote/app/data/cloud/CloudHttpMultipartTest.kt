package com.comfyremote.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.assertThrows
import java.net.ServerSocket
import java.util.Base64
import kotlin.concurrent.thread

class CloudHttpMultipartTest {

    @Test
    fun dataUrlReferenceDecodesIntoBytes() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val value = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)

        val payload = CloudHttp.imagePayload(value)

        assertEquals("image/png", payload?.first)
        assertTrue(payload!!.second.contentEquals(bytes))
    }

    @Test
    fun oversizedReferenceDownloadsAreRejectedEvenWithoutContentLength() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
        assertThrows(com.comfyremote.app.ComfyException::class.java) {
            CloudHttp.readBounded(input, 4)
        }
    }

    @Test
    fun referenceDownloadLimitAllowsAnExactSize() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        assertTrue(CloudHttp.readBounded(input, 4).contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun multipleReferencePartsCanShareTheOpenAiImageField() {
        var body = ""
        val server = ServerSocket(0)
        val serving = thread(start = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val headerBuffer = ByteArrayOutputStream()
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    headerBuffer.write(b)
                    val bytes = headerBuffer.toByteArray()
                    if (bytes.size >= 4 &&
                        bytes[bytes.size - 4] == 13.toByte() &&
                        bytes[bytes.size - 3] == 10.toByte() &&
                        bytes[bytes.size - 2] == 13.toByte() &&
                        bytes[bytes.size - 1] == 10.toByte()
                    ) break
                }
                val headerText = headerBuffer.toByteArray().toString(Charsets.ISO_8859_1)
                val headers = headerText.split("\r\n").drop(1).mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) null
                    else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
                }.toMap()
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = input.read(bodyBytes, read, length - read)
                    if (count < 0) break
                    read += count
                }
                body = bodyBytes.toString(Charsets.ISO_8859_1)
                val response = """{"data":[{"url":"https://example.com/out.png"}]}"""
                    .toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${response.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1)
                    )
                    write(response)
                    flush()
                }
            }
        }
        try {
            val first = byteArrayOf(0x01, 0x02)
            val second = byteArrayOf(0x03, 0x04)
            CloudHttp.postMultipart(
                url = "http://127.0.0.1:${server.localPort}/images/edits",
                fields = mapOf("model" to "gpt-image-2.5", "prompt" to "combine"),
                files = listOf(
                    CloudFilePart("image[]", "reference1.png", "image/png", first),
                    CloudFilePart("image[]", "reference2.png", "image/png", second),
                ),
                headers = mapOf("Authorization" to "Bearer test"),
            )

            assertTrue(body.contains("name=\"image[]\"; filename=\"reference1.png\""))
            assertTrue(body.contains("name=\"image[]\"; filename=\"reference2.png\""))
        } finally {
            server.close()
            serving.join(2_000)
        }
    }

    @Test
    fun openAiEditRequestSendsARealMultipartFile() {
        var contentType = ""
        var body = ""
        val server = ServerSocket(0)
        val responseJson = """{"data":[{"url":"https://example.com/out.png"}]}"""
        val serving = thread(start = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val headerBuffer = ByteArrayOutputStream()
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    headerBuffer.write(b)
                    val bytes = headerBuffer.toByteArray()
                    if (bytes.size >= 4 &&
                        bytes[bytes.size - 4] == 13.toByte() &&
                        bytes[bytes.size - 3] == 10.toByte() &&
                        bytes[bytes.size - 2] == 13.toByte() &&
                        bytes[bytes.size - 1] == 10.toByte()
                    ) break
                }
                val headerText = headerBuffer.toByteArray().toString(Charsets.ISO_8859_1)
                val headers = headerText.split("\r\n").drop(1).mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) null
                    else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
                }.toMap()
                contentType = headers["content-type"].orEmpty()
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = input.read(bodyBytes, read, length - read)
                    if (count < 0) break
                    read += count
                }
                body = bodyBytes.toString(Charsets.ISO_8859_1)
                val response = responseJson.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${response.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1)
                    )
                    write(response)
                    flush()
                }
            }
        }
        try {
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
            val payload = CloudHttp.imagePayload(
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png),
            )!!
            val response = CloudHttp.postMultipart(
                url = "http://127.0.0.1:${server.localPort}/images/edits",
                fields = mapOf("model" to "gpt-image-2.5", "prompt" to "a red fox"),
                files = listOf(
                    CloudFilePart("image", "reference.png", payload.first, payload.second)
                ),
                headers = mapOf("Authorization" to "Bearer test"),
            )

            assertEquals("https://example.com/out.png", response.getJSONArray("data").getJSONObject(0).getString("url"))
            assertTrue(contentType.startsWith("multipart/form-data"))
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("gpt-image-2.5"))
            assertTrue(body.contains("name=\"prompt\""))
            assertTrue(body.contains("a red fox"))
            assertTrue(body.contains("name=\"image\"; filename=\"reference.png\""))
        } finally {
            server.close()
            serving.join(2_000)
        }
    }
}
