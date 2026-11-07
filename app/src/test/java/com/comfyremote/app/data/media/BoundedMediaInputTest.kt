package com.comfyremote.app.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BoundedMediaInputTest {
    @Test
    fun truncatedAndUnboundedStreamsAreRejectedBeforeWritingPastLimit() {
        val destination = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            BoundedMediaInput.copy(ByteArrayInputStream(ByteArray(9)), destination, 8)
        }
        assertEquals(0, destination.size())
    }

    @Test
    fun exactSizeAndUnknownContentLengthAreSupported() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(bytes, BoundedMediaInput.read(ByteArrayInputStream(bytes), 4))
        val output = ByteArrayOutputStream()
        assertEquals(4L, BoundedMediaInput.copy(ByteArrayInputStream(bytes), output, 4))
        assertArrayEquals(bytes, output.toByteArray())
    }
}
