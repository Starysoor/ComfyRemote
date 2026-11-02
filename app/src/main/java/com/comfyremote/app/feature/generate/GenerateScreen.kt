package com.comfyremote.app.feature.generate

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.FieldKind
import com.comfyremote.app.data.generation.ErrorKind
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.data.repository.WorkflowInsight
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.LocalAppSettings
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.ErrorCard
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    viewModel: GenerateViewModel,
    workflowPath: String,
    workflowName: String,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenCloudVideo: (String, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val appSettings = LocalAppSettings.current
    val draft = state.draft
    val task = tasks.firstOrNull { it.taskId == state.activeTaskId }
    val running = task?.isActive == true
    val results = task?.results.orEmpty()
    val visibleResults = results.takeLast(2)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(workflowPath, workflowName) { viewModel.loadWorkflow(workflowPath, workflowName) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var pendingField by remember { mutableStateOf<String?>(null) }
    var pendingAudioField by remember { mutableStateOf<String?>(null) }
    var audioPreview by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingField
        pendingField = null
        if (uri != null && key != null) viewModel.uploadImage(key, uri)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingAudioField
        pendingAudioField = null
        if (uri != null && key != null) viewModel.uploadAudio(key, uri)
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadImagesBatch(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val key = pendingField
        val uri = cameraUri
        pendingField = null
        cameraUri = null
        if (ok && key != null && uri != null) viewModel.uploadImage(key, uri)
    }

    var advancedOpen by rememberSaveable { mutableStateOf(appSettings.advancedExpandedByDefault) }
    var notesOpen by rememberSaveable { mutableStateOf(appSettings.notesExpandedByDefault) }
    var libraryOpen by remember { mutableStateOf(false) }
    var compareOpen by remember { mutableStateOf(false) }
    var viewingResult by remember { mutableStateOf<Int?>(null) }
    var selectedResult by rememberSaveable { mutableStateOf(0) }
    var visionTarget by remember { mutableStateOf<VisionRequest?>(null) }
    var confirmCloudCancel by remember { mutableStateOf(false) }
    val primaryTextKey = draft?.fields
        ?.firstOrNull { it.kind == FieldKind.TEXT && it.multiline && !it.looksLikeNegativePrompt() }
        ?.key
        ?: draft?.fields?.firstOrNull { it.kind == FieldKind.TEXT }?.key

    LaunchedEffect(task?.taskId) {
        selectedResult = 0
        viewingResult = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(draft?.workflowName ?: workflowName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (draft?.isCloud == true) {
                            Text(
                                "AUTODL · CLOUD",
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    if (draft != null) {
                        TextButton(onClick = viewModel::resetRememberedDraft, enabled = !running) {
                            Text(L10n.t("默认值", "Defaults"))
                        }
                    }
                    task?.let { t -> TextButton(onClick = { onOpenTask(t.taskId) }) { Text(L10n.t("任务", "Task")) } }
                }
            )
        },
        bottomBar = {
            if (draft != null) {
                Surface(tonalElevation = 3.dp) {
                    if (running) {
                        OutlinedButton(
                            onClick = {
                                if (draft.isCloud) confirmCloudCancel = true else viewModel.cancelActiveTask()
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                        ) { Text(L10n.t("取消生成", "Cancel generation")) }
                    } else {
                        Button(
                            onClick = viewModel::generate,
                            enabled = state.insight?.compatible != false,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                        ) {
                            val suffix = if (state.batchCount > 1) " ×${state.batchCount}" else ""
                            Text(L10n.t("生  成", "GENERATE") + suffix, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }
    ) { pad ->
        when {
            state.loading -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ErrorCard(L10n.t("工作流加载失败", "Failed to load workflow"), state.error)
            }
            draft == null -> Unit
            else -> {
                val imageFields = draft.fields.filter { it.kind == FieldKind.IMAGE }
                val audioFields = draft.fields.filter { it.kind == FieldKind.AUDIO }
                val textFields = draft.fields.filter { it.kind == FieldKind.TEXT }
                val seedFields = draft.fields.filter { it.kind == FieldKind.SEED }
                val paramFields = draft.fields.filter { it.kind == FieldKind.PARAM }

                LazyColumn(
                    modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    task?.let { item("status") { GenerationStatusCard(task, running) } }
                    state.insight?.let { insight ->
                        item("workflow-insight") { WorkflowInsightCard(insight) }
                    }
                    task?.error?.let { error ->
                        item("error") {
                            ErrorCard(
                                errorKindLabel(error.kind),
                                buildString {
                                    error.nodeType?.let { append("$it · ") }
                                    error.nodeId?.let { append("#$it · ") }
                                    append(error.message)
                                }
                            )
                        }
                    }
                    if (draft.warnings.isNotEmpty()) item("warnings") {
                        Card { Column(Modifier.padding(12.dp)) { draft.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                    }
                    if (draft.notes.isNotEmpty()) item("notes") {
                        Card {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { notesOpen = !notesOpen }.padding(12.dp)) {
                                    Text(L10n.t("工作流说明", "Workflow notes"), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (notesOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), color = MaterialTheme.colorScheme.primary)
                                }
                                if (notesOpen) Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                                    draft.notes.forEach { Text(stripMarkdown(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    task?.preview?.let { preview -> item("preview") {
                        Column {
                            Text(L10n.t("实时预览", "Live preview"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Image(preview.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        }
                    } }

                    item("prompt-header") {
                        SectionHeader(L10n.t("提示词", "Prompt")) {
                            TextButton(onClick = { libraryOpen = true }) { Text(L10n.t("预设库", "Presets")) }
                        }
                    }
                    itemsIndexed(textFields, key = { _, f -> f.key }) { _, field ->
                        Column {
                            OutlinedTextField(
                                value = draft.values[field.key] ?: field.value,
                                onValueChange = { viewModel.updateValue(field.key, it) },
                                label = { Text(field.label) },
                                minLines = if (field.multiline) 4 else 1,
                                maxLines = if (field.multiline) 14 else 2,
                                modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                                    if (field.multiline && st.isFocused && draft.values[field.key] == null && field.value.isNotBlank()) {
                                        viewModel.updateValue(field.key, "")
                                    }
                                },
                            )
                            if (field.multiline) {
                                val ordered = remember(draft.workflowName, state.skills) {
                                    AiSkillDef.recommendedOrder(
                                        state.skills.filter { it.id != AiSkillDef.DEFAULT_VISION_ID },
                                        draft.workflowName,
                                    )
                                }
                                val primary = ordered.take(2)
                                val rest = ordered.drop(2)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    primary.forEach { skill ->
                                        val busyKey = "${field.key}|${skill.id}"
                                        TextButton(
                                            onClick = { viewModel.aiExpand(field.key, draft.values[field.key] ?: field.value, skill) },
                                            enabled = state.aiBusy == null && !running,
                                        ) {
                                            if (state.aiBusy == busyKey) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                            else Text(skill.label)
                                        }
                                    }
                                    if (rest.isNotEmpty()) {
                                        var more by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { more = true }, enabled = state.aiBusy == null && !running) { Text(L10n.t("更多 ▾", "More ▾")) }
                                            DropdownMenu(more, { more = false }) {
                                                rest.forEach { skill ->
                                                    DropdownMenuItem(
                                                        text = { Text(skill.label) },
                                                        onClick = { more = false; viewModel.aiExpand(field.key, draft.values[field.key] ?: field.value, skill) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (imageFields.isNotEmpty()) {
                        item("image-header") {
                            SectionHeader(L10n.t("参考图片", "Reference images")) {
                                if (imageFields.size > 1) TextButton(onClick = { multiPicker.launch("image/*") }) { Text(L10n.t("多选", "Multi-pick")) }
                            }
                        }
                        itemsIndexed(imageFields, key = { _, f -> f.key }) { _, field ->
                            val current = draft.values[field.key] ?: field.value
                            val upload = state.uploads[field.key]
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ImageSlot(
                                        viewModel = viewModel,
                                        name = current,
                                        upload = upload,
                                        onPick = { pendingField = field.key; picker.launch("image/*") },
                                        onClear = { viewModel.updateValue(field.key, "") },
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            pendingField = field.key
                                            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                            val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                            cameraUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                            camera.launch(cameraUri!!)
                                        },
                                        modifier = Modifier.size(92.dp),
                                    ) { Text(L10n.t("拍照", "Cam"), fontSize = 11.sp) }
                                }
                                if (upload?.phase == ImageUploadPhase.FAILED) Text(upload.message ?: L10n.t("上传失败", "Upload failed"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                if (current.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            visionTarget = VisionRequest(primaryTextKey, current, field.label)
                                        },
                                        enabled = state.aiBusy == null && !running,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            if (state.aiBusy?.startsWith("vision:") == true) {
                                                L10n.t("看图改写中…", "Looking…")
                                            } else {
                                                L10n.t("AI 看图改写", "AI vision rewrite")
                                            },
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (audioFields.isNotEmpty()) {
                        item("audio-header") { SectionHeader(L10n.t("音频参考", "Audio references")) }
                        itemsIndexed(audioFields, key = { _, f -> f.key }) { _, field ->
                            val current = draft.values[field.key] ?: field.value
                            val upload = state.uploads[field.key]
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (current.isNotBlank()) {
                                        MediaThumbnail(
                                            path = current.removePrefix("local:"),
                                            size = 44.dp,
                                            modifier = Modifier.clickable { audioPreview = current.removePrefix("local:") },
                                        )
                                        Text(
                                            audioDisplayName(current),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { viewModel.updateValue(field.key, "") }) {
                                            Text(L10n.t("清除", "Clear"))
                                        }
                                    } else {
                                        Text(
                                            L10n.t("未选择音频", "No audio selected"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pendingAudioField = field.key
                                            audioPicker.launch("audio/*")
                                        },
                                        enabled = !running,
                                    ) { Text(L10n.t("选择音频", "Pick audio")) }
                                }
                                if (upload?.phase == ImageUploadPhase.FAILED) {
                                    Text(
                                        upload.message ?: L10n.t("音频读取失败", "Could not read audio"),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    if (seedFields.isNotEmpty()) item("seeds") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(draft.randomSeed, { viewModel.toggleRandomSeed() })
                                Text(L10n.t("随机种子", "Random seed"))
                            }
                            if (!draft.randomSeed) seedFields.forEach { field ->
                                OutlinedTextField(
                                    draft.values[field.key] ?: field.value,
                                    { viewModel.updateValue(field.key, it.filter(Char::isDigit)) },
                                    label = { Text(field.label) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    item("batch") {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(L10n.t("批量", "Batch"), fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { viewModel.setBatchCount(state.batchCount - 1) },
                                        enabled = state.batchCount > 1 && !running,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) { Text("−") }
                                    Text("${state.batchCount}", fontFamily = Mono, fontWeight = FontWeight.Bold)
                                    TextButton(
                                        onClick = { viewModel.setBatchCount(state.batchCount + 1) },
                                        enabled = state.batchCount < 16 && !running,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) { Text("+") }
                                    Checkbox(state.batchLocalQueue, { viewModel.toggleBatchLocalQueue() }, enabled = state.batchCount > 1 && !running)
                                    Text(L10n.t("本地队列", "Local queue"), fontSize = 12.sp)
                                }
                                if (state.batchCount > 1 && !draft.randomSeed) {
                                    Text(
                                        L10n.t("固定 Seed 会按 +0、+1、+2… 生成变体", "Fixed seed increments +0, +1, +2…"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (paramFields.isNotEmpty()) item("advanced") {
                        Card {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { advancedOpen = !advancedOpen }.padding(12.dp)) {
                                    Text(L10n.t("高级参数（", "Advanced (") + paramFields.size + L10n.t("）", ")"), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (advancedOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), color = MaterialTheme.colorScheme.primary)
                                }
                                if (advancedOpen) Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    paramFields.forEach { field -> ParamField(field, draft.values[field.key] ?: field.value) { viewModel.updateValue(field.key, it) } }
                                }
                            }
                        }
                    }

                    if (visibleResults.isNotEmpty()) {
                        item("result-header") {
                            SectionHeader(L10n.t("生成结果", "Results")) {
                                if (results.size > 1 && results.none { isNonImageMedia(it.localPath) }) {
                                    TextButton(onClick = { compareOpen = true }) { Text(L10n.t("对比", "Compare")) }
                                }
                            }
                        }
                        item("result-limit-note") {
                            if (results.size > visibleResults.size) Text(
                                L10n.t("仅显示最近 2 张，全部见历史页", "Showing latest 2 of ${results.size} - see History"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item("result-main") {
                            val index = selectedResult.coerceIn(visibleResults.indices)
                            val result = visibleResults[index]
                            val globalIndex = results.size - visibleResults.size + index
                            if (isNonImageMedia(result.localPath)) {
                                MediaThumbnail(
                                    path = result.localPath,
                                    size = 220.dp,
                                    modifier = Modifier.fillMaxWidth().clickable { viewingResult = index },
                                )
                            } else {
                                val bitmap by produceState<Bitmap?>(null, result.localPath) {
                                    value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) }
                                }
                                bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clickable { viewingResult = index }, contentScale = ContentScale.FillWidth) }
                            }
                            Text("${globalIndex + 1} / ${results.size}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (visibleResults.size > 1) item("result-thumbs") {
                            val selectedIndex = selectedResult.coerceIn(visibleResults.indices)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(visibleResults, key = { i, r -> "$i:${r.localPath}" }) { index, result ->
                                    MediaThumbnail(
                                        path = result.localPath,
                                        size = 72.dp,
                                        modifier = Modifier
                                            .then(
                                                if (index == selectedIndex) {
                                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                                } else Modifier
                                            )
                                            .clickable { selectedResult = index },
                                    )
                                }
                            }
                        }
                        item("result-meta") {
                            task?.seedInfo?.takeIf(String::isNotBlank)?.let { Text("seed: $it", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        item("result-actions") {
                            val index = selectedResult.coerceIn(visibleResults.indices)
                            val result = visibleResults[index]
                            val visual = !isNonImageMedia(result.localPath)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { shareMedia(context, result.localPath) }) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share")) }
                                TextButton(onClick = { viewModel.saveMedia(result.localPath) }) { Text(L10n.t("保存", "Save")) }
                                TextButton(
                                    onClick = {
                                        visionTarget = VisionRequest(primaryTextKey, result.localPath, L10n.t("生成结果", "Result"))
                                    },
                                    enabled = visual && state.aiBusy == null,
                                ) { Text(L10n.t("AI 看图改写", "Vision rewrite")) }
                                if (visual) {
                                    TextButton(onClick = {
                                        viewModel.prepareCloudVideo(result.localPath)?.let { (path, name) ->
                                            onOpenCloudVideo(path, name)
                                        }
                                    }) { Text(L10n.t("生成视频", "Make video")) }
                                }
                                if (visual && imageFields.isNotEmpty()) TextButton(onClick = { viewModel.useLocalResultAsReference(result.localPath) }) { Text(L10n.t("作为参考图", "Use as reference")) }
                                if (!running) TextButton(onClick = { viewModel.generate() }) { Text(L10n.t("再生成", "Re-run")) }
                            }
                        }
                    }
                }
            }
        }
    }

    state.aiResult?.let { result ->
        AiResultDialog(
            initial = result.text,
            onDismiss = viewModel::dismissAiResult,
            onReplace = { viewModel.applyAi(true, it) },
            onAppend = { viewModel.applyAi(false, it) },
        )
    }
    if (libraryOpen) ModalBottomSheet(onDismissRequest = { libraryOpen = false }) {
        PromptLibrarySheetContent(
            presets = state.filteredPresets,
            query = state.presetQuery,
            onQuery = viewModel::setPresetQuery,
            onApply = { preset, mode -> viewModel.applyPreset(preset, mode); libraryOpen = false },
            onDelete = viewModel::deletePreset,
            onToggleFavorite = viewModel::togglePresetFavorite,
            onUpdateMeta = viewModel::updatePresetMeta,
            onSaveCurrent = viewModel::saveCurrentPreset,
            onSaveWorkflow = viewModel::saveCurrentWorkflowPreset,
            onExport = viewModel::exportPresets,
            onImport = viewModel::importPresets,
        )
    }
    if (compareOpen) CompareDialog(results) { compareOpen = false }
    audioPreview?.let { path ->
        MediaViewerDialog(
            path = path,
            onClose = { audioPreview = null },
            onShare = { shareMedia(context, path) },
        )
    }
    viewingResult?.let { index ->
        visibleResults.getOrNull(index)?.let { result ->
            MediaViewerDialog(
                path = result.localPath,
                onClose = { viewingResult = null },
                onSave = { viewModel.saveMedia(result.localPath) },
                onShare = { shareMedia(context, result.localPath) },
            )
        }
    }
    visionTarget?.let { target ->
        VisionInstructionDialog(
            targetLabel = target.label,
            onDismiss = { visionTarget = null },
            onRun = { instruction ->
                viewModel.visionRewrite(target.fieldKey, target.imageValue, instruction)
                visionTarget = null
            },
        )
    }
    state.cloudConfirm?.let { confirmation ->
        CloudConfirmDialog(
            confirmation = confirmation,
            onConfirm = viewModel::confirmCloudSubmit,
            onDismiss = viewModel::dismissCloudConfirm,
        )
    }
    if (confirmCloudCancel) {
        AlertDialog(
            onDismissRequest = { confirmCloudCancel = false },
            title = { Text(L10n.t("取消云端任务？", "Cancel cloud task?")) },
            text = {
                Text(
                    L10n.t(
                        "这里只能停止手机端跟踪；AutoDL 平台侧任务可能继续执行并产生费用。",
                        "This only stops local tracking. The AutoDL job may keep running and billing.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloudCancel = false
                    viewModel.cancelActiveTask()
                }) { Text(L10n.t("仍然取消", "Cancel anyway"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloudCancel = false }) { Text(L10n.t("继续等待", "Keep waiting")) }
            },
        )
    }
}

@Composable
private fun WorkflowInsightCard(insight: WorkflowInsight) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (insight.compatible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.t("工作流识别", "Workflow"), fontWeight = FontWeight.SemiBold)
                insight.capabilities.forEach { StatusChip(it.label) }
            }
            if (insight.issues.isEmpty()) {
                Text(L10n.t("未发现明显兼容性问题", "No obvious compatibility issues"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                insight.issues.take(4).forEach { issue ->
                    Text(
                        (if (issue.blocking) "⚠ " else "• ") + issue.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (issue.blocking) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerationStatusCard(task: GenerationTask?, running: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusLabel(task).uppercase(), fontFamily = Mono, fontSize = 11.sp, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                if (task?.recovered == true) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip(L10n.t("已恢复", "RECOVERED"))
                }
                task?.batchIndex?.let { index ->
                    Spacer(Modifier.width(6.dp))
                    StatusChip("$index/${task.batchSize ?: index}")
                }
                task?.nodeTitle?.takeIf(String::isNotBlank)?.let { Spacer(Modifier.width(8.dp)); Text(it, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) } ?: Spacer(Modifier.weight(1f))
                task?.queuePosition?.let { Text("#${it}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
                    ?: task?.queueRemaining?.let { Text(L10n.t("队列 ", "Queue ") + it, fontFamily = Mono, fontSize = 10.sp) }
            }
            task?.recoveryNote?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task?.phase == GenerationPhase.RUNNING) {
                val p = task.progress?.coerceIn(0f, 1f)
                if (p == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.weight(1f))
                        Text("${(p * 100).toInt()}%", fontFamily = Mono, fontSize = 10.sp)
                    }
                }
            } else if (task?.localPending == true) {
                Text(L10n.t("等待 App 本地队列放行，不会提前塞入 ComfyUI 队列", "Waiting locally; not submitted to ComfyUI yet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImageSlot(
    viewModel: GenerateViewModel,
    name: String,
    upload: ImageUploadStatus?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        Modifier.size(92.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (name.isNotBlank()) {
            val bitmap by produceState<Bitmap?>(null, name) { value = viewModel.loadInputBitmap(name) }
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("?", fontFamily = Mono)
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clickable(onClick = onClear),
            ) { Text("×", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onErrorContainer) }
        } else Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (upload?.phase == ImageUploadPhase.UPLOADING) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
            }
        }
    }
}

private fun audioDisplayName(value: String): String =
    value.substringAfterLast('/').substringAfterLast(':').ifBlank { value }

private fun statusLabel(task: GenerationTask?): String = when {
    task?.localPending == true -> L10n.t("本地等待", "Local queue")
    else -> when (task?.phase) {
        null -> L10n.t("待生成", "Ready")
        GenerationPhase.PREPARING -> L10n.t("准备中", "Preparing")
        GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> L10n.t("排队中", "Queued")
        GenerationPhase.RUNNING -> L10n.t("生成中", "Running")
        GenerationPhase.CANCELLING -> L10n.t("取消中", "Cancelling")
        GenerationPhase.CANCELLED -> L10n.t("已取消", "Cancelled")
        GenerationPhase.SUCCEEDED -> L10n.t("完成", "Done")
        GenerationPhase.FAILED -> L10n.t("失败", "Failed")
    }
}

fun errorKindLabel(kind: ErrorKind): String = when (kind) {
    ErrorKind.EXECUTION -> L10n.t("执行错误", "Execution error")
    ErrorKind.CONNECTION -> L10n.t("连接错误", "Connection error")
    ErrorKind.PROMPT_VALIDATION -> L10n.t("参数校验失败", "Validation failed")
    ErrorKind.TIMEOUT -> L10n.t("等待超时", "Timed out")
    ErrorKind.DOWNLOAD -> L10n.t("下载失败", "Download failed")
    ErrorKind.CANCELLED -> L10n.t("已取消", "Cancelled")
    ErrorKind.UNKNOWN -> L10n.t("未知错误", "Unknown error")
}
