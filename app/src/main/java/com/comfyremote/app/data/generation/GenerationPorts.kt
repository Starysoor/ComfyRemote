package com.comfyremote.app.data.generation

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.OutputImage
import com.comfyremote.app.WsEvent
import com.comfyremote.app.data.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.File

/** Small generation-only port around ComfyApi so the runner can be regression-tested without HTTP. */
interface GenerationBackend {
    suspend fun queuePrompt(prompt: JSONObject, clientId: String): String
    fun wsEvents(clientId: String): Flow<WsEvent>
    suspend fun historyEntry(promptId: String): JSONObject?
    suspend fun downloadImage(image: OutputImage): ByteArray
}

/**
 * A task-scoped backend permanently bound to the server selected at submit time.
 * Switching the app's current ServerSession cannot redirect an in-flight task.
 */
class ComfyGenerationBackend(serverUrl: String) : GenerationBackend {
    private val api = ComfyApi(serverUrl)

    override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String = api.queuePrompt(prompt, clientId)
    override fun wsEvents(clientId: String): Flow<WsEvent> = api.wsEvents(clientId)
    override suspend fun historyEntry(promptId: String): JSONObject? = api.historyEntry(promptId)
    override suspend fun downloadImage(image: OutputImage): ByteArray = api.downloadImage(image)
}

interface GenerationHistorySink {
    fun newImageFile(promptId: String, index: Int): File
    fun add(item: HistoryItem)
}

class RepositoryGenerationHistorySink(
    private val repository: HistoryRepository,
) : GenerationHistorySink {
    override fun newImageFile(promptId: String, index: Int): File = repository.newImageFile(promptId, index)
    override fun add(item: HistoryItem) = repository.add(item)
}
