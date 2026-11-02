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
        )
        val project = StoryboardProject(
            id = "project-1",
            title = "Sakura",
            workflowId = "minimax_h3_lightx2v_v5",
            stylePrompt = "cinematic",
            shots = listOf(shot1, shot2),
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
        assertNull(restored.shots[1].taskId)
    }

    @Test
    fun malformedJsonDecodesToEmptyListInsteadOfCrashing() {
        assertTrue(StoryboardCodec.decode("not json").isEmpty())
    }
}
