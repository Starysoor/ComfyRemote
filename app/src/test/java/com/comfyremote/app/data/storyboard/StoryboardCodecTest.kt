package com.comfyremote.app.data.storyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardCodecTest {
    @Test
    fun projectAndShotRoundTripPreservesChainState() {
        val shot1 = StoryboardShot(
            id = "shot-1",
            prompt = "wide shot",
            lastFramePath = "/files/last.png",
            tailFrameStored = true,
            status = "done",
            taskId = "task-1",
            resultPath = "/files/result.mp4",
        )
        val shot2 = StoryboardShot(
            id = "shot-2",
            prompt = "close-up",
            referencePath = "/files/last.png",
            referenceManual = true,
            referenceRole = com.comfyremote.app.data.reference.ReferenceRole.START_FRAME,
            referenceAssetIds = listOf("asset-hero", "asset-scene"),
            workflowId = "minimax_h3_lightx2v_v5",
            values = mapOf("prompt" to "close-up", "duration" to "5"),
        )
        val project = StoryboardProject(
            id = "project-1",
            title = "Sakura",
            workflowId = "minimax_h3_lightx2v_v5",
            stylePrompt = "cinematic",
            aspectRatio = "9:16",
            defaultValues = mapOf("resolution" to "768p竖"),
            chainFrames = false,
            shots = listOf(shot1, shot2),
            assets = listOf(
                com.comfyremote.app.data.reference.ProjectAsset(
                    id = "asset-1",
                    role = com.comfyremote.app.data.reference.ReferenceRole.CHARACTER_REFERENCE,
                    path = "/files/character.png",
                    label = "hero",
                    order = 1,
                )
            ),
            createdAt = 100L,
            updatedAt = 200L,
        )

        val restored = StoryboardCodec.decode(StoryboardCodec.encode(listOf(project))).single()

        assertEquals(project.id, restored.id)
        assertEquals(project.workflowId, restored.workflowId)
        assertEquals(project.stylePrompt, restored.stylePrompt)
        assertEquals(2, restored.shots.size)
        assertEquals("done", restored.shots[0].status)
        assertEquals("/files/result.mp4", restored.shots[0].resultPath)
        assertTrue(restored.shots[0].tailFrameStored)
        assertEquals("/files/last.png", restored.shots[1].referencePath)
        assertTrue(restored.shots[1].referenceManual)
        assertEquals(
            com.comfyremote.app.data.reference.ReferenceRole.START_FRAME,
            restored.shots[1].referenceRole,
        )
        assertEquals("9:16", restored.aspectRatio)
        assertEquals("768p竖", restored.defaultValues["resolution"])
        assertFalse(restored.chainFrames)
        assertTrue(restored.shots[0].tailFrameStored)
        assertEquals(listOf("asset-hero", "asset-scene"), restored.shots[1].referenceAssetIds)
        assertEquals("minimax_h3_lightx2v_v5", restored.shots[1].workflowId)
        assertEquals("5", restored.shots[1].values["duration"])
        assertEquals(1, restored.assets.size)
        assertEquals("hero", restored.assets.single().label)
        assertNull(restored.shots[1].taskId)
    }

    @Test
    fun legacyAutoDlWorkflowIdsMigrateToFullPaths() {
        val raw = """[
          {"id":"p1","title":"Legacy","workflowId":"minimax_h3_lightx2v_v5",
           "shots":[{"id":"s1","prompt":"shot","workflowId":"minimax_h3_lightx2v_v5"}]}
        ]"""

        val project = StoryboardCodec.decode(raw).single()

        assertEquals("autodl:minimax_h3_lightx2v_v5", project.workflowPath)
        assertEquals("autodl", project.providerId)
        assertEquals("autodl:minimax_h3_lightx2v_v5", project.shots.single().workflowPath)
        assertEquals("autodl", project.shots.single().providerId)
        assertTrue(project.chainFrames)
        assertFalse(project.shots.single().tailFrameStored)
    }

    @Test
    fun providerIdentityRoundTripsForProjectAndShotOverrides() {
        val project = StoryboardProject(
            id = "project-cloud",
            title = "Cloud board",
            workflowId = "owner/project-model",
            workflowPath = "replicate:owner/project-model",
            providerId = "replicate",
            shots = listOf(
                StoryboardShot(
                    id = "shot-a",
                    prompt = "wide",
                    workflowPath = "siliconflow:Wan-AI/Wan2.2",
                    providerId = "siliconflow",
                    workflowId = "Wan-AI/Wan2.2",
                ),
                StoryboardShot(id = "shot-b", prompt = "inherit"),
            ),
        )

        val restored = StoryboardCodec.decode(StoryboardCodec.encode(listOf(project))).single()

        assertEquals("replicate:owner/project-model", restored.workflowPath)
        assertEquals("replicate", restored.providerId)
        assertEquals("siliconflow:Wan-AI/Wan2.2", restored.shots[0].workflowPath)
        assertEquals("siliconflow", restored.shots[0].providerId)
        assertEquals("Wan-AI/Wan2.2", restored.shots[0].workflowId)
        assertNull(restored.shots[1].workflowPath)
    }

    @Test
    fun malformedJsonDecodesToEmptyListInsteadOfCrashing() {
        assertTrue(StoryboardCodec.decode("not json").isEmpty())
    }
}
