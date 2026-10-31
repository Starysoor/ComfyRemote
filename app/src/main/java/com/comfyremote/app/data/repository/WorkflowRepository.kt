package com.comfyremote.app.data.repository

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.ConvertedWorkflow
import com.comfyremote.app.WorkflowConverter
import com.comfyremote.app.WorkflowEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads workflows from the server and converts them via the object_info cache. */
class WorkflowRepository(
    private val session: ServerSessionRepository,
    private val objectInfoCache: ObjectInfoCache,
) {

    suspend fun list(): List<WorkflowEntry> = session.require().listWorkflows()

    suspend fun convert(entry: WorkflowEntry): ConvertedWorkflow {
        val a = session.require()
        val info = objectInfoCache.get(a, session.version)
        val json = withContext(Dispatchers.IO) { a.fetchWorkflow(entry.path) }
        return WorkflowConverter.convert(json, info)
    }
}
