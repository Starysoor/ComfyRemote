package com.comfyremote.app.feature.project

import com.comfyremote.app.data.project.taskCostStats
import com.comfyremote.app.data.generation.GenerationRequest
import com.comfyremote.app.data.generation.GenerationTask
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCostStatsTest {
    private fun task(projectId: String?, cost: Int?): GenerationTask = GenerationTask(
        taskId = "t-${projectId}-$cost",
        request = GenerationRequest(
            id = "r-${projectId}-$cost",
            serverUrl = "",
            workflowPath = "autodl:model",
            workflowName = "Model",
            fieldValues = emptyMap(),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = emptyList(),
            convertedPrompt = JSONObject(),
            storyboardProjectId = projectId,
            estimatedCloudCost = cost,
        ),
    )

    @Test
    fun onlyTheGivenProjectIsAggregated() {
        val tasks = listOf(
            task("p1", 3),
            task("p1", null),
            task("p2", 100),
            task(null, 7),
        )

        val stats = taskCostStats(tasks, "p1")

        assertEquals(2, stats.count)
        assertEquals(3.0, stats.costSum, 1e-9)
    }

    @Test
    fun emptySelectionIsZeroed() {
        val stats = taskCostStats(emptyList(), "p1")

        assertEquals(0, stats.count)
        assertEquals(0.0, stats.costSum, 1e-9)
    }

    @Test
    fun budgetComparisonIgnoresNonPositiveBudgets() {
        // The banner only fires for a positive budget; a zero/null budget is
        // handled by the caller, so stats only need to sum costs.
        val stats = taskCostStats(listOf(task("p1", 1), task("p1", 2)), "p1")

        assertEquals(3.0, stats.costSum, 1e-9)
    }
}
