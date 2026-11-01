package com.comfyremote.app.data.generation

import com.comfyremote.app.HistoryItem
import com.comfyremote.app.OutputImage
import com.comfyremote.app.WsEvent
import com.comfyremote.app.data.repository.HistoryRepository
import com.comfyremote.app.data.repository.ServerSessionRepository
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

class SessionGenerationBackend(
    private val session: ServerSessionRepository,
) : GenerationBackend {
    override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String = session.require().queuePrompt(prompt, clientId)
    override fun wsEvents(clientId: String): Flow<WsEvent> = session.require().wsEvents(clientId)
    override suspend fun historyEntry(promptId: String): JSONObject? = session.require().historyEntry(promptId)
    override suspend fun downloadImage(image: OutputImage): ByteArray = session.require().downloadImage(image)
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
