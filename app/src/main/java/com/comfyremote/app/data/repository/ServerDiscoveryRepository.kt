package com.comfyremote.app.data.repository

import com.comfyremote.app.data.discovery.DiscoveryEvent
import com.comfyremote.app.data.discovery.LanServerDiscovery
import kotlinx.coroutines.flow.Flow

/** Stable discovery boundary; LAN /24 scanning is only the current implementation. */
class ServerDiscoveryRepository(
    private val lan: LanServerDiscovery,
) {
    fun discover(): Flow<DiscoveryEvent> = lan.discover()
}
