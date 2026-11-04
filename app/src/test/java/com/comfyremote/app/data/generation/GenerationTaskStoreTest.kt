package com.comfyremote.app.data.generation

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class GenerationTaskStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun saveRoundTripsTasks() {
        val store = GenerationTaskStore(File(temp.root, "tasks.json"))
        val request = GenerationRequest(
            id = "task",
            serverUrl = "http://127.0.0.1:8188",
            workflowPath = "w.json",
            workflowName = "Workflow",
            fieldValues = emptyMap(),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = emptyList(),
            convertedPrompt = org.json.JSONObject(),
            createdAt = 1L,
        )
        val task = GenerationTask(taskId = "task", request = request)

        store.save(listOf(task))

        assertTrue(store.load().single().taskId == "task")
    }

    @Test
    fun anUnwritableParentThrowsInsteadOfBeingSilent() {
        val blocked = File(temp.root, "blocked").apply { writeText("not a directory") }
        val store = GenerationTaskStore(File(blocked, "tasks.json"))

        val error = runCatching { store.save(emptyList()) }.exceptionOrNull()

        assertTrue(error is IOException)
    }
}
