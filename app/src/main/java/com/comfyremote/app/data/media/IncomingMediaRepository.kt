package com.comfyremote.app.data.media

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds media received from outside the app until a workflow can consume it. */
class IncomingMediaRepository {
    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    val pending: StateFlow<List<Uri>> = _pending.asStateFlow()

    fun add(uri: Uri): Boolean {
        _pending.value = (_pending.value + uri).distinct()
        return true
    }

    fun addAll(uris: Collection<Uri>): Boolean {
        if (uris.isEmpty()) return false
        _pending.value = (_pending.value + uris).distinct()
        return true
    }

    fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let(::add) ?: false
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
                addAll(uris)
            }
            else -> false
        }
    }

    fun drain(): List<Uri> {
        val list = _pending.value
        _pending.value = emptyList()
        return list
    }

    fun hasPending(): Boolean = _pending.value.isNotEmpty()
}
