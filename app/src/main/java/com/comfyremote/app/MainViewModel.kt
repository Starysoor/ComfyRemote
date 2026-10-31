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
import com.comfyremote.app.ui.L10n
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class Screen { CONNECT, AI_SETTINGS, UPDATE, WORKFLOWS, GENERATE, HISTORY }

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

    private val prefs = app.getSharedPreferences("comfyremote", Application.MODE_PRIVATE)
    private val history = HistoryStore(app)
    private val _state = MutableStateFlow(UiState(url = prefs.getString("url", null) ?: "http://10.0.2.2:8188"))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: ComfyApi? = null
    private var serverVersion = ""
    private var converted: ConvertedWorkflow? = null
    private var currentWfPath = ""
    private var genJob: Job? = null
    private var pendingShare: Uri? = null

    init {
        _state.value = _state.value.copy(
            servers = loadServers(),
            history = history.load(),
            skills = AiSkills.load(prefs),
        )
        maybeAutoCheckUpdate()
    }

    /** Loads saved servers; migrates the legacy string-array format, defaulting alias = url. */
    private fun loadServers(): List<ServerEntry> = runCatching {
        val v2 = prefs.getString("serversV2", null)
        if (v2 != null) {
            val arr = JSONArray(v2)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ServerEntry(o.optString("name"), o.optString("url"))
            }
        }
        strList(prefs.getString("servers", null)).map { ServerEntry(it, it) }
    }.getOrDefault(emptyList())

    private fun persistServers(list: List<ServerEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("serversV2", arr.toString()).apply()
    }

    fun renameServer(url: String, alias: String) {
        val list = _state.value.servers.map { if (it.url == url) it.copy(name = alias) else it }
        persistServers(list)
        mutateState { it.copy(servers = list) }
    }

    fun removeServer(url: String) {
        val list = _state.value.servers.filter { it.url != url }
        persistServers(list)
        mutateState { it.copy(servers = list) }
    }

    private fun mutateState(f: (UiState) -> UiState) {
        _state.value = f(_state.value)
    }

    private fun mutateGen(f: (GenState) -> GenState) {
        mutateState { s -> s.copy(gen = s.gen?.let(f)) }
    }

    /** Live progress on the foreground-service notification (id 1), tap -> app. */
    private fun updateRunningNotification(statusText: String?, progress: Float?) {
        val ctx = getApplication<Application>()
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val pi = android.app.PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(L10n.t("ComfyUI 生成中", "ComfyUI generating"))
            .setContentText(statusText ?: L10n.t("处理中…", "Working…"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (progress != null) setProgress(100, (progress * 100).toInt(), false)
                else setProgress(0, 0, true)
            }
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(1, notif) }
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
                mutateState { it.copy(connecting = false, connError = L10n.t("连接失败：", "Connection failed: ") + (e.message ?: "error")) }
            }
        }
    }

    private fun rememberServer(url: String): List<ServerEntry> {
        val exists = _state.value.servers.any { it.url == url }
        val list = if (exists) _state.value.servers
        else (_state.value.servers + ServerEntry(url, url)).takeLast(5)
        persistServers(list)
        return list
    }

    fun loadWorkflows() {
        val a = api ?: return
        mutateState { it.copy(loadingWf = true, wfError = null) }
        viewModelScope.launch {
            try {
                val list = a.listWorkflows()
                mutateState { it.copy(loadingWf = false, workflows = list) }
            } catch (e: Exception) {
                mutateState { it.copy(loadingWf = false, wfError = L10n.t("获取工作流失败：", "Failed to list workflows: ") + (e.message ?: "error")) }
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
            mutateState { it.copy(busy = L10n.t("正在加载工作流…", "Loading workflow…")) }
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
        val a = api ?: return
        if (g.running) return
        val ctx = getApplication<Application>()
        ensureNotificationChannels(ctx)
        ContextCompat.startForegroundService(ctx, Intent(ctx, GenService::class.java))
        mutateGen {
            it.copy(running = true, error = null, results = emptyList(), resultPaths = emptyList(),
                preview = null, status = L10n.t("排队中…", "Queued…"), progress = null)
        }
        updateRunningNotification(L10n.t("排队中…", "Queued…"), null)
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

                if (historyCancelled(entry)) {
                    mutateGen { it.copy(running = false, status = L10n.t("已取消", "Cancelled"), progress = null) }
                    return@launch
                }
                extractError(entry)?.let { err ->
                    mutateGen { it.copy(running = false, error = err, status = L10n.t("失败", "Failed")) }
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
                        status = if (results.isEmpty()) L10n.t("完成（没有输出图片）", "Done (no output images)") else L10n.t("完成", "Done"),
                        results = results, resultPaths = paths
                    )
                }
                notifyDone(g.wfName, results.size)
            } catch (e: Exception) {
                mutateGen { it.copy(running = false, error = e.message ?: e.toString(), status = L10n.t("失败", "Failed")) }
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
                if (q >= 0) mutateGen { it.copy(queueInfo = L10n.t("队列中任务: ", "Queue: ") + q) }
            }
            "execution_start" -> if (data?.optString("prompt_id") == pid) {
                mutateGen { it.copy(status = L10n.t("执行中…", "Running…"), progress = 0f) }
                updateRunningNotification(L10n.t("执行中…", "Running…"), 0f)
            }
            "executing" -> if (data?.optString("prompt_id") == pid) {
                val node = data.optString("node")
                if (node.isNotEmpty() && node != "null") {
                    val st = L10n.t("执行节点 ", "Node ") + "#" + node + " " + (_state.value.gen?.nodeTitles?.get(node) ?: "")
                    updateRunningNotification(st, null)
                    mutateGen { g -> g.copy(status = st, progress = null) }
                }
            }
            "progress" -> {
                val p = data?.optString("prompt_id")
                if (data != null && data.has("value") && (p.isNullOrEmpty() || p == pid)) {
                    val v = data.optDouble("value", 0.0)
                    val m = data.optDouble("max", 1.0).coerceAtLeast(1e-6)
                    val prog = (v / m).toFloat().coerceIn(0f, 1f)
                    updateRunningNotification(null, prog)
                    mutateGen { it.copy(progress = prog) }
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
        throw ComfyException(L10n.t("等待结果超时（30 分钟），结果可在服务器 output 目录查看", "Timed out after 30 minutes; check the server output folder"))
    }

    private fun extractError(entry: JSONObject): String? {
        val status = entry.optJSONObject("status") ?: return null
        if (status.optString("status_str") != "error") return null
        val sb = StringBuilder(L10n.t("执行出错", "Execution error"))
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
            mutateState { it.copy(busy = L10n.t("上传图片…", "Uploading…")) }
            try {
                val uploaded = uploadOne(a, uri, 0)
                mutateGen { g -> g.copy(values = g.values + (key to uploaded)) }
                mutateState { it.copy(busy = null) }
            } catch (e: Exception) {
                mutateState { it.copy(busy = null) }
                Toast.makeText(getApplication(), L10n.t("上传失败：", "Upload failed: ") + (e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Uploads several images and fills them into the IMAGE fields in order. */
    fun uploadImagesBatch(uris: List<Uri>) {
        val a = api ?: return
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
                Toast.makeText(getApplication(), L10n.t("上传失败：", "Upload failed: ") + (e.message ?: "error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadOne(a: ComfyApi, uri: Uri, seq: Int): String {
        val ctx = getApplication<Application>()
        val raw = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw ComfyException(L10n.t("读取图片失败", "Failed to read image"))
        val (bytes, ext) = withContext(Dispatchers.IO) { prepareForUpload(raw, ctx.contentResolver.getType(uri)) }
        return a.uploadImage(bytes, "mobile_${System.currentTimeMillis()}_$seq.$ext")
    }

    /** Downscales big camera/gallery photos to 1536px JPEG so uploads finish fast. */
    private fun prepareForUpload(bytes: ByteArray, mime: String?): Pair<ByteArray, String> {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return Pair(bytes, mime?.substringAfterLast('/', "png")?.takeIf { it.length in 2..5 } ?: "png")
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 1536) return Pair(bytes, mime?.substringAfterLast('/', "png")?.takeIf { it.length in 2..5 } ?: "png")
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = (maxDim / 1536).coerceAtLeast(1)
        }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return Pair(bytes, "jpg")
        val rotation = runCatching {
            when (android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val oriented = if (rotation != 0f) {
            val m = android.graphics.Matrix().apply { postRotate(rotation) }
            android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } else bmp
        val bos = java.io.ByteArrayOutputStream()
        oriented.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bos)
        return Pair(bos.toByteArray(), "jpg")
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
        Toast.makeText(getApplication(), L10n.t("AI 设置已保存", "AI settings saved"), Toast.LENGTH_SHORT).show()
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
        val base = prefs.getString("aiBase", "")?.trim().orEmpty()
        val key = prefs.getString("aiKey", "")?.trim().orEmpty()
        val model = prefs.getString("aiModel", "")?.trim().orEmpty()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            Toast.makeText(getApplication(), L10n.t("请先在连接页配置 AI 扩写设置", "Configure AI settings first (from the connect screen)"), Toast.LENGTH_LONG).show()
            return null
        }
        return Triple(base, key, model)
    }

    fun aiExpand(fieldKey: String, currentText: String, skill: AiSkillDef) {
        val cfg = aiConfigured() ?: return
        if (currentText.isBlank()) {
            Toast.makeText(getApplication(), L10n.t("先写几个字，AI 才知道往哪个方向扩写", "Type a few words first so the AI knows the direction"), Toast.LENGTH_SHORT).show()
            return
        }
        mutateState { it.copy(aiBusy = "$fieldKey|${skill.id}") }
        viewModelScope.launch {
            try {
                val out = AiClient.chat(cfg.first, cfg.second, cfg.third, skill.systemPrompt, currentText, aiTunables())
                mutateState { it.copy(aiBusy = null, aiResult = AiResult(fieldKey, out)) }
            } catch (e: Exception) {
                mutateState { it.copy(aiBusy = null) }
                Toast.makeText(getApplication(), (L10n.t("AI 调用失败：", "AI call failed: ") + (e.message ?: "error")).take(180), Toast.LENGTH_LONG).show()
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
        prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
        val cur = currentVersion()
        if (Updater.compareVersions(rel.tag, cur) > 0) {
            mutateState { it.copy(update = UpdateUi(release = rel)) }
        } else {
            mutateState { it.copy(update = UpdateUi(status = L10n.t("已是最新（v", "Up to date (v") + cur + L10n.t("）", ")"))) }
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
                mutateState { it.copy(update = UpdateUi(status = L10n.t("下载失败：", "Download failed: ") + (e.message ?: "error").take(200), isError = true)) }
            }
        }
    }

    // ---------- history ----------

    fun reopenFromHistory(item: HistoryItem) {
        val a = api ?: return
        if (item.wfPath.isEmpty()) {
            Toast.makeText(getApplication(), L10n.t("该记录没有关联工作流，无法重新生成", "No workflow linked to this entry"), Toast.LENGTH_SHORT).show()
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
                    ?: throw ComfyException(L10n.t("无法创建媒体文件", "Could not create media file"))
                ctx.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, L10n.t("已保存到 相册/ComfyRemote", "Saved to Pictures/ComfyRemote"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, L10n.t("保存失败：", "Save failed: ") + (e.message ?: "error"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openHistory() {
        mutateState { it.copy(screen = Screen.HISTORY) }
    }

    /** Receives an image shared from the system gallery/other apps. */
    fun handleSendIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        pendingShare = uri
        mutateState { it.copy(pendingShare = true) }
        tryConsumePendingShare()
    }

    /** Fills the first reference-image slot of the open workflow with the shared image. */
    fun tryConsumePendingShare() {
        val uri = pendingShare ?: return
        val g = _state.value.gen ?: return
        if (g.running) return
        if (g.fields.none { it.kind == FieldKind.IMAGE }) return
        pendingShare = null
        mutateState { it.copy(pendingShare = false) }
        uploadImagesBatch(listOf(uri))
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
            NotificationChannel(CHANNEL_RUNNING, L10n.t("生成状态", "Generation status"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, L10n.t("生成完成", "Generation finished"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun notifyDone(wfName: String, count: Int) {
        val ctx = getApplication<Application>()
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val pi = android.app.PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(ctx, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(L10n.t("生成完成", "Generation finished"))
            .setContentText("$wfName · " + L10n.t("${count} 张图", "${count} image(s)"))
            .setContentIntent(pi)
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
