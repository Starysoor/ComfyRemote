package com.comfyremote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
}
