package com.comfyremote.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.media.IncomingMediaRepository
import com.comfyremote.app.data.repository.AiSettings
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Screen { CONNECT, AI_SETTINGS, UPDATE, WORKFLOWS, GENERATE, HISTORY, TASKS }

/** One labeled prompt text kept in history (full text, copyable). */
data class LabeledText(val label: String, val value: String)

/** A saved server address with a user-editable alias. */
data class ServerEntry(val name: String, val url: String)

data class UpdateUi(
    val status: String? = null,
    val isError: Boolean = false,
    val release: GhRelease? = null,
    val downloading: Boolean = false,
    val progress: Float? = null,
)

data class GenState(
    val wfName: String,
    val fields: List<PromptField>,
    val warnings: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val nodeTitles: Map<String, String> = emptyMap(),
    val values: Map<String, String> = emptyMap(),
    val randomSeed: Boolean = true,
    val activeTaskId: String? = null,
)

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val url: String,
    val connecting: Boolean = false,
    val connError: String? = null,
    val sysInfo: String? = null,
    val workflows: List<WorkflowEntry> = emptyList(),
    val loadingWf: Boolean = false,
    val wfError: String? = null,
    val busy: String? = null,
    val gen: GenState? = null,
    val history: List<HistoryItem> = emptyList(),
    // AI prompt-expansion settings (edited on the settings screen)
    val aiBase: String = "",
    val aiKey: String = "",
    val aiModel: String = "",
    val aiTemp: String = "0.8",
    val aiMaxTok: String = "",
    val aiExtra: String = "",
    val aiThinking: String = "default",
    val skills: List<AiSkillDef> = AiSkillDef.defaults(),
    val aiTest: String? = null,
    val aiTesting: Boolean = false,
    // "$fieldKey|${skill.id}" while a request is in flight
    val aiBusy: String? = null,
    val aiResult: AiResult? = null,
    val update: UpdateUi? = null,
    val ghToken: String = "",
    val servers: List<ServerEntry> = emptyList(),
    val pendingShare: Boolean = false,
    val scanning: Boolean = false,
    val scanProgress: Int? = null,
    val scanFound: List<ServerEntry> = emptyList(),
    val scanError: String? = null,
    val bookmarks: List<PromptPreset> = emptyList(),
    val currentWfPath: String = "",
)

