package com.comfyremote.app.data.generation

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSubmissionPolicyTest {

    private fun request(
        provider: String = "autodl",
        cost: Int? = 10,
        fields: List<PromptField> = emptyList(),
        values: Map<String, String> = emptyMap(),
    ) = GenerationRequest(
        provider = provider,
        serverUrl = "https://autodl.art",
        workflowPath = "autodl:wf",
        workflowName = "Cloud workflow",
        fieldValues = values,
        randomSeed = true,
        nodeTitles = emptyMap(),
        fields = fields,
        convertedPrompt = JSONObject(),
        estimatedCloudCost = cost,
    )

    @Test
    fun confirmationIsOnlyRequiredForCloudRequestsWhenEnabled() {
        assertTrue(CloudSubmissionPolicy.needsConfirmation(listOf(request()), true))
        assertFalse(CloudSubmissionPolicy.needsConfirmation(listOf(request()), false))
        assertFalse(CloudSubmissionPolicy.needsConfirmation(listOf(request(provider = "comfyui")), true))
    }

    @Test
    fun batchConfirmationCarriesCountPerRunAndTotal() {
        val confirmation = CloudSubmissionPolicy.confirmation(List(3) { request(cost = 10) }, "per run")

        assertEquals(3, confirmation.count)
        assertEquals(10, confirmation.perRunCost)
        assertEquals(30, confirmation.totalCost)
        assertEquals("per run", confirmation.priceLabel)
        assertEquals("Cloud workflow", confirmation.workflowName)
    }

    @Test
    fun mixedCostsKeepTheTotalButDropASinglePerRunEstimate() {
        val confirmation = CloudSubmissionPolicy.confirmation(
            listOf(request(cost = 10), request(cost = 15)),
            null,
        )

        assertNull(confirmation.perRunCost)
        assertEquals(25, confirmation.totalCost)
    }

    @Test
    fun unknownCostDoesNotPretendToKnowTheTotal() {
        val confirmation = CloudSubmissionPolicy.confirmation(
            listOf(request(cost = 10), request(cost = null)),
            null,
        )

        assertNull(confirmation.perRunCost)
        assertNull(confirmation.totalCost)
    }

    @Test
    fun spendIsRecordedOnlyAfterTheRemoteTaskIdExists() {
        val task = GenerationTask(taskId = "local", request = request(), promptId = null)
        assertFalse(task.shouldRecordCloudSpend)
        assertTrue(task.copy(promptId = "remote-id").shouldRecordCloudSpend)
        assertFalse(task.copy(promptId = "remote-id", spendRecorded = true).shouldRecordCloudSpend)
        assertFalse(
            task.copy(promptId = "remote-id", request = request(provider = "comfyui"))
                .shouldRecordCloudSpend
        )
    }

    @Test
    fun durationAndResolutionComeFromTheCloudFields() {
        val duration = PromptField(
            key = "duration",
            nodeId = "cloud",
            input = "duration",
            label = "Duration",
            kind = FieldKind.PARAM,
            value = "5",
        )
        val resolution = PromptField(
            key = "resolution",
            nodeId = "cloud",
            input = "resolution",
            label = "Resolution",
            kind = FieldKind.PARAM,
            value = "720p",
        )
        val confirmation = CloudSubmissionPolicy.confirmation(
            listOf(request(fields = listOf(duration, resolution))),
            null,
        )

        assertEquals("5", confirmation.duration)
        assertEquals("720p", confirmation.resolution)
    }
}
