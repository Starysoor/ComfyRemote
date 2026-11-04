package com.comfyremote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class HistoryStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun directory() = temp.newFolder("history")

    private fun item(
        id: String,
        dir: File,
        images: List<File> = emptyList(),
        fields: List<PromptField> = emptyList(),
    ) = HistoryItem(
        id = id,
        wfPath = "wf-$id.json",
        wfName = "Workflow",
        time = System.currentTimeMillis(),
        values = mapOf("6:text" to "castle"),
        fields = fields,
        randomSeed = true,
        images = images.map { it.absolutePath },
        summary = "castle",
    )

    @Test
    fun fieldMetadataSurvivesTheIndexRoundTrip() {
        val dir = directory()
        val media = File(dir, "result.png").apply { writeText("image") }
        val fields = listOf(
            PromptField(
                key = "6:text",
                nodeId = "6",
                input = "text",
                label = "Positive Prompt",
                kind = FieldKind.TEXT,
                value = "",
            ),
            PromptField(
                key = "7:seed",
                nodeId = "7",
                input = "seed",
                label = "Seed",
                kind = FieldKind.SEED,
                value = "0",
            ),
        )
        val store = HistoryStore(dir)

        store.add(item("one", dir, listOf(media), fields))

        val loaded = store.load().single()
        assertEquals(listOf("6:text", "7:seed"), loaded.fields.map { it.key })
        assertEquals(FieldKind.TEXT, loaded.fields.first().kind)
        assertEquals("Positive Prompt", loaded.fields.first().label)
        assertFalse(File(dir, "index.json.tmp").exists())
    }

    @Test
    fun deleteManyKeepsMediaPinnedByAStoryboard() {
        val dir = directory()
        val pinned = File(dir, "pinned.mp4").apply { writeText("video") }
        val unused = File(dir, "unused.mp4").apply { writeText("video") }
        var protectedPaths = emptySet<String>()
        val store = HistoryStore(dir) { protectedPaths }
        store.add(item("one", dir, listOf(pinned)))
        store.add(item("two", dir, listOf(unused)))

        protectedPaths = setOf(pinned.absolutePath)
        store.deleteMany(listOf("one", "two"))

        assertTrue(pinned.exists())
        assertFalse(unused.exists())
    }

    @Test
    fun pruneRemovesOldOrphansButWaitsForFreshWrites() {
        val dir = directory()
        val indexed = File(dir, "indexed.png").apply { writeText("image") }
        val oldOrphan = File(dir, "old.png").apply { writeText("image") }
        oldOrphan.setLastModified(System.currentTimeMillis() - 2 * 60 * 60_000L)
        val store = HistoryStore(dir)

        store.add(item("one", dir, listOf(indexed)))
        assertFalse(oldOrphan.exists())

        val freshOrphan = File(dir, "fresh.png").apply { writeText("image") }
        store.add(item("two", dir, listOf(indexed)))

        // A runner may have written this file but not indexed it yet.
        assertTrue(freshOrphan.exists())
    }
    @Test
    fun deletingOneHistoryItemNeverDeletesMediaReferencedByAnother() {
        val dir = directory()
        val shared = File(dir, "shared.png").apply { writeText("important image") }
        val store = HistoryStore(dir)
        store.add(item("first", dir, listOf(shared)))
        store.add(item("second", dir, listOf(shared)))
        store.delete("first")
        assertEquals(listOf("second"), store.load().map { it.id })
        assertTrue(shared.isFile)
    }

    @Test
    fun aFailedIndexCommitNeverDeletesMediaOrItsRecord() {
        val dir = directory()
        var failReplacement = false
        val store = HistoryStore(dir, replaceIndex = { source, target ->
            if (failReplacement) throw IOException("Injected failed index commit")
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Unit
        })
        val first = File(dir, "one.png").apply { writeText("first") }
        val second = File(dir, "two.png").apply { writeText("second") }
        store.add(item("one", dir, listOf(first)))
        store.add(item("two", dir, listOf(second)))
        failReplacement = true
        assertTrue(runCatching { store.delete("one") }.exceptionOrNull() is IOException)
        assertTrue(runCatching { store.deleteMany(listOf("one", "two")) }.exceptionOrNull() is IOException)
        assertTrue(first.exists())
        assertTrue(second.exists())
        assertEquals(setOf("one", "two"), store.load().map { it.id }.toSet())
    }

    @Test
    fun corruptedHistoryIndexIsNotOverwrittenByASave() {
        val dir = directory()
        val store = HistoryStore(dir)
        val index = File(dir, "index.json").apply { writeText("{corrupted json") }
        assertTrue(runCatching { store.add(item("new", dir)) }.exceptionOrNull() is IOException)
        assertEquals("{corrupted json", index.readText())
    }

}