/** True when ComfyUI marked this prompt as user-interrupted (not a real error). */
fun historyCancelled(entry: JSONObject): Boolean {
    val msgs = entry.optJSONObject("status")?.optJSONArray("messages") ?: return false
    for (i in 0 until msgs.length()) {
        val m = msgs.optJSONArray(i) ?: continue
        if (m.optString(0) == "execution_interrupted") return true
    }
    return false
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val CHANNEL_RUNNING = "gen_running"
        const val CHANNEL_DONE = "gen_done"
    }

    private val container get() = (getApplication() as ComfyRemoteApp).container
    private val session get() = container.serverSession

    private val _state = MutableStateFlow(UiState(url = container.serverRepository.lastUrl()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var converted: ConvertedWorkflow? = null
    private var currentWfPath = ""
    private var genJob: Job? = null

    init {
        _state.value = _state.value.copy(
            servers = container.serverRepository.load(),
            history = container.historyRepository.load(),
            skills = container.aiSkillStore.load(),
            bookmarks = container.promptPresetRepository.load(),
        )
        maybeAutoCheckUpdate()
        // GenerationManager runs app-scoped; when a task succeeds its images are
        // already persisted - refresh the history list so it appears immediately.
        val syncedTaskIds = mutableSetOf<String>()
        viewModelScope.launch {
            container.generationManager.tasks.collect { tasks ->
                var changed = false
                for (t in tasks) {
                    if (t.phase == GenerationPhase.SUCCEEDED && syncedTaskIds.add(t.taskId)) changed = true
                }
                if (changed) mutateState { it.copy(history = container.historyRepository.load()) }
            }
        }
    }

    private fun mutateState(f: (UiState) -> UiState) {
        _state.value = f(_state.value)
    }

    private fun mutateGen(f: (GenState) -> GenState) {
        mutateState { s -> s.copy(gen = s.gen?.let(f)) }
    }

    private fun toast(msg: String, long: Boolean = false) {
        Toast.makeText(getApplication(), msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // ---------- connect / workflows ----------

    fun connect(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isEmpty()) return
        mutateState { it.copy(connecting = true, connError = null, sysInfo = null) }
        viewModelScope.launch {
            try {
                val info = session.connect(clean)
                container.objectInfoCache.invalidate()
                container.serverRepository.saveLastUrl(clean)
                mutateState {
                    it.copy(
                        url = clean, connecting = false, screen = Screen.WORKFLOWS,
                        servers = container.serverRepository.remember(clean),
                        sysInfo = "ComfyUI ${info.version} · ${info.device}${if (info.vramGb.isNotEmpty()) " · ${info.vramGb}" else ""}"
                    )
                }
                loadWorkflows()
            } catch (e: Exception) {
                mutateState { it.copy(connecting = false, connError = L10n.t("连接失败：", "Connection failed: ") + (e.message ?: "error")) }
            }
        }
    }

    fun loadWorkflows() {
        if (!session.connected) return
        mutateState { it.copy(loadingWf = true, wfError = null) }
        viewModelScope.launch {
            try {
                val list = container.workflowRepository.list()
                mutateState { it.copy(loadingWf = false, workflows = list) }
            } catch (e: Exception) {
                mutateState { it.copy(loadingWf = false, wfError = L10n.t("获取工作流失败：", "Failed to list workflows: ") + (e.message ?: "error")) }
            }
        }
    }

    fun openWorkflow(entry: WorkflowEntry) {
        if (!session.connected) return
        viewModelScope.launch {
            mutateState { it.copy(busy = L10n.t("正在加载工作流…", "Loading workflow…")) }
            try {
                val conv = container.workflowRepository.convert(entry)
                converted = conv
                currentWfPath = entry.path
                val titles = conv.prompt.keys().asSequence().associate { nid ->
                    nid to (conv.prompt.optJSONObject(nid)?.optJSONObject("_meta")?.optString("title") ?: "")
                }
                mutateState {
                    it.copy(
                        busy = null, screen = Screen.GENERATE, currentWfPath = entry.path,
                        gen = GenState(
                            wfName = entry.name, fields = conv.fields,
                            warnings = conv.warnings, notes = conv.notes, nodeTitles = titles
                        )
                    )
                }
                tryConsumePendingShare()
            } catch (e: Exception) {
                mutateState { it.copy(busy = null, wfError = L10n.t("加载失败：", "Load failed: ") + (e.message ?: "error")) }
            }
        }
    }

    // ---------- generate ----------

    fun updateValue(key: String, v: String) {
        mutateGen { g -> g.copy(values = g.values + (key to v)) }
    }

    fun toggleRandomSeed() {
        mutateGen { g -> g.copy(randomSeed = !g.randomSeed) }
    }

    fun generate() {
        val g = _state.value.gen ?: return
        val cv = converted ?: return
        if (!session.connected) return
        if (g.activeTaskId != null && manager.tasks.value
                .firstOrNull { it.taskId == g.activeTaskId }?.isActive == true) return
        val request = com.comfyremote.app.data.generation.GenerationRequest(
            serverUrl = session.url,
            workflowPath = currentWfPath,
            workflowName = g.wfName,
            fieldValues = g.values,
            randomSeed = g.randomSeed,
            nodeTitles = g.nodeTitles,
            fields = g.fields,
            convertedPrompt = JSONObject(cv.prompt.toString()),
        )
        val taskId = manager.submit(request)
        mutateGen { it.copy(activeTaskId = taskId) }
    }

    fun cancelActiveTask() {
        val id = _state.value.gen?.activeTaskId ?: return
        viewModelScope.launch { manager.cancel(id) }
    }

    fun retryTask(taskId: String): String? = manager.retry(taskId)

    val tasks = manager.tasks

    private val manager get() = container.generationManager

    fun cancelGenerate() = cancelActiveTask()

    fun cancelTask(taskId: String) {
        viewModelScope.launch { manager.cancel(taskId) }
    }

    // ---------- uploads ----------

    fun uploadImage(key: String, uri: Uri) {
        val a = session.require()
        viewModelScope.launch {
            mutateState { it.copy(busy = L10n.t("上传图片…", "Uploading…")) }
            try {
                val uploaded = uploadOne(a, uri, 0)
                mutateGen { g -> g.copy(values = g.values + (key to uploaded)) }
                mutateState { it.copy(busy = null) }
            } catch (e: Exception) {
                mutateState { it.copy(busy = null) }
                toast(L10n.t("上传失败：", "Upload failed: ") + (e.message ?: "error"), long = true)
            }
        }
    }

    /** Uploads several images and fills them into the IMAGE fields in order. */
    fun uploadImagesBatch(uris: List<Uri>) {
        val a = session.require()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mutateState { it.copy(busy = L10n.t("上传图片（0/${uris.size}）…", "Uploading (0/${uris.size})…")) }
            try {
                val uploaded = mutableListOf<String>()
                uris.forEachIndexed { i, uri ->
                    mutateState { it.copy(busy = L10n.t("上传图片（${i + 1}/${uris.size}）…", "Uploading (${i + 1}/${uris.size})…")) }
                    uploaded.add(uploadOne(a, uri, i))
                }
                mutateGen { g ->
                    val imgFields = g.fields.filter { it.kind == FieldKind.IMAGE }
                    var extra = g.values
                    imgFields.zip(uploaded).forEach { (f, name) -> extra = extra + (f.key to name) }
                    g.copy(values = extra)
                }
                mutateState { it.copy(busy = null) }
            } catch (e: Exception) {
                mutateState { it.copy(busy = null) }
                toast(L10n.t("上传失败：", "Upload failed: ") + (e.message ?: "error"), long = true)
            }
        }
    }

    private suspend fun uploadOne(a: ComfyApi, uri: Uri, seq: Int): String {
        val ctx = getApplication<Application>()
        val raw = withContext(kotlinx.coroutines.Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw ComfyException(L10n.t("读取图片失败", "Failed to read image"))
        val (bytes, ext) = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prepared = com.comfyremote.app.data.media.ImagePreprocessor.prepare(raw, ctx.contentResolver.getType(uri))
            prepared
        }
        return a.uploadImage(bytes, "mobile_${System.currentTimeMillis()}_$seq.$ext")
    }

    // ---------- input image thumbnails ----------

    suspend fun loadInputBitmap(name: String): Bitmap? =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { session.require().fetchInputImage(name) }.getOrNull()
        }

    // ---------- AI prompt expansion ----------

    fun openAiSettings() {
        val ai = container.aiSettingsStore.load()
        mutateState {
            it.copy(
                screen = Screen.AI_SETTINGS,
                aiBase = ai.baseUrl, aiKey = ai.apiKey, aiModel = ai.model,
                aiTemp = ai.temperatureText, aiMaxTok = ai.maxTokensText,
                aiExtra = ai.extraJson, aiThinking = ai.thinkingMode,
                skills = container.aiSkillStore.load(),
                aiTest = null,
            )
        }
    }

    fun updateAiBase(v: String) = mutateState { it.copy(aiBase = v) }
    fun updateAiKey(v: String) = mutateState { it.copy(aiKey = v) }
    fun updateAiModel(v: String) = mutateState { it.copy(aiModel = v) }
    fun updateAiTemp(v: String) = mutateState { it.copy(aiTemp = v) }
    fun updateAiMaxTok(v: String) = mutateState { it.copy(aiMaxTok = v.filter { c -> c.isDigit() }) }
    fun updateAiExtra(v: String) = mutateState { it.copy(aiExtra = v) }

    /** Maps a thinking-mode choice onto the extra-JSON body fields. */
    fun applyThinkingMode(mode: String) {
        val extra = runCatching {
            if (_state.value.aiExtra.isBlank()) JSONObject() else JSONObject(_state.value.aiExtra)
        }.getOrDefault(JSONObject())
        when (mode) {
            "glm_off" -> extra.put("thinking", JSONObject().put("type", "disabled"))
            "glm_on" -> extra.put("thinking", JSONObject().put("type", "enabled"))
            "low", "medium", "high" -> extra.put("reasoning_effort", mode)
            else -> { extra.remove("thinking"); extra.remove("reasoning_effort") }
        }
        mutateState { it.copy(aiThinking = mode, aiExtra = extra.toString()) }
    }

    fun saveAiSettings() {
        val s = _state.value
        container.aiSettingsStore.save(
            AiSettings(
                baseUrl = s.aiBase.trim(), apiKey = s.aiKey.trim(), model = s.aiModel.trim(),
                temperatureText = s.aiTemp.trim(), maxTokensText = s.aiMaxTok.trim(),
                extraJson = s.aiExtra.trim(), thinkingMode = s.aiThinking,
            )
        )
        toast(L10n.t("AI 设置已保存", "AI settings saved"))
    }

    // ---------- skill management ----------

    private fun persistSkills(skills: List<AiSkillDef>) {
        container.aiSkillStore.save(skills)
        mutateState { it.copy(skills = skills) }
    }

    fun saveSkill(def: AiSkillDef) {
        val list = _state.value.skills.toMutableList()
        val idx = list.indexOfFirst { it.id == def.id }
        if (idx >= 0) list[idx] = def else list.add(def)
        persistSkills(list)
    }

    fun addSkill(label: String, prompt: String) {
        val id = "custom_${System.currentTimeMillis()}"
        persistSkills(_state.value.skills + AiSkillDef(id, label, prompt, false))
    }

    fun deleteSkill(id: String) {
        persistSkills(_state.value.skills.filter { it.id != id })
    }

    fun resetSkill(id: String) {
        val def = when (id) {
            AiSkillDef.DEFAULT_NATURAL_ID -> AiSkillDef.NATURAL_DEFAULT
            AiSkillDef.DEFAULT_TAGS_ID -> AiSkillDef.TAGS_DEFAULT
            AiSkillDef.DEFAULT_EN_ID -> AiSkillDef.EN_EXPAND_DEFAULT
            AiSkillDef.DEFAULT_NEG_ID -> AiSkillDef.NEGATIVE_DEFAULT
            AiSkillDef.DEFAULT_TRANSLATE_ID -> AiSkillDef.TRANSLATE_DEFAULT
            else -> return
        }
        saveSkill(def)
    }

    private fun aiTunables() = container.aiSettingsStore.load().asTunables()

    fun testAi() {
        val s = _state.value
        val base = s.aiBase.trim()
        val key = s.aiKey.trim()
        val model = s.aiModel.trim()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            mutateState { it.copy(aiTest = L10n.t("请先填写完整三项", "Please fill in all three fields")) }
            return
        }
        mutateState { it.copy(aiTesting = true, aiTest = null) }
        viewModelScope.launch {
            try {
                val reply = AiClient.chat(
                    base, key, model,
                    L10n.t("你是连通性测试。收到任何消息都只回复：连接成功", "Connectivity test. Reply with exactly: OK"),
                    "ping", aiTunables(),
                )
                mutateState { it.copy(aiTesting = false, aiTest = L10n.t("成功：", "OK: ") + reply.take(80)) }
            } catch (e: Exception) {
                mutateState { it.copy(aiTesting = false, aiTest = L10n.t("失败：", "Failed: ") + (e.message ?: "error").take(200)) }
            }
        }
    }

    private fun aiConfigured(): Triple<String, String, String>? {
        val ai = container.aiSettingsStore.load()
        if (!ai.configured) {
            toast(L10n.t("请先在连接页配置 AI 扩写设置", "Configure AI settings first (from the connect screen)"), long = true)
            return null
        }
        return Triple(ai.baseUrl, ai.apiKey, ai.model)
    }

    fun aiExpand(fieldKey: String, currentText: String, skill: AiSkillDef) {
        val cfg = aiConfigured() ?: return
        if (currentText.isBlank()) {
            toast(L10n.t("先写几个字，AI 才知道往哪个方向扩写", "Type a few words first so the AI knows the direction"))
            return
        }
        mutateState { it.copy(aiBusy = "$fieldKey|${skill.id}") }
        viewModelScope.launch {
            try {
                val out = AiClient.chat(cfg.first, cfg.second, cfg.third, skill.systemPrompt, currentText, aiTunables())
                mutateState { it.copy(aiBusy = null, aiResult = AiResult(fieldKey, out)) }
            } catch (e: Exception) {
                mutateState { it.copy(aiBusy = null) }
                toast((L10n.t("AI 调用失败：", "AI call failed: ") + (e.message ?: "error")).take(180), long = true)
            }
        }
    }

    fun aiApply(replace: Boolean, newText: String) {
        val r = _state.value.aiResult ?: return
        mutateGen { g ->
            val f = g.fields.firstOrNull { it.key == r.fieldKey }
            val merged = if (replace) newText
            else {
                val cur = g.values[r.fieldKey] ?: f?.value ?: ""
                if (cur.isBlank()) newText else "$cur, $newText"
            }
            g.copy(values = g.values + (r.fieldKey to merged))
        }
        aiDismiss()
    }

    fun aiDismiss() = mutateState { it.copy(aiResult = null) }

    // ---------- app update ----------

    private fun currentVersion(): String = try {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    fun currentVersionPublic(): String = currentVersion()

    fun maybeAutoCheckUpdate() {
        if (System.currentTimeMillis() - container.updateSettingsStore.lastUpdateCheck() < 24 * 3600_000L) return
        viewModelScope.launch { runCatching { doCheckUpdate(container.updateSettingsStore.ghToken()) } }
    }

    fun openUpdateScreen() {
        mutateState {
            it.copy(screen = Screen.UPDATE, ghToken = container.updateSettingsStore.ghToken() ?: "", update = it.update)
        }
    }

    fun updateGhToken(v: String) = mutateState { it.copy(ghToken = v) }

    fun checkUpdate() {
        val token = _state.value.ghToken.trim().ifBlank { null }
        container.updateSettingsStore.saveGhToken(token)
        mutateState { it.copy(update = UpdateUi(status = L10n.t("正在检查…", "Checking…"))) }
        viewModelScope.launch {
            try {
                doCheckUpdate(token)
            } catch (e: Exception) {
                mutateState { it.copy(update = UpdateUi(status = L10n.t("检查失败：", "Check failed: ") + (e.message ?: "error").take(200), isError = true)) }
            }
        }
    }

    private suspend fun doCheckUpdate(token: String?) {
        val rel = Updater.latest(token)
        container.updateSettingsStore.markUpdateChecked()
        val cur = currentVersion()
        if (Updater.compareVersions(rel.tag, cur) > 0) {
            mutateState { it.copy(update = UpdateUi(release = rel)) }
        } else {
            mutateState { it.copy(update = UpdateUi(status = L10n.t("已是最新（v", "Up to date (v") + cur + L10n.t("）", ")"))) }
        }
    }

    fun downloadUpdate() {
        val rel = _state.value.update?.release ?: return
        val token = container.updateSettingsStore.ghToken()
        mutateState { it.copy(update = UpdateUi(release = rel, downloading = true, progress = 0f)) }
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val dest = java.io.File(ctx.cacheDir, "update/comfyremote_${rel.tag}.apk")
                Updater.download(token, rel, dest) { p ->
                    mutateState { it.copy(update = it.update?.copy(progress = p)) }
                }
                mutateState { it.copy(update = it.update?.copy(downloading = false, progress = 1f)) }
                Updater.installApk(ctx, dest)
            } catch (e: Exception) {
                mutateState { it.copy(update = UpdateUi(status = L10n.t("下载失败：", "Download failed: ") + (e.message ?: "error").take(200), isError = true)) }
            }
        }
    }

    // ---------- history ----------

    fun reopenFromHistory(item: HistoryItem) {
        if (!session.connected) return
        if (item.wfPath.isEmpty()) {
            toast(L10n.t("该记录没有关联工作流，无法重新生成", "No workflow linked to this entry"))
            return
        }
        // clear any leftover generate state so the waiter below sees the freshly loaded workflow
        mutateState { it.copy(gen = null, screen = Screen.WORKFLOWS, wfError = null) }
        openWorkflow(WorkflowEntry(item.wfPath, item.wfName, ""))
        // apply stored edits once the generate screen loads
        viewModelScope.launch {
            var waited = 0
            while (_state.value.gen == null && waited < 15_000) {
                delay(200); waited += 200
            }
            mutateGen { g ->
                g.copy(values = item.values, randomSeed = item.randomSeed)
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        container.historyRepository.delete(id)
        mutateState { it.copy(history = container.historyRepository.load()) }
    }

    fun deleteHistoryItems(ids: Collection<String>) {
        container.historyRepository.deleteMany(ids)
        mutateState { it.copy(history = container.historyRepository.load()) }
    }

    // ---------- misc ----------

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = getApplication<Application>()
            try {
                container.galleryExporter.savePng(bitmap)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast(container.galleryExporter.savedMessage())
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast(container.galleryExporter.failMessage(e))
                }
            }
        }
    }

    fun openWorkflows() {
        mutateState { it.copy(screen = Screen.WORKFLOWS) }
    }

    fun openHistory() {
        mutateState { it.copy(screen = Screen.HISTORY) }
    }

    fun openTasks() {
        mutateState { it.copy(screen = Screen.TASKS) }
    }

    /** Receives an image shared from the system gallery/other apps. */
    fun handleSendIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        container.incomingMedia.add(uri)
        mutateState { it.copy(pendingShare = true) }
        tryConsumePendingShare()
    }

    /** Fills the first reference-image slot of the open workflow with the shared image. */
    fun tryConsumePendingShare() {
        val g = _state.value.gen ?: return
        if (g.fields.none { it.kind == FieldKind.IMAGE }) return
        val uris = container.incomingMedia.drain()
        if (uris.isEmpty()) return
        mutateState { it.copy(pendingShare = false) }
        uploadImagesBatch(uris)
    }

    // ---------- LAN scan ----------

    fun scanLan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            container.lanDiscovery.discover().collect { ev ->
                when (ev) {
                    is com.comfyremote.app.data.discovery.DiscoveryEvent.Progress ->
                        mutateState { it.copy(scanning = true, scanProgress = ev.scanned) }
                    is com.comfyremote.app.data.discovery.DiscoveryEvent.Found ->
                        mutateState { it.copy(scanFound = it.scanFound + ev.server.asEntry()) }
                    is com.comfyremote.app.data.discovery.DiscoveryEvent.Error ->
                        mutateState { it.copy(scanError = scanErrorMessage(ev.message)) }
                    com.comfyremote.app.data.discovery.DiscoveryEvent.Finished ->
                        mutateState {
                            it.copy(
                                scanning = false,
                                scanError = if (it.scanFound.isEmpty()) L10n.t("未发现 ComfyUI（确认手机与电脑在同一 Wi-Fi）", "No ComfyUI found - check that both devices are on the same Wi-Fi") else null
                            )
                        }
                }
            }
        }
    }

    private fun scanErrorMessage(code: String): String = when (code) {
        "not-on-wifi" -> L10n.t("无法确定 Wi-Fi 网段（未连接或使用流量）", "Unknown subnet - not on Wi-Fi")
        else -> code
    }

    // ---------- server management ----------

    fun renameServer(url: String, alias: String) {
        mutateState { it.copy(servers = container.serverRepository.rename(url, alias)) }
    }

    fun removeServer(url: String) {
        mutateState { it.copy(servers = container.serverRepository.remove(url)) }
    }

    // ---------- prompt bookmarks (prompt library) ----------

    fun addBookmarkFromCurrent() {
        val g = _state.value.gen ?: return
        val texts = g.fields.filter { it.kind == FieldKind.TEXT }
            .map { LabeledText(it.label, g.values[it.key] ?: it.value) }
            .filter { it.value.isNotBlank() }
        if (texts.isEmpty()) {
            toast(L10n.t("当前没有可收藏的提示词", "Nothing to bookmark yet"))
            return
        }
        addBookmark(texts.first().value.take(28), texts, currentWfPath.ifEmpty { null })
    }

    fun addBookmarkFromHistory(item: HistoryItem) {
        if (item.prompts.isEmpty()) {
            toast(L10n.t("该记录没有完整提示词可收藏", "No saved prompts for this entry"))
            return
        }
        addBookmark(item.summary.take(28), item.prompts, item.wfPath.ifEmpty { null })
    }

    private fun addBookmark(title: String, prompts: List<LabeledText>, workflowPath: String?) {
        val trimmed = prompts.filter { it.value.isNotBlank() }
        if (trimmed.isEmpty()) return
        val preset = PromptPreset(
            id = "bm_${System.currentTimeMillis()}",
            title = title,
            prompts = trimmed,
            workflowPath = workflowPath,
            createdAt = System.currentTimeMillis(),
        )
        mutateState { it.copy(bookmarks = container.promptPresetRepository.add(preset)) }
        toast(L10n.t("已收藏提示词", "Prompt bookmarked"))
    }

    fun deleteBookmark(id: String) {
        mutateState { it.copy(bookmarks = container.promptPresetRepository.delete(id)) }
    }

    /** Fills the workflow text fields in stored order. */
    fun applyBookmark(bm: PromptPreset) {
        container.promptPresetRepository.markUsed(bm.id)
        mutateGen { g ->
            var newValues = g.values
            var applied = 0
            g.fields.filter { it.kind == FieldKind.TEXT }.forEach { f ->
                bm.prompts.getOrNull(applied)?.let { pp ->
                    newValues = newValues + (f.key to pp.value)
                    applied++
                }
            }
            g.copy(values = newValues)
        }
        toast(L10n.t("已应用书签", "Bookmark applied"))
    }

    fun back() {
        when (_state.value.screen) {
            Screen.GENERATE -> {
                // leaving the page does NOT cancel the task (stage 18)
                mutateState { it.copy(screen = Screen.WORKFLOWS) }
            }
            Screen.AI_SETTINGS -> mutateState { it.copy(screen = Screen.CONNECT) }
            Screen.UPDATE -> mutateState { it.copy(screen = Screen.CONNECT) }
            Screen.HISTORY -> mutateState { it.copy(screen = Screen.WORKFLOWS) }
            Screen.TASKS -> mutateState { it.copy(screen = Screen.WORKFLOWS) }
            Screen.WORKFLOWS -> mutateState { it.copy(screen = Screen.CONNECT) }
            else -> {}
        }
    }

    // ---------- helpers ----------

    private fun decodeBitmap(bytes: ByteArray): Bitmap? =
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
