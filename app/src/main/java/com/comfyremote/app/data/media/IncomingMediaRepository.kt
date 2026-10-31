package com.comfyremote.app.data.media

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds media received from outside the app (currently ACTION_SEND images),
 * until a workflow with reference-image slots consumes it.
 */
class IncomingMediaRepository {

    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    val pending: StateFlow<List<Uri>> = _pending.asStateFlow()

    /** Returns true when there was something pending to consume. */
    fun add(uri: Uri): Boolean {
        _pending.value = _pending.value + uri
        return true
    }

    /** Removes and returns all pending media (caller uploads them). */
    fun drain(): List<Uri> {
        val list = _pending.value
        _pending.value = emptyList()
        return list
    }

    fun hasPending(): Boolean = _pending.value.isNotEmpty()
}
