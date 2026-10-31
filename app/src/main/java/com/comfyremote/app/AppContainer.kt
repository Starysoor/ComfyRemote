package com.comfyremote.app

import android.app.Application
import android.content.SharedPreferences
import com.comfyremote.app.data.discovery.LanServerDiscovery
import com.comfyremote.app.data.generation.GenerationManager
import com.comfyremote.app.data.media.GalleryExporter
import com.comfyremote.app.data.media.IncomingMediaRepository
import com.comfyremote.app.data.repository.AiSettingsStore
import com.comfyremote.app.data.repository.AiSkillStore
import com.comfyremote.app.data.repository.HistoryRepository
import com.comfyremote.app.data.repository.ObjectInfoCache
import com.comfyremote.app.data.repository.PromptPresetRepository
import com.comfyremote.app.data.repository.ServerRepository
import com.comfyremote.app.data.repository.ServerSessionRepository
import com.comfyremote.app.data.repository.UpdateSettingsStore
import com.comfyremote.app.data.repository.WorkflowRepository

/**
 * Hand-rolled dependency container (no DI framework needed at this scale).
 * Owns every store/repository so ViewModels never touch SharedPreferences
 * or framework plumbing directly.
 */
class AppContainer(app: Application) {

    val prefs: SharedPreferences = app.getSharedPreferences("comfyremote", Application.MODE_PRIVATE)

    val aiSettingsStore = AiSettingsStore(prefs)
    val aiSkillStore = AiSkillStore(prefs)
    val serverRepository = ServerRepository(prefs)
    val updateSettingsStore = UpdateSettingsStore(prefs)
    val promptPresetRepository = PromptPresetRepository(prefs)
    val historyRepository = HistoryRepository(HistoryStore(app))
    val serverSession = ServerSessionRepository()
    val objectInfoCache = ObjectInfoCache(app.cacheDir)
    val workflowRepository = WorkflowRepository(serverSession, objectInfoCache)
    val lanDiscovery = LanServerDiscovery()
    val galleryExporter = GalleryExporter(app)
    val incomingMedia = IncomingMediaRepository()
    val generationManager = GenerationManager(this, app)
}
