package com.comfyremote.app.data.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoryboardStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = StoryboardStore(File(temp.root, "storyboards.json"))

    @Test
    fun nextShotWaitsForAnEarlierRunningShot() {
        val store = store()
        val project = store.create("Board", "wf")
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        val third = store.addShot(project.id, "third")!!

        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        assertTrue(store.attachTask(project.id, second.id, "task-1"))

        // The first pending shot is running, so later drafts must wait for its last frame.
        assertNull(store.nextShot(project.id))

        store.markDone(project.id, second.id, "/tmp/second.mp4", "/tmp/second.png")
        assertEquals(third.id, store.nextShot(project.id)?.id)
    }

    @Test
    fun attachTaskDoesNotOverwriteARunningShot() {
        val store = store()
        val project = store.create("Board", "wf")
        val shot = store.addShot(project.id, "shot")!!

        assertTrue(store.attachTask(project.id, shot.id, "task-1"))
        assertFalse(store.attachTask(project.id, shot.id, "task-2"))

        assertEquals("task-1", store.project(project.id)?.shots?.single()?.taskId)
    }

    @Test
    fun deletingAShotRebasesTheNextReference() {
        val store = store()
        val project = store.create("Board", "wf")
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        val third = store.addShot(project.id, "third")!!

        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        store.markDone(project.id, second.id, "/tmp/second.mp4", "/tmp/second.png")
        store.setReference(project.id, second.id, "/tmp/first.png", manual = false)
        store.setReference(project.id, third.id, "/tmp/second.png", manual = false)

        store.deleteShot(project.id, second.id)

        assertEquals("/tmp/first.png", store.project(project.id)?.shots?.last()?.referencePath)
    }

    @Test
    fun deletingAShotDoesNotRebaseAManualReference() {
        val store = store()
        val project = store.create("Board", "wf")
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        val third = store.addShot(project.id, "third")!!

        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        store.markDone(project.id, second.id, "/tmp/second.mp4", "/tmp/second.png")
        store.setReference(project.id, third.id, "/tmp/second.png", manual = true)

        store.deleteShot(project.id, second.id)

        val remaining = store.project(project.id)?.shots?.last()
        assertEquals("/tmp/second.png", remaining?.referencePath)
        assertTrue(remaining?.referenceManual == true)
    }

    @Test
    fun deletingAProjectCleansOnlyUnreferencedFrames() {
        val store = store()
        val mediaDir = File(temp.root, "storyboards").apply { mkdirs() }
        val keepFile = File(mediaDir, "keep.png").apply { writeText("keep") }
        val orphan = File(mediaDir, "orphan.png").apply { writeText("orphan") }

        val keep = store.create("Keep", "wf")
        val keepShot = store.addShot(keep.id, "shot")!!
        store.markDone(keep.id, keepShot.id, "/tmp/result.mp4", keepFile.absolutePath)
        val drop = store.create("Drop", "wf")
        orphan.setLastModified(System.currentTimeMillis() - 60 * 60_000L)

        store.delete(drop.id)

        assertTrue(keepFile.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun referencedMediaPathsCoverResultsFramesAndReferences() {
        val store = store()
        val project = store.create("Board", "wf")
        val shot = store.addShot(project.id, "shot")!!
        store.markDone(project.id, shot.id, "/tmp/result.mp4", "/tmp/last.png")
        store.setReference(project.id, shot.id, "/tmp/reference.png")

        assertEquals(
            setOf("/tmp/result.mp4", "/tmp/last.png", "/tmp/reference.png"),
            store.referencedMediaPaths(),
        )
    }
}
