package com.comfyremote.app.data.project

import com.comfyremote.app.data.reference.AssetKind
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectMigrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val legacyJson = """
        [
          {"id":"p1","title":"旧项目","workflowId":"minimax_h3_lightx2v_v5","workflowPath":"","providerId":"",
           "stylePrompt":"cinematic 风格","aspectRatio":"9:16","defaultValues":{"resolution":"768p竖"},
           "chainFrames":true,"createdAt":100,"updatedAt":200,
           "shots":[
             {"id":"s1","prompt":"第一镜","referencePath":"/m/ref.png","referenceManual":true,
              "referenceRole":"CHARACTER_REFERENCE","referenceAssetIds":["a1"],
              "workflowId":"minimax_h3_lightx2v_v5","values":{"duration":"5"},
              "lastFramePath":"/m/last.png","tailFrameStored":true,"status":"done",
              "taskId":"t1","resultPath":"/m/r1.mp4","error":null},
             {"id":"s2","prompt":"第二镜","status":"draft"},
             {"id":"s3","prompt":"第三镜","status":"running","taskId":"t3"}
           ],
           "assets":[
             {"id":"a1","role":"CHARACTER_REFERENCE","path":"/m/hero.png","label":"主角","order":0,"createdAt":50},
             {"id":"a2","role":"GENERIC_REFERENCE","path":"/m/clip.mp4","label":"片段","order":1,"createdAt":51}
           ]}
        ]
    """.trimIndent()

    private fun filesDir(): File = temp.newFolder("files")

    private fun writeLegacy(dir: File, content: String = legacyJson): File {
        val legacy = File(dir, "storyboards.json")
        legacy.writeText(content)
        return legacy
    }

    @Test
    fun legacyProjectsMigrateFieldForField() {
        val dir = filesDir()
        val legacy = writeLegacy(dir)

        val result = ProjectMigration.migrateIfNeeded(dir, now = 999L)

        assertTrue(result.migrated)
        assertEquals(1, result.projectCount)
        assertNull(result.error)
        // The legacy anchor stays byte-identical and frozen.
        assertEquals(legacyJson, legacy.readText())

        val project = ProjectStore(File(dir, "projects.json")).project("p1")!!
        assertEquals("旧项目", project.title)
        assertEquals("autodl:minimax_h3_lightx2v_v5", project.workflowPath)
        assertEquals("autodl", project.providerId)
        assertEquals("cinematic 风格", project.stylePrompt)
        assertEquals("9:16", project.aspectRatio)
        assertEquals("768p竖", project.defaultValues["resolution"])
        assertTrue(project.chainFrames)
        assertEquals(100L, project.createdAt)
        assertEquals(200L, project.updatedAt)
        // Legacy projects have no synopsis concept yet; the field stays empty.
        assertEquals("", project.synopsis)
        assertNull(project.targetDurationSeconds)
        assertNull(project.budget)

        val done = project.shots[0]
        assertEquals("s1", done.id)
        assertEquals("第一镜", done.prompt)
        assertEquals("/m/ref.png", done.referencePath)
        assertTrue(done.referenceManual)
        assertEquals(listOf("a1"), done.referenceAssetIds)
        assertEquals("autodl:minimax_h3_lightx2v_v5", done.workflowPath)
        assertEquals("autodl", done.providerId)
        assertEquals("/m/last.png", done.lastFramePath)
        assertTrue(done.tailFrameStored)
        assertEquals("done", done.status)
        assertEquals("t1", done.taskId)
        assertEquals("/m/r1.mp4", done.resultPath)
        val take = done.takes.single()
        assertEquals("s1-take-1", take.id)
        assertEquals("done", take.status)
        assertEquals("t1", take.taskId)
        assertEquals("/m/r1.mp4", take.resultPath)
        assertEquals("autodl:minimax_h3_lightx2v_v5", take.workflowPath)
        assertEquals("5", take.values["duration"])
        assertEquals(999L, take.createdAt)
        assertEquals(take.id, done.selectedTakeId)

        // A never-generated shot records no take at all.
        val draft = project.shots[1]
        assertEquals("s2", draft.id)
        assertTrue(draft.takes.isEmpty())
        assertNull(draft.selectedTakeId)

        // A running shot keeps its live state; orphan recovery closes it later.
        val running = project.shots[2]
        assertEquals("running", running.status)
        assertEquals("running", running.takes.single().status)
        assertEquals("t3", running.taskId)

        val assets = project.assets
        assertEquals(2, assets.size)
        assertEquals("主角", assets[0].label)
        assertEquals(AssetKind.IMAGE, assets[0].kind)
        assertEquals(AssetKind.VIDEO, assets[1].kind)
    }

    @Test
    fun migrationIsIdempotentOnceTheProjectsIndexExists() {
        val dir = filesDir()
        writeLegacy(dir)
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 1L).migrated)
        val before = File(dir, "projects.json").readText()

        val second = ProjectMigration.migrateIfNeeded(dir, now = 2L)

        assertFalse(second.migrated)
        assertEquals(1, second.projectCount)
        assertEquals(before, File(dir, "projects.json").readText())
    }

    @Test
    fun aCrashBeforeCommitLeavesLegacyIntactAndRetriesCleanly() {
        val dir = filesDir()
        val legacy = writeLegacy(dir)

        val failed = ProjectMigration.migrateIfNeeded(dir, now = 1L, writer = { _, _ ->
            throw IOException("ENOSPC: simulated disk full")
        })

        assertTrue(failed.aborted)
        assertEquals("ENOSPC: simulated disk full", failed.error)
        assertFalse(File(dir, "projects.json").exists())
        assertEquals(legacyJson, legacy.readText())

        val retried = ProjectMigration.migrateIfNeeded(dir, now = 2L)
        assertTrue(retried.migrated)
        assertEquals(1, retried.projectCount)
    }

    @Test
    fun corruptLegacyIndexAbortsAndNeverTouchesTheFile() {
        val dir = filesDir()
        val broken = """[{"title":"row without id"}]"""
        val legacy = writeLegacy(dir, broken)

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertTrue(result.aborted)
        assertFalse(File(dir, "projects.json").exists())
        assertEquals(broken, legacy.readText())
    }

    @Test
    fun unparseableLegacyIndexAbortsInsteadOfMigratingEmptyData() {
        val dir = filesDir()
        val legacy = writeLegacy(dir, "definitely not json")

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertTrue(result.aborted)
        assertFalse(File(dir, "projects.json").exists())
        assertEquals("definitely not json", legacy.readText())
    }

    @Test
    fun backupRotationKeepsOnlyTheTwoNewestBackups() {
        val dir = filesDir()
        writeLegacy(dir)
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 1L).migrated)
        File(dir, "projects.json").delete()
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 2L).migrated)
        File(dir, "projects.json").delete()
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 3L).migrated)

        val backups = dir.listFiles { file -> file.name.startsWith("storyboards.backup-") }!!.sortedBy { it.name }
        assertEquals(listOf("storyboards.backup-2.json", "storyboards.backup-3.json"), backups.map { it.name })
    }

    @Test
    fun aFailedCommitThroughTheRealWriterCleansTheTmpFile() {
        val dir = filesDir()
        writeLegacy(dir)
        // A non-empty directory at the target path makes the atomic move fail
        // after the tmp file has been written.
        File(dir, "projects.json").mkdirs()
        File(dir, "projects.json/occupied.txt").writeText("block")

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertTrue(result.aborted)
        val leftovers = dir.listFiles { file -> file.name.endsWith(".tmp") }.orEmpty()
        assertTrue("tmp residue: $leftovers", leftovers.isEmpty())
        assertEquals(legacyJson, File(dir, "storyboards.json").readText())
    }

    @Test
    fun corruptProjectsIndexFallsBackToRemigratingFromTheLegacyAnchor() {
        val dir = filesDir()
        writeLegacy(dir)
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 1L).migrated)
        File(dir, "projects.json").writeText("{corrupted by a bad disk}")

        val result = ProjectMigration.migrateIfNeeded(dir, now = 2L)

        assertTrue(result.migrated)
        assertEquals(1, result.projectCount)
        assertEquals("p1", ProjectCodec.decode(File(dir, "projects.json").readText()).single().id)
    }

    @Test
    fun freshInstallsWithNoIndexesAreANoOp() {
        val dir = filesDir()

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertFalse(result.migrated)
        assertEquals(0, result.projectCount)
        assertNull(result.error)
    }

    @Test
    fun anEmptyLegacyIndexMigratesToAnEmptyProjectsIndex() {
        val dir = filesDir()
        writeLegacy(dir, "[]")

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertTrue(result.migrated)
        assertEquals(0, result.projectCount)
        assertTrue(ProjectCodec.decode(File(dir, "projects.json").readText()).isEmpty())
    }

    @Test
    fun migratedShotsRebuildTheSameChainTheLegacyStoreWouldServe() {
        val dir = filesDir()
        writeLegacy(dir)
        assertTrue(ProjectMigration.migrateIfNeeded(dir, now = 1L).migrated)

        val store = ProjectStore(File(dir, "projects.json"))
        val project = store.project("p1")!!
        // Legacy s1 has lastFramePath and s2 has no manual reference, so the
        // relay rebase must hand s1's frame to s2 exactly like before.
        assertEquals("/m/last.png", project.shots[1].referencePath)
        assertFalse(project.shots[1].referenceManual)
    }

    @Test
    fun legacyRowsAreCountedBeforeMigration() {
        val raw = JSONArray().apply {
            put(
                org.json.JSONObject()
                    .put("id", "only")
                    .put("title", "single")
                    .put("workflowId", "wf"),
            )
        }.toString()
        val dir = filesDir()
        writeLegacy(dir, raw)

        val result = ProjectMigration.migrateIfNeeded(dir, now = 1L)

        assertTrue(result.migrated)
        assertEquals(1, result.projectCount)
    }
}
