package com.comfyremote.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypeResolverTest {

    @Test
    fun viewerSaveAndShareAgreeOnVideoFormats() {
        listOf("clip.mp4", "clip.webm", "clip.mov", "clip.m4v", "clip.mkv", "clip.avi", "clip.3gp").forEach {
            assertEquals(it, MediaKind.VIDEO, MediaTypeResolver.kind(it))
            assertTrue(it, MediaTypeResolver.isVideo(it))
            assertFalse(it, MediaTypeResolver.isAudio(it))
            assertTrue(it, MediaTypeResolver.mimeType(it).startsWith("video/"))
        }
    }

    @Test
    fun audioFormatsStayAudio() {
        listOf("voice.wav", "voice.mp3", "voice.m4a", "voice.ogg", "voice.flac", "voice.aac").forEach {
            assertEquals(it, MediaKind.AUDIO, MediaTypeResolver.kind(it))
            assertTrue(it, MediaTypeResolver.mimeType(it).startsWith("audio/"))
        }
    }

    @Test
    fun aviAndThreeGpHaveConcreteMimeTypes() {
        assertEquals("video/x-msvideo", MediaTypeResolver.mimeType("clip.avi"))
        assertEquals("video/3gpp", MediaTypeResolver.mimeType("clip.3gp"))
        assertEquals("video/x-matroska", MediaTypeResolver.mimeType("clip.mkv"))
    }

    @Test
    fun imagesRemainTheFallback() {
        assertEquals(MediaKind.IMAGE, MediaTypeResolver.kind("result.png"))
        assertFalse(MediaTypeResolver.isVideo("result.png"))
        assertEquals("image/png", MediaTypeResolver.mimeType("result.png"))
    }
}
