package com.comfyremote.app.data.repository

import com.comfyremote.app.HistoryItem
import com.comfyremote.app.HistoryStore

/** Facade over the JSON history store; Room can replace it later if ever needed. */
class HistoryRepository(private val store: HistoryStore) {

    fun load(): List<HistoryItem> = store.load()

    fun add(item: HistoryItem) {
        store.add(item)
    }

    fun delete(id: String) {
        store.delete(id)
    }

    fun deleteMany(ids: Collection<String>) {
        store.deleteMany(ids)
    }

    fun newImageFile(promptId: String, index: Int) = store.newImageFile(promptId, index)
}
