package com.comfyremote.app.data.storyboard

import org.junit.Assert.assertEquals
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
        assertEquals("/files/last.png", restored.shots[1].referencePath)
        assertTrue(restored.shots[1].referenceManual)
        assertEquals(
            com.comfyremote.app.data.reference.ReferenceRole.START_FRAME,
            restored.shots[1].referenceRole,
        )
        assertEquals("9:16", restored.aspectRatio)
        assertEquals("768p竖", restored.defaultValues["resolution"])
        assertEquals("minimax_h3_lightx2v_v5", restored.shots[1].workflowId)
        assertEquals("5", restored.shots[1].values["duration"])
        assertEquals(1, restored.assets.size)
        assertEquals("hero", restored.assets.single().label)
        assertNull(restored.shots[1].taskId)
    }

    @Test
    fun malformedJsonDecodesToEmptyListInsteadOfCrashing() {
        assertTrue(StoryboardCodec.decode("not json").isEmpty())
    }
}
