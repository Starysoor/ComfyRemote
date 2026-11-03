package com.comfyremote.app.data.cloud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReplicateEndpointRecoveryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun endpointSnapshotSurvivesProcessRestartAndSettingsChange() {
        val folder = File(temp.root, "cloud_tasks")
        val taskId = "prediction-123"
        CloudProviderTaskStore(folder).savePending(
            ReplicateRepository.PROVIDER,
            taskId,
            JSONObject().put("model", "black-forest-labs/flux-schnell")
                .put("kind", "image")
                .put("endpoint", "https://old.replicate.test/v1/"),
        )

        // A different store instance represents recovery after process death.
        val recovered = CloudProviderTaskStore(folder)
            .pending(ReplicateRepository.PROVIDER, taskId)!!
        assertEquals(
            "https://old.replicate.test/v1/predictions/$taskId",
            replicatePredictionUrl(recovered.optString("endpoint"), "https://new.replicate.test/v1", taskId),
        )
        assertEquals("image", recovered.getString("kind"))
    }

    @Test
    fun legacyTaskWithoutSnapshotFallsBackToCurrentEndpoint() {
        assertEquals(
            "https://api.replicate.com/v1/predictions/legacy",
            replicatePredictionUrl(null, "https://api.replicate.com/v1/", "legacy"),
        )
        assertEquals(
            "https://api.replicate.com/v1/predictions/legacy",
            replicatePredictionUrl("", "https://api.replicate.com/v1", "legacy"),
        )
    }
}
