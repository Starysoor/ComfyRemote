package com.comfyremote.app.feature.history

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.data.repository.PromptPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<HistoryItem>> = container.historyRepository.items
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun delete(id: String) = container.historyRepository.delete(id)
    fun deleteMany(ids: Collection<String>) = container.historyRepository.deleteMany(ids)

    fun bookmark(item: HistoryItem) {
        if (item.prompts.isEmpty()) return
        container.promptPresetRepository.add(
            PromptPreset(
                id = "history_${item.id}_${System.currentTimeMillis()}",
                title = item.summary.take(32).ifBlank { item.wfName },
                prompts = item.prompts,
                workflowPath = item.wfPath.ifBlank { null },
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    fun prepareReuse(item: HistoryItem): DraftTransfer? {
        if (item.wfPath.isBlank()) return null
        val transfer = DraftTransfer(item.wfPath, item.wfName, item.values, item.randomSeed)
        container.draftTransferRepository.put(transfer)
        return transfer
    }

    fun save(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.galleryExporter.savePng(bitmap) }
                .onSuccess { _message.value = container.galleryExporter.savedMessage() }
                .onFailure { _message.value = container.galleryExporter.failMessage(it) }
        }
    }

    fun clearMessage() { _message.value = null }
}
