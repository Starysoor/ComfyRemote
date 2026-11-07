package com.comfyremote.app.data.project

import com.comfyremote.app.data.reference.AssetKind
import com.comfyremote.app.data.reference.ReferenceRole
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectAssetStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = ProjectAssetStore(temp.newFolder("assets"))

    @Test
    fun resolveAssetKindPrefersExplicitMediaMimes() {
        assertEquals(AssetKind.VIDEO, ProjectAssetStore.resolveAssetKind("video/mp4", "clip.bin"))
        assertEquals(AssetKind.AUDIO, ProjectAssetStore.resolveAssetKind("audio/mpeg", "clip.bin"))
        assertEquals(AssetKind.VIDEO, ProjectAssetStore.resolveAssetKind(null, "clip.mov"))
        assertEquals(AssetKind.AUDIO, ProjectAssetStore.resolveAssetKind(null, "voice.flac"))
        assertEquals(AssetKind.SUBTITLE, ProjectAssetStore.resolveAssetKind("text/vtt", "subs.vtt"))
        assertEquals(AssetKind.SUBTITLE, ProjectAssetStore.resolveAssetKind(null, "subs.srt"))
        // Images (explicit or by extension) and unknown types stay images.
        assertEquals(AssetKind.IMAGE, ProjectAssetStore.resolveAssetKind("image/png", "p.png"))
        assertEquals(AssetKind.IMAGE, ProjectAssetStore.resolveAssetKind(null, "p.png"))
        assertEquals(AssetKind.IMAGE, ProjectAssetStore.resolveAssetKind(null, null))
    }

    @Test
    fun videoFilesAreCopiedVerbatimWithTheirExtension() = runBlocking {
        val store = store()
        val source = temp.newFile("scene.mp4").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }

        val asset = store.importFile(source.absolutePath, ReferenceRole.SCENE_REFERENCE, "开场", listOf("场景", "v1"))

        val saved = File(asset.path)
        assertEquals(AssetKind.VIDEO, asset.kind)
        assertEquals("开场", asset.label)
        assertEquals(listOf("场景", "v1"), asset.tags)
        assertTrue(saved.name.startsWith("asset_") && saved.name.endsWith(".mp4"))
        assertTrue(source.readBytes().contentEquals(saved.readBytes()))
        assertTrue(saved.parentFile!!.name == "assets")
    }

    @Test
    fun audioFilesKeepTheirContainerExtension() = runBlocking {
        val store = store()
        val source = temp.newFile("voice.m4a").apply { writeBytes(ByteArray(128)) }

        val asset = store.importFile(source.absolutePath, ReferenceRole.GENERIC_REFERENCE, "旁白")

        assertEquals(AssetKind.AUDIO, asset.kind)
        assertTrue(File(asset.path).name.endsWith(".m4a"))
    }

    @Test
    fun oversizedMediaIsRejectedBeforeAnyCopy() {
        val store = store()
        // Inflate the length without writing 32 MiB of actual data.
        val oversized = temp.newFile("huge.mp4")
        java.io.RandomAccessFile(oversized, "rw").use { raf -> raf.setLength(32L * 1024 * 1024 + 1) }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                store.importFile(oversized.absolutePath, ReferenceRole.GENERIC_REFERENCE, "big")
            }
        }
        assertEquals(0, store.listImportedFiles().size)
    }

    @Test
    fun missingMediaFileFailsWithoutCreatingAnything() {
        val store = store()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                store.importFile("/nonexistent/clip.mp4", ReferenceRole.GENERIC_REFERENCE, "x")
            }
        }
        assertEquals(0, store.listImportedFiles().size)
    }
}
