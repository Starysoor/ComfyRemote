package com.comfyremote.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot navigation requests emitted from Android entry points such as notifications. */
class NavigationRequestRepository {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    fun request(route: String) {
        _route.value = route
    }

    fun consume(route: String) {
        if (_route.value == route) _route.value = null
    }
}
