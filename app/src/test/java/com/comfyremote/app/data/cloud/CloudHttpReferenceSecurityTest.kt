package com.comfyremote.app.data.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

class CloudHttpReferenceSecurityTest {

    @Test
    fun remoteReferenceImageNeverReceivesProviderCredentials() {
        var requestHeaders = ""
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
                requestHeaders = headerBuffer.toByteArray().toString(Charsets.ISO_8859_1)
                val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: image/png\r\n" +
                                "Content-Length: ${image.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.ISO_8859_1)
                    )
                    write(image)
                    flush()
                }
            }
        }
        try {
            val payload = CloudHttp.imagePayload(
                "http://127.0.0.1:${server.localPort}/reference.png",
            )

            assertNotNull(payload)
            assertFalse(requestHeaders.contains("Authorization", ignoreCase = true))
            assertFalse(requestHeaders.contains("x-goog-api-key", ignoreCase = true))
        } finally {
            server.close()
            serving.join(2_000)
        }
    }
}
