package com.comfyremote.app.data.generation

import com.comfyremote.app.ComfyApi
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.OutputImage
import com.comfyremote.app.QueueSnapshot
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

    /** Streams an output to [target]; cloud providers override this for large videos. */
    suspend fun downloadTo(image: OutputImage, target: File) {
        target.writeBytes(downloadImage(image))
    }

    /** Optional for fake/legacy backends; production uses it for exact queue state and recovery. */
    suspend fun queueSnapshot(): QueueSnapshot? = null
}

/** A task-scoped backend permanently bound to the server selected at submit time. */
class ComfyGenerationBackend(serverUrl: String) : GenerationBackend {
    private val api = ComfyApi(serverUrl)

    override suspend fun queuePrompt(prompt: JSONObject, clientId: String): String = api.queuePrompt(prompt, clientId)
    override fun wsEvents(clientId: String): Flow<WsEvent> = api.wsEvents(clientId)
    override suspend fun historyEntry(promptId: String): JSONObject? = api.historyEntry(promptId)
    override suspend fun downloadImage(image: OutputImage): ByteArray = api.downloadImage(image)
    override suspend fun queueSnapshot(): QueueSnapshot = api.queueSnapshot()
}

interface GenerationHistorySink {
    fun newImageFile(promptId: String, index: Int): File

    /** Media-aware variant; legacy/test sinks can keep overriding [newImageFile]. */
    fun newMediaFile(promptId: String, index: Int, extension: String): File =
        newImageFile(promptId, index)

    fun add(item: HistoryItem)
}

class RepositoryGenerationHistorySink(
    private val repository: HistoryRepository,
    private val serverUrl: String = "",
) : GenerationHistorySink {
    override fun newImageFile(promptId: String, index: Int): File = repository.newImageFile(promptId, index)
    override fun newMediaFile(promptId: String, index: Int, extension: String): File =
        repository.newMediaFile(promptId, index, extension)

    override fun add(item: HistoryItem) = repository.add(
        if (serverUrl.isBlank() || item.serverUrl.isNotBlank()) item else item.copy(serverUrl = serverUrl)
    )
}
