package com.comfyremote.app

import android.app.Application
import android.content.SharedPreferences
import com.comfyremote.app.data.discovery.LanServerDiscovery
import com.comfyremote.app.data.generation.GenerationManager
import com.comfyremote.app.data.generation.GenerationTaskStore
import com.comfyremote.app.data.media.GalleryExporter
import com.comfyremote.app.data.media.IncomingMediaRepository
import com.comfyremote.app.data.repository.AiRepository
import com.comfyremote.app.data.repository.AiSettingsStore
import com.comfyremote.app.data.repository.AiSkillStore
import com.comfyremote.app.data.repository.DraftTransferRepository
import com.comfyremote.app.data.repository.HistoryRepository
import com.comfyremote.app.data.repository.NavigationRequestRepository
import com.comfyremote.app.data.repository.ObjectInfoCache
import com.comfyremote.app.data.repository.PromptPresetRepository
import com.comfyremote.app.data.repository.ServerDiscoveryRepository
import com.comfyremote.app.data.repository.ServerRepository
import com.comfyremote.app.data.repository.ServerSessionRepository
import com.comfyremote.app.data.repository.UpdateRepository
import com.comfyremote.app.data.repository.UpdateSettingsStore
import com.comfyremote.app.data.repository.WorkflowRepository
import com.comfyremote.app.data.repository.WorkflowUsageRepository

/** Lightweight application-scoped dependency container. */
class AppContainer(val app: Application) {
    private val prefs: SharedPreferences = app.getSharedPreferences("comfyremote", Application.MODE_PRIVATE)

    val aiSettingsStore = AiSettingsStore(prefs)
    val aiSkillStore = AiSkillStore(prefs)
    val aiRepository = AiRepository(aiSettingsStore)

    val serverRepository = ServerRepository(prefs)
    val serverSession = ServerSessionRepository()
    val serverDiscoveryRepository = ServerDiscoveryRepository(LanServerDiscovery())

    val updateSettingsStore = UpdateSettingsStore(prefs)
    val updateRepository = UpdateRepository(app)

    val promptPresetRepository = PromptPresetRepository(prefs)
    val draftTransferRepository = DraftTransferRepository()
    val workflowUsageRepository = WorkflowUsageRepository(prefs)

    val historyRepository = HistoryRepository(HistoryStore(app))
    val objectInfoCache = ObjectInfoCache(app.cacheDir)
    val workflowRepository = WorkflowRepository(serverSession, objectInfoCache)

    val galleryExporter = GalleryExporter(app)
    val incomingMedia = IncomingMediaRepository()
    val navigationRequests = NavigationRequestRepository()

    val generationTaskStore = GenerationTaskStore(app)
    val generationManager = GenerationManager(this, app)
}
