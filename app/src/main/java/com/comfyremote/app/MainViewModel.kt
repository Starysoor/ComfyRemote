package com.comfyremote.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class Screen { CONNECT, AI_SETTINGS, UPDATE, WORKFLOWS, GENERATE, HISTORY }

/** One labeled prompt text kept in history (full text, copyable). */
data class LabeledText(val label: String, val value: String)

data class UpdateUi(
    val status: String? = null,
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
    val running: Boolean = false,
    val status: String? = null,
    val queueInfo: String? = null,
    val progress: Float? = null,
    val preview: Bitmap? = null,
    val results: List<Bitmap> = emptyList(),
    val resultPaths: List<String> = emptyList(),
    val error: String? = null,
)

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val url: String,
    val servers: List<String> = emptyList(),
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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val CHANNEL_RUNNING = "gen_running"
        const val CHANNEL_DONE = "gen_done"
    }

    private val prefs = app.getSharedPreferences("comfyremote", Application.MODE_PRIVATE)
    private val history = HistoryStore(app)
    private val _state = MutableStateFlow(UiState(url = prefs.getString("url", null) ?: "http://10.0.2.2:8188"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: ComfyApi? = null
    private var serverVersion = ""
    private var converted: ConvertedWorkflow? = null
    private var currentWfPath = ""
    private var genJob: Job? = null

    init {
        _state.value = _state.value.copy(
            servers = strList(prefs.getString("servers", null)),
            history = history.load(),
            skills = AiSkills.load(prefs),
        )
        maybeAutoCheckUpdate()
    }

    private fun mutateState(f: (UiState) -> UiState) {
        _state.value = f(_state.value)
    }

    private fun mutateGen(f: (GenState) -> GenState) {
        mutateState { s -> s.copy(gen = s.gen?.let(f)) }
    }

    // ---------- connect / workflows ----------

    fun connect(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isEmpty()) return
        mutateState { it.copy(connecting = true, connError = null, sysInfo = null) }
        viewModelScope.launch {
            try {
                val a = ComfyApi(clean)
                val info = a.systemStats()
                api = a
                serverVersion = info.version
                objectInfo = null
                prefs.edit().putString("url", clean).apply()
                mutateState {
                    it.copy(
                        url = clean, connecting = false, screen = Screen.WORKFLOWS,
                        servers = rememberServer(clean),
                        sysInfo = "ComfyUI ${info.version} · ${info.device}${if (info.vramGb.isNotEmpty()) " · ${info.vramGb}" else ""}"
                    )
                }
                loadWorkflows()
            } catch (e: Exception) {
                mutateState { it.copy(connecting = false, connError = "连接失败：${e.message}") }
            }
        }
    }

    private fun rememberServer(url: String): List<String> {
        val list = (_state.value.servers - url) + url
        val kept = list.takeLast(5)
        prefs.edit().putString("servers", JSONArray(kept.toList()).toString()).apply()
        return kept.reversed()
    }

    fun loadWorkflows() {
        val a = api ?: return
        mutateState { it.copy(loadingWf = true, wfError = null) }
        viewModelScope.launch {
            try {
                val list = a.listWorkflows()
                mutateState { it.copy(loadingWf = false, workflows = list) }
            } catch (e: Exception) {
                mutateState { it.copy(loadingWf = false, wfError = "获取工作流失败：${e.message}") }
            }
        }
    }

    private var objectInfo: JSONObject? = null

    /** Disk cache of /object_info keyed by server; invalidated when ComfyUI version changes. */
    private suspend fun loadObjectInfo(a: ComfyApi): JSONObject {
        objectInfo?.let { return it }
        val cacheFile = File(getApplication<Application>().cacheDir, "object_info_${a.base.hashCode()}.json")
        if (cacheFile.isFile) {
            runCatching {
                val o = JSONObject(cacheFile.readText())
                if (o.optString("version") == serverVersion) {
                    val data = o.optJSONObject("data")
                    if (data != null) return data.also { objectInfo = it }
                }
            }
        }
        val fresh = a.objectInfo()
        runCatching {
            cacheFile.writeText(JSONObject().put("version", serverVersion).put("data", fresh).toString())
        }
        objectInfo = fresh
        return fresh
    }

    fun openWorkflow(entry: WorkflowEntry) {
        val a = api ?: return
        viewModelScope.launch {
            mutateState { it.copy(busy = "正在加载工作流…") }
            try {
                val info = loadObjectInfo(a)
                val json = a.fetchWorkflow(entry.path)
                val conv = WorkflowConverter.convert(json, info)
                converted = conv
                currentWfPath = entry.path
                val titles = conv.prompt.keys().asSequence().associate { nid ->
                    nid to (conv.prompt.optJSONObject(nid)?.optJSONObject("_meta")?.optString("title") ?: "")
                }
                mutateState {
                    it.copy(
                        busy = null, screen = Screen.GENERATE,
                        gen = GenState(
                            wfName = entry.name, fields = conv.fields,
                            warnings = conv.warnings, notes = conv.notes, nodeTitles = titles
                        )
                    )
                }
            } catch (e: Exception) {
                mutateState { it.copy(busy = null, wfError = "加载失败：${e.message}") }
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
        val a = api ?: return
        if (g.running) return
        val ctx = getApplication<Application>()
        ensureNotificationChannels(ctx)
        ContextCompat.startForegroundService(ctx, Intent(ctx, GenService::class.java))
        mutateGen {
            it.copy(running = true, error = null, results = emptyList(), resultPaths = emptyList(),
                preview = null, status = "排队中…", progress = null)
        }
        val clientId = UUID.randomUUID().toString()
        val values = g.values
        val randomSeed = g.randomSeed
        genJob = viewModelScope.launch {
            var pid = ""
            try {
                val (prompt, seedsUsed) = WorkflowConverter.buildPrompt(cv, values, randomSeed)
                pid = a.queuePrompt(prompt, clientId)
                if (seedsUsed.isNotEmpty()) mutateGen { it.copy(values = it.values + seedsUsed) }

                // WS is best-effort live progress/preview; completion authority is polling /history
                val wsJob = launch {
                    runCatching {
                        a.wsEvents(clientId).collect { ev ->
                            when (ev) {
                                is WsEvent.Preview -> mutateGen { it.copy(preview = ev.bitmap) }
                                is WsEvent.Json -> handleWsEvent(ev.obj, pid)
                            }
                        }
                    }
                }
                val entry = pollHistory(a, pid)
                wsJob.cancel()

                extractError(entry)?.let { err ->
                    mutateGen { it.copy(running = false, error = err, status = "失败") }
                    return@launch
                }

                val imgs = parseImages(entry)
                val results = mutableListOf<Bitmap>()
                val paths = mutableListOf<String>()
                for ((idx, im) in imgs.withIndex()) {
                    try {
                        val bytes = a.downloadImage(im)
                        decodeBitmap(bytes)?.let { results.add(it) }
                        val f = history.newImageFile(pid, idx)
                        withContext(Dispatchers.IO) { f.writeBytes(bytes) }
                        paths.add(f.absolutePath)
                    } catch (_: Exception) {
                    }
                }
                val summary = cv.fields.firstOrNull { it.kind == FieldKind.TEXT }
                    ?.let { (values[it.key] ?: it.value).take(50) } ?: g.wfName
                val finalValues = values + seedsUsed
                val prompts = cv.fields.filter { it.kind == FieldKind.TEXT }
                    .map { LabeledText(it.label, finalValues[it.key] ?: it.value) }
                    .filter { it.value.isNotBlank() }
                val seeds = cv.fields.filter { it.kind == FieldKind.SEED }
                    .mapNotNull { finalValues[it.key] }.joinToString(", ")
                history.add(
                    HistoryItem(pid, currentWfPath, g.wfName, System.currentTimeMillis(),
                        finalValues, randomSeed, paths, summary, prompts, seeds)
                )
                mutateState { s -> s.copy(history = history.load()) }
                mutateGen { cur ->
                    cur.copy(
                        running = false, progress = 1f,
                        status = if (results.isEmpty()) "完成（没有输出图片）" else "完成",
                        results = results, resultPaths = paths
                    )
                }
                notifyDone(g.wfName, results.size)
            } catch (e: Exception) {
                mutateGen { it.copy(running = false, error = e.message ?: e.toString(), status = "失败") }
            } finally {
                ctx.stopService(Intent(ctx, GenService::class.java))
            }
        }
    }

    private fun handleWsEvent(ev: JSONObject, pid: String) {
        val data = ev.optJSONObject("data")
        when (ev.optString("type")) {
            "status" -> {
                val q = data?.optJSONObject("status")?.optJSONObject("exec_info")
                    ?.optInt("queue_remaining", -1) ?: -1
                if (q >= 0) mutateGen { it.copy(queueInfo = "队列中任务: $q") }
            }
            "execution_start" -> if (data?.optString("prompt_id") == pid)
                mutateGen { it.copy(status = "执行中…", progress = 0f) }
            "executing" -> if (data?.optString("prompt_id") == pid) {
                val node = data.optString("node")
                if (node.isNotEmpty() && node != "null") mutateGen { s ->
                    s.copy(status = ("执行节点 #$node " + (s.nodeTitles[node] ?: "")).trim(), progress = null)
                }
            }
            "progress" -> {
                val p = data?.optString("prompt_id")
                if (data != null && data.has("value") && (p.isNullOrEmpty() || p == pid)) {
                    val v = data.optDouble("value", 0.0)
                    val m = data.optDouble("max", 1.0).coerceAtLeast(1e-6)
                    mutateGen { it.copy(progress = (v / m).toFloat().coerceIn(0f, 1f)) }
                }
            }
        }
    }

    /** /history/{id} is written only after execution finishes — authoritative completion. */
    private suspend fun pollHistory(a: ComfyApi, promptId: String): JSONObject {
        val deadline = System.currentTimeMillis() + 30 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            a.historyEntry(promptId)?.let { return it }
            delay(4000)
        }
        throw ComfyException("等待结果超时（30 分钟），请稍后在历史记录或服务器 output 目录查看")
    }

    private fun extractError(entry: JSONObject): String? {
        val status = entry.optJSONObject("status") ?: return null
        if (status.optString("status_str") != "error") return null
        val sb = StringBuilder("执行出错")
        status.optJSONArray("messages")?.let { msgs ->
            for (i in 0 until msgs.length()) {
                val m = msgs.optJSONArray(i) ?: continue
                if (m.optString(0) == "execution_error") {
                    val d = m.optJSONObject(1)
                    if (d != null) sb.append("\n").append(d.optString("node_type"))
                        .append("：").append(d.optString("exception_message"))
                }
            }
        }
        return sb.toString()
    }

    private fun parseImages(entry: JSONObject): List<OutputImage> {
        val out = mutableListOf<OutputImage>()
        val outputs = entry.optJSONObject("outputs") ?: return out
        outputs.keys().forEach { k ->
            val o = outputs.optJSONObject(k) ?: return@forEach
            val imgs = o.optJSONArray("images") ?: return@forEach
            for (i in 0 until imgs.length()) {
                val im = imgs.optJSONObject(i) ?: continue
                out.add(OutputImage(im.optString("filename"), im.optString("subfolder"), im.optString("type", "output")))
            }
        }
        return out
    }

    fun cancelGenerate() {
        val a = api ?: return
        viewModelScope.launch { runCatching { a.interrupt() } }
    }

    // ---------- uploads ----------

    fun uploadImage(key: String, uri: Uri) {
        val a = api ?: return
        viewModelScope.launch {
            mutateState { it.copy(busy = "上传图片…") }
            try {
                val uploaded = uploadOne(a, uri, 0)
                mutateGen { g -> g.copy(values = g.values + (key to uploaded)) }
                mutateState { it.copy(busy = null) }
            } catch (e: Exception) {
                mutateState { it.copy(busy = null) }
                Toast.makeText(getApplication(), "上传失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Uploads several images and fills them into the IMAGE fields in order. */
    fun uploadImagesBatch(uris: List<Uri>) {
        val a = api ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mutateState { it.copy(busy = "上传图片（0/${uris.size}）…") }
            try {
                val uploaded = mutableListOf<String>()
                uris.forEachIndexed { i, uri ->
                    mutateState { it.copy(busy = "上传图片（${i + 1}/${uris.size}）…") }
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
                Toast.makeText(getApplication(), "上传失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadOne(a: ComfyApi, uri: Uri, seq: Int): String {
        val ctx = getApplication<Application>()
        val bytes = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw ComfyException("读取图片失败")
        val ext = ctx.contentResolver.getType(uri)
            ?.substringAfterLast('/', "png")?.takeIf { it.length in 2..5 } ?: "png"
        return a.uploadImage(bytes, "mobile_${System.currentTimeMillis()}_$seq.$ext")
    }

    // ---------- AI prompt expansion ----------

    fun openAiSettings() {
        mutateState {
            it.copy(
                screen = Screen.AI_SETTINGS,
                aiBase = prefs.getString("aiBase", "") ?: "",
                aiKey = prefs.getString("aiKey", "") ?: "",
                aiModel = prefs.getString("aiModel", "") ?: "",
                aiTemp = prefs.getString("aiTemp", "0.8") ?: "0.8",
                aiMaxTok = prefs.getString("aiMaxTok", "") ?: "",
                aiExtra = prefs.getString("aiExtra", "") ?: "",
                aiThinking = prefs.getString("aiThinking", "default") ?: "default",
                skills = AiSkills.load(prefs),
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
        prefs.edit()
            .putString("aiBase", s.aiBase.trim())
            .putString("aiKey", s.aiKey.trim())
            .putString("aiModel", s.aiModel.trim())
            .putString("aiTemp", s.aiTemp.trim())
            .putString("aiMaxTok", s.aiMaxTok.trim())
            .putString("aiExtra", s.aiExtra.trim())
            .putString("aiThinking", s.aiThinking)
            .apply()
        Toast.makeText(getApplication(), "AI 设置已保存", Toast.LENGTH_SHORT).show()
    }

    // ---------- skill management ----------

    private fun persistSkills(skills: List<AiSkillDef>) {
        AiSkills.save(prefs, skills)
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
            else -> return
        }
        saveSkill(def)
    }

    private fun aiTunables(): AiTunables {
        val s = _state.value
        val temp = s.aiTemp.trim().toDoubleOrNull()
        val maxTok = s.aiMaxTok.trim().toIntOrNull()
        val extra = runCatching { JSONObject(s.aiExtra) }.getOrNull()
        return AiTunables(temp, maxTok, extra)
    }

    fun testAi() {
        val s = _state.value
        val base = s.aiBase.trim()
        val key = s.aiKey.trim()
        val model = s.aiModel.trim()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            mutateState { it.copy(aiTest = "请先填写完整三项") }
            return
        }
        mutateState { it.copy(aiTesting = true, aiTest = null) }
        viewModelScope.launch {
            try {
                val reply = AiClient.chat(
                    base, key, model,
                    "你是连通性测试。收到任何消息都只回复：连接成功",
                    "ping", aiTunables(),
                )
                mutateState { it.copy(aiTesting = false, aiTest = "成功：${reply.take(80)}") }
            } catch (e: Exception) {
                mutateState { it.copy(aiTesting = false, aiTest = "失败：${e.message}".take(200)) }
            }
        }
    }

    private fun aiConfigured(): Triple<String, String, String>? {
        val base = prefs.getString("aiBase", "")?.trim().orEmpty()
        val key = prefs.getString("aiKey", "")?.trim().orEmpty()
        val model = prefs.getString("aiModel", "")?.trim().orEmpty()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            Toast.makeText(getApplication(), "请先在连接页配置 AI 扩写设置", Toast.LENGTH_LONG).show()
            return null
        }
        return Triple(base, key, model)
    }

    fun aiExpand(fieldKey: String, currentText: String, skill: AiSkillDef) {
        val cfg = aiConfigured() ?: return
        if (currentText.isBlank()) {
            Toast.makeText(getApplication(), "先写几个字，AI 才知道往哪个方向扩写", Toast.LENGTH_SHORT).show()
            return
        }
        mutateState { it.copy(aiBusy = "$fieldKey|${skill.id}") }
        viewModelScope.launch {
            try {
                val out = AiClient.chat(cfg.first, cfg.second, cfg.third, skill.systemPrompt, currentText, aiTunables())
                mutateState { it.copy(aiBusy = null, aiResult = AiResult(fieldKey, out)) }
            } catch (e: Exception) {
                mutateState { it.copy(aiBusy = null) }
                Toast.makeText(getApplication(), "AI 调用失败：${e.message}".take(180), Toast.LENGTH_LONG).show()
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
        val last = prefs.getLong("lastUpdateCheck", 0L)
        if (System.currentTimeMillis() - last < 24 * 3600_000L) return
        viewModelScope.launch { runCatching { doCheckUpdate(prefs.getString("ghToken", "")?.trim()) } }
    }

    fun openUpdateScreen() {
        mutateState {
            it.copy(screen = Screen.UPDATE, ghToken = prefs.getString("ghToken", "") ?: "", update = it.update)
        }
    }

    fun updateGhToken(v: String) = mutateState { it.copy(ghToken = v) }

    fun checkUpdate() {
        val token = _state.value.ghToken.trim().ifBlank { null }
        prefs.edit().putString("ghToken", token ?: "").apply()
        mutateState { it.copy(update = UpdateUi(status = "正在检查…")) }
        viewModelScope.launch {
            try {
                doCheckUpdate(token)
            } catch (e: Exception) {
                mutateState { it.copy(update = UpdateUi(status = "检查失败：${e.message}".take(200))) }
            }
        }
    }

    private suspend fun doCheckUpdate(token: String?) {
        val rel = Updater.latest(token)
        prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
        val cur = currentVersion()
        if (Updater.compareVersions(rel.tag, cur) > 0) {
            mutateState { it.copy(update = UpdateUi(release = rel)) }
        } else {
            mutateState { it.copy(update = UpdateUi(status = "已是最新（v$cur）")) }
        }
    }

    fun downloadUpdate() {
        val rel = _state.value.update?.release ?: return
        val token = prefs.getString("ghToken", "")?.trim()?.ifBlank { null }
        mutateState { it.copy(update = UpdateUi(release = rel, downloading = true, progress = 0f)) }
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val dest = File(ctx.cacheDir, "update/comfyremote_${rel.tag}.apk")
                Updater.download(token, rel, dest) { p ->
                    mutateState { it.copy(update = it.update?.copy(progress = p)) }
                }
                mutateState { it.copy(update = it.update?.copy(downloading = false, progress = 1f)) }
                Updater.installApk(ctx, dest)
            } catch (e: Exception) {
                mutateState { it.copy(update = UpdateUi(status = "下载失败：${e.message}".take(200))) }
            }
        }
    }

    // ---------- history ----------

    fun reopenFromHistory(item: HistoryItem) {
        val a = api ?: return
        if (item.wfPath.isEmpty()) {
            Toast.makeText(getApplication(), "该记录没有关联工作流，无法重新生成", Toast.LENGTH_SHORT).show()
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
                g.copy(values = item.values, randomSeed = item.randomSeed,
                    results = emptyList(), resultPaths = emptyList())
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        history.delete(id)
        mutateState { it.copy(history = history.load()) }
    }

    // ---------- misc ----------

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "comfy_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyRemote")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw ComfyException("无法创建媒体文件")
                ctx.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "已保存到 相册/ComfyRemote", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openHistory() {
        mutateState { it.copy(screen = Screen.HISTORY) }
    }

    fun back() {
        when (_state.value.screen) {
            Screen.GENERATE -> {
                if (_state.value.gen?.running == true) cancelGenerate()
                mutateState { it.copy(screen = Screen.WORKFLOWS) }
            }
            Screen.AI_SETTINGS -> mutateState { it.copy(screen = Screen.CONNECT) }
            Screen.UPDATE -> mutateState { it.copy(screen = Screen.CONNECT) }
            Screen.HISTORY -> mutateState { it.copy(screen = Screen.WORKFLOWS) }
            Screen.WORKFLOWS -> mutateState { it.copy(screen = Screen.CONNECT) }
            else -> {}
        }
    }

    // ---------- notifications ----------

    fun ensureNotificationChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "生成状态", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "生成完成", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun notifyDone(wfName: String, count: Int) {
        val ctx = getApplication<Application>()
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val notif: Notification = NotificationCompat.Builder(ctx, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("生成完成")
            .setContentText("$wfName · $count 张图")
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(2, notif) }
    }

    // ---------- helpers ----------

    private fun decodeBitmap(bytes: ByteArray): Bitmap? =
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun strList(s: String?): List<String> = runCatching {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).map { a.optString(it) }
    }.getOrDefault(emptyList())
}
