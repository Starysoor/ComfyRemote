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
        val project = store.create("Board", "wf")!!
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
    fun loadingAProjectRebuildsTheChainAccordingToItsRelaySetting() {
        val file = File(temp.root, "storyboards.json")
        file.writeText(
            """[
              {"id":"chain-on","title":"On","workflowId":"wf","chainFrames":true,
               "updatedAt":2,"shots":[
                 {"id":"a","prompt":"a","lastFramePath":"/tmp/tail.png","status":"done"},
                 {"id":"b","prompt":"b","referenceManual":false}
               ]},
              {"id":"chain-off","title":"Off","workflowId":"wf","chainFrames":false,
               "updatedAt":1,"shots":[
                 {"id":"c","prompt":"c","lastFramePath":"/tmp/tail.png","status":"done"},
                 {"id":"d","prompt":"d","referencePath":"/tmp/tail.png","referenceManual":false}
               ]}
            ]"""
        )

        val store = StoryboardStore(file)

        assertEquals("/tmp/tail.png", store.project("chain-on")!!.shots[1].referencePath)
        assertNull(store.project("chain-off")!!.shots[1].referencePath)
    }

    @Test
    fun aProjectCanStartWithTailFrameRelayOff() {
        val store = store()
        val project = store.create("Board", "wf", chainFrames = false)!!

        assertFalse(project.chainFrames)
        assertFalse(store.project(project.id)!!.chainFrames)
    }

    @Test
    fun tailFrameRelayIsAnOptionalGameplay() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!

        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        assertEquals("/tmp/first.png", store.project(project.id)!!.shots[1].referencePath)

        store.setChainFrames(project.id, false)

        val chainedOff = store.project(project.id)!!
        assertFalse(chainedOff.chainFrames)
        assertNull(chainedOff.shots[1].referencePath)

        store.setReference(project.id, second.id, "/tmp/manual.png", manual = true)
        store.setChainFrames(project.id, true)

        val chainedOn = store.project(project.id)!!
        assertTrue(chainedOn.chainFrames)
        assertEquals("/tmp/manual.png", chainedOn.shots[1].referencePath)
        assertTrue(chainedOn.shots[1].referenceManual)
    }

    @Test
    fun addingTheSameAssetIdUpdatesItInsteadOfDuplicating() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = com.comfyremote.app.data.reference.ProjectAsset(
            id = "tail:shot-1",
            role = com.comfyremote.app.data.reference.ReferenceRole.END_FRAME,
            path = "/tmp/tail-1.png",
            label = "first tail",
        )
        val second = first.copy(path = "/tmp/tail-2.png", label = "second tail")

        store.addAsset(project.id, first)
        store.addAsset(project.id, second)

        val assets = store.project(project.id)!!.assets
        assertEquals(1, assets.size)
        assertEquals("/tmp/tail-2.png", assets.single().path)
        assertEquals("second tail", assets.single().label)
    }

    @Test
    fun aNewTailFrameIsMarkedForAssetAndResultImportAgain() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val shot = store.addShot(project.id, "shot")!!
        store.markDone(project.id, shot.id, "/tmp/result-1.mp4", "/tmp/tail-1.png")
        store.updateShot(project.id, shot.id) { it.copy(tailFrameStored = true) }

        store.markDone(project.id, shot.id, "/tmp/result-2.mp4", "/tmp/tail-2.png")

        val updated = store.project(project.id)!!.shots.single()
        assertEquals("/tmp/tail-2.png", updated.lastFramePath)
        assertFalse(updated.tailFrameStored)
    }

    @Test
    fun duplicatingAShotDoesNotInheritImportedTailState() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val shot = store.addShot(project.id, "shot")!!
        store.markDone(project.id, shot.id, "/tmp/result.mp4", "/tmp/tail.png")
        store.updateShot(project.id, shot.id) { it.copy(tailFrameStored = true) }

        val copy = store.duplicateShot(project.id, shot.id)!!

        assertFalse(copy.tailFrameStored)
        assertNull(copy.lastFramePath)
    }

    @Test
    fun attachTaskDoesNotOverwriteARunningShot() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val shot = store.addShot(project.id, "shot")!!

        assertTrue(store.attachTask(project.id, shot.id, "task-1"))
        assertFalse(store.attachTask(project.id, shot.id, "task-2"))

        assertEquals("task-1", store.project(project.id)?.shots?.single()?.taskId)
    }

    @Test
    fun deletingAShotRebasesTheNextReference() {
        val store = store()
        val project = store.create("Board", "wf")!!
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
        val project = store.create("Board", "wf")!!
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

        val keep = store.create("Keep", "wf")!!
        val keepShot = store.addShot(keep.id, "shot")!!
        store.markDone(keep.id, keepShot.id, "/tmp/result.mp4", keepFile.absolutePath)
        val drop = store.create("Drop", "wf")!!
        orphan.setLastModified(System.currentTimeMillis() - 60 * 60_000L)

        store.delete(drop.id)

        assertTrue(keepFile.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun moveDuplicateAndInsertKeepShotOrder() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!

        assertTrue(store.moveShot(project.id, second.id, -1))
        assertEquals(listOf(second.id, first.id), store.project(project.id)?.shots?.map { it.id })

        store.markDone(project.id, second.id, "/tmp/second.mp4", "/tmp/second.png")
        val copy = store.duplicateShot(project.id, second.id)!!
        val shots = store.project(project.id)!!.shots
        assertEquals(second.id, shots[0].id)
        assertEquals(copy.id, shots[1].id)
        assertEquals("draft", shots[1].status)
        assertNull(shots[1].taskId)
        assertNull(shots[1].resultPath)

        val inserted = store.insertShotAfter(project.id, first.id, "inserted")!!
        assertEquals(inserted.id, store.project(project.id)!!.shots.last().id)
    }

    @Test
    fun projectSettingsUpdateAndResetShotWorkflowOverrides() {
        val store = store()
        val project = store.create("Board", "old-workflow")!!
        val shot = store.addShot(project.id, "shot")!!
        store.updateShot(project.id, shot.id) {
            it.copy(
                workflowId = "old-workflow",
                workflowPath = "autodl:old-workflow",
                providerId = "autodl",
                values = mapOf("duration" to "5"),
            )
        }

        store.updateSettings(
            projectId = project.id,
            title = "New board",
            workflowPath = "autodl:new-workflow",
            providerId = "autodl",
            aspectRatio = "9:16",
            defaultValues = mapOf("resolution" to "768p"),
        )

        val updated = store.project(project.id)!!
        assertEquals("New board", updated.title)
        assertEquals("new-workflow", updated.workflowId)
        assertEquals("autodl:new-workflow", updated.workflowPath)
        assertEquals("autodl", updated.providerId)
        assertEquals("9:16", updated.aspectRatio)
        assertEquals("768p", updated.defaultValues["resolution"])
        assertNull(updated.shots.single().workflowId)
        assertNull(updated.shots.single().workflowPath)
        assertNull(updated.shots.single().providerId)
        assertTrue(updated.shots.single().values.isEmpty())
    }

    @Test
    fun createKeepsProviderIdentityForNonAutoDlWorkflows() {
        val store = store()
        val project = store.create(
            title = "Replicate board",
            workflowId = "owner/model",
            workflowPath = "replicate:owner/model",
            providerId = "replicate",
        )!!

        assertEquals("owner/model", project.workflowId)
        assertEquals("replicate:owner/model", project.workflowPath)
        assertEquals("replicate", project.providerId)
        assertEquals(project, store.project(project.id))
    }

    @Test
    fun removingAnAssetCleansEveryShotReference() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val shot = store.addShot(project.id, "shot")!!
        val first = com.comfyremote.app.data.reference.ProjectAsset(
            id = "asset-a",
            role = com.comfyremote.app.data.reference.ReferenceRole.START_FRAME,
            path = "/tmp/a.png",
        )
        val second = com.comfyremote.app.data.reference.ProjectAsset(
            id = "asset-b",
            role = com.comfyremote.app.data.reference.ReferenceRole.CHARACTER_REFERENCE,
            path = "/tmp/b.png",
        )
        store.addAsset(project.id, first)
        store.addAsset(project.id, second)
        store.updateShot(project.id, shot.id) {
            it.copy(
                referenceAssetIds = listOf("asset-a", "asset-b"),
                referencePath = "/tmp/a.png",
                referenceRole = com.comfyremote.app.data.reference.ReferenceRole.START_FRAME,
                referenceManual = true,
            )
        }

        assertTrue(store.removeAsset(project.id, "asset-a"))

        val afterFirst = store.project(project.id)!!.shots.single()
        assertEquals(listOf("asset-b"), afterFirst.referenceAssetIds)
        assertEquals("/tmp/b.png", afterFirst.referencePath)
        assertEquals(com.comfyremote.app.data.reference.ReferenceRole.CHARACTER_REFERENCE, afterFirst.referenceRole)

        assertTrue(store.removeAsset(project.id, "asset-b"))

        val afterLast = store.project(project.id)!!.shots.single()
        assertTrue(afterLast.referenceAssetIds.isEmpty())
        assertNull(afterLast.referencePath)
        assertTrue(afterLast.referenceManual)
    }

    @Test
    fun reorderingShotsRebuildsTheAutomaticFrameChain() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        val third = store.addShot(project.id, "third")!!
        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        store.markDone(project.id, second.id, "/tmp/second.mp4", "/tmp/second.png")
        store.setReference(project.id, second.id, "/tmp/first.png", manual = false)
        store.setReference(project.id, third.id, "/tmp/second.png", manual = false)

        assertTrue(store.moveShot(project.id, third.id, -1))

        val shots = store.project(project.id)!!.shots
        assertEquals(listOf(first.id, third.id, second.id), shots.map { it.id })
        assertEquals("/tmp/first.png", shots[1].referencePath)
        assertEquals("/tmp/first.png", shots[2].referencePath)
    }

    @Test
    fun insertingAShotRebuildsTheAutomaticFrameChain() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        store.setReference(project.id, second.id, "/tmp/first.png", manual = false)

        val inserted = store.insertShotAfter(project.id, first.id, "inserted")!!

        val shots = store.project(project.id)!!.shots
        assertEquals(listOf(first.id, inserted.id, second.id), shots.map { it.id })
        assertEquals("/tmp/first.png", shots[1].referencePath)
        assertEquals("/tmp/first.png", shots[2].referencePath)
    }

    @Test
    fun manualReferencesSurviveReordering() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val manual = store.addShot(project.id, "manual")!!
        val auto = store.addShot(project.id, "auto")!!
        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        store.setReference(project.id, manual.id, "/tmp/manual.png", manual = true)
        store.setReference(project.id, auto.id, "/tmp/first.png", manual = false)

        assertTrue(store.moveShot(project.id, auto.id, -1))

        val shots = store.project(project.id)!!.shots
        assertEquals(listOf(first.id, auto.id, manual.id), shots.map { it.id })
        assertEquals("/tmp/first.png", shots[1].referencePath)
        assertEquals("/tmp/manual.png", shots[2].referencePath)
    }

    @Test
    fun removingATailFrameAssetDoesNotBreakTheAutomaticRelay() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val first = store.addShot(project.id, "first")!!
        val second = store.addShot(project.id, "second")!!
        store.markDone(project.id, first.id, "/tmp/first.mp4", "/tmp/first.png")
        val tailAsset = com.comfyremote.app.data.reference.ProjectAsset(
            id = "tail:${first.id}",
            role = com.comfyremote.app.data.reference.ReferenceRole.END_FRAME,
            path = "/tmp/first.png",
            label = "first tail",
        )
        store.addAsset(project.id, tailAsset)

        assertTrue(store.removeAsset(project.id, tailAsset.id))

        val next = store.project(project.id)!!.shots[1]
        assertEquals("/tmp/first.png", next.referencePath)
        assertFalse(next.referenceManual)
        assertTrue(next.referenceAssetIds.isEmpty())
    }

    @Test
    fun aFailedWriteKeepsTheProjectAndDoesNotDeleteAssetMedia() {
        val index = File(temp.root, "storyboards.json")
        val store = StoryboardStore(index)
        val project = store.create("Board", "wf")!!
        val assetFile = File(temp.root, "asset.png").apply { writeText("asset") }
        val asset = com.comfyremote.app.data.reference.ProjectAsset(
            id = "asset-file",
            role = com.comfyremote.app.data.reference.ReferenceRole.GENERIC_REFERENCE,
            path = assetFile.absolutePath,
        )
        store.addAsset(project.id, asset)
        index.delete()
        index.mkdirs()

        val removed = store.removeAsset(project.id, asset.id)

        assertFalse(removed)
        assertTrue(assetFile.exists())
        assertTrue(store.project(project.id)!!.assets.any { it.id == asset.id })
        assertTrue(store.persistenceError.value != null)
    }

    @Test
    fun removingAnAssetDeletesItsAppOwnedFileImmediately() {
        val store = store()
        val mediaDir = File(temp.root, "storyboards").apply { mkdirs() }
        val file = File(mediaDir, "fresh.png").apply { writeText("asset") }
        val project = store.create("Board", "wf")!!
        val asset = com.comfyremote.app.data.reference.ProjectAsset(
            id = "asset-file",
            role = com.comfyremote.app.data.reference.ReferenceRole.GENERIC_REFERENCE,
            path = file.absolutePath,
        )
        store.addAsset(project.id, asset)

        assertTrue(store.removeAsset(project.id, "asset-file"))

        assertFalse(file.exists())
    }

    @Test
    fun projectAssetsPersistAndCountAsReferencedMedia() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val asset = com.comfyremote.app.data.reference.ProjectAsset(
            id = "asset-1",
            role = com.comfyremote.app.data.reference.ReferenceRole.CHARACTER_REFERENCE,
            path = "/tmp/character.png",
            label = "hero",
        )

        assertTrue(store.addAsset(project.id, asset))
        assertEquals("hero", store.project(project.id)?.assets?.single()?.label)
        assertTrue("/tmp/character.png" in store.referencedMediaPaths())

        assertTrue(store.removeAsset(project.id, asset.id))
        assertTrue(store.project(project.id)?.assets?.isEmpty() == true)
        assertFalse("/tmp/character.png" in store.referencedMediaPaths())
    }

    @Test
    fun referencedMediaPathsCoverResultsFramesAndReferences() {
        val store = store()
        val project = store.create("Board", "wf")!!
        val shot = store.addShot(project.id, "shot")!!
        store.markDone(project.id, shot.id, "/tmp/result.mp4", "/tmp/last.png")
        store.setReference(project.id, shot.id, "/tmp/reference.png")

        assertEquals(
            setOf("/tmp/result.mp4", "/tmp/last.png", "/tmp/reference.png"),
            store.referencedMediaPaths(),
        )
    }
}
