package com.comfyremote.app.data.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.json.JSONObject
import org.junit.Test

class GenerationCancelPolicyTest {

    @Test
    fun localQueuedAndRunningNeedServerConfirmation() {
        assertEquals(
            CancelAction.DELETE_QUEUE,
            cancelActionFor(GenerationPhase.QUEUED, cloud = false, localPending = false),
        )
        assertEquals(
            CancelAction.INTERRUPT_SERVER,
            cancelActionFor(GenerationPhase.RUNNING, cloud = false, localPending = false),
        )
    }

    @Test
    fun cloudCancelsOnlyStopLocalTracking() {
        assertEquals(
            CancelAction.CANCEL_JOB,
            cancelActionFor(GenerationPhase.QUEUED, cloud = true, localPending = false),
        )
        assertEquals(
            CancelAction.CANCEL_JOB,
            cancelActionFor(GenerationPhase.RUNNING, cloud = true, localPending = false),
        )
    }

    @Test
    fun localPendingDoesNotTouchTheServer() {
        assertEquals(
            CancelAction.LOCAL_PENDING,
            cancelActionFor(GenerationPhase.QUEUED, cloud = false, localPending = true),
        )
    }

    @Test
    fun preparingAndTerminalStatesAreHandledExplicitly() {
        assertEquals(
            CancelAction.CANCEL_JOB,
            cancelActionFor(GenerationPhase.PREPARING, cloud = false, localPending = false),
        )
        assertEquals(
            CancelAction.NOT_CANCELLABLE,
            cancelActionFor(GenerationPhase.SUCCEEDED, cloud = false, localPending = false),
        )
    }
    @Test
    fun localCancelRejectsAnyTaskAlreadyDispatchedToTheServer() {
        val request = GenerationRequest(
            serverUrl = "http://server:8188",
            workflowPath = "work.json",
            workflowName = "Test",
            fieldValues = emptyMap(),
            randomSeed = true,
            nodeTitles = emptyMap(),
            fields = emptyList(),
            convertedPrompt = JSONObject(),
        )
        val pending = GenerationTask(
            taskId = request.id,
            request = request,
            phase = GenerationPhase.PREPARING,
            localPending = true,
        )
        val cancelled = pending.cancelIfStillLocalPending(100L)
        assertNotNull(cancelled)
        assertEquals(GenerationPhase.CANCELLED, cancelled?.phase)
        assertFalse(cancelled?.localPending ?: true)
        assertNull(pending.copy(localPending = false, phase = GenerationPhase.QUEUEING)
            .cancelIfStillLocalPending(100L))
        assertNull(pending.copy(localPending = false, phase = GenerationPhase.RUNNING)
            .cancelIfStillLocalPending(100L))
    }

}
