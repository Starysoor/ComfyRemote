package com.comfyremote.app.data.repository

/** One-shot handoff used when History/Task Center reopens a workflow for editing. */
data class DraftTransfer(
    val workflowPath: String,
    val workflowName: String,
    val values: Map<String, String>,
    val randomSeed: Boolean,
)

class DraftTransferRepository {
    private val lock = Any()
    private var pending: DraftTransfer? = null

    fun put(transfer: DraftTransfer) = synchronized(lock) { pending = transfer }

    fun consume(workflowPath: String): DraftTransfer? = synchronized(lock) {
        pending?.takeIf { it.workflowPath == workflowPath }?.also { pending = null }
    }

    fun clear() = synchronized(lock) { pending = null }
}
