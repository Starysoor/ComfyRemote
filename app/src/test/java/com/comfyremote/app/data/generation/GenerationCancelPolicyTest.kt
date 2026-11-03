package com.comfyremote.app.data.generation

import org.junit.Assert.assertEquals
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
}
