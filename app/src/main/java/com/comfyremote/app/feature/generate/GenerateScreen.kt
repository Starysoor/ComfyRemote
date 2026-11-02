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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
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
import com.comfyremote.app.ui.L10n
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
) {
    val state by viewModel.state.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val draft = state.draft
    val task = tasks.firstOrNull { it.taskId == state.activeTaskId }
    val running = task?.isActive == true
    val results = task?.results.orEmpty()
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
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingField
        pendingField = null
        if (uri != null && key != null) viewModel.uploadImage(key, uri)
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

    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var libraryOpen by remember { mutableStateOf(false) }
    var compareOpen by remember { mutableStateOf(false) }
    var viewingResult by remember { mutableStateOf<Int?>(null) }
    var selectedResult by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(draft?.workflowName ?: workflowName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    task?.let { t -> TextButton(onClick = { onOpenTask(t.taskId) }) { Text(L10n.t("任务", "Task")) } }
                }
            )
        },
        bottomBar = {
            if (draft != null) {
                Surface(tonalElevation = 3.dp) {
                    if (running) {
                        OutlinedButton(
                            onClick = viewModel::cancelActiveTask,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                        ) { Text(L10n.t("取消生成", "Cancel generation")) }
                    } else {
                        Button(
                            onClick = viewModel::generate,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                        ) { Text(L10n.t("生  成", "GENERATE"), fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp) }
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
                val textFields = draft.fields.filter { it.kind == FieldKind.TEXT }
                val seedFields = draft.fields.filter { it.kind == FieldKind.SEED }
                val paramFields = draft.fields.filter { it.kind == FieldKind.PARAM }

                LazyColumn(
                    modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item("status") { GenerationStatusCard(task, running) }
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
                                    if (st.isFocused && draft.values[field.key] == null && field.value.isNotBlank()) {
                                        viewModel.updateValue(field.key, "")
                                    }
                                },
                            )
                            if (field.multiline) {
                                val ordered = remember(draft.workflowName, state.skills) {
                                    AiSkillDef.recommendedOrder(state.skills, draft.workflowName)
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

                    if (results.isNotEmpty()) {
                        item("result-header") {
                            SectionHeader(L10n.t("生成结果", "Results")) {
                                if (results.size > 1) TextButton(onClick = { compareOpen = true }) { Text(L10n.t("对比", "Compare")) }
                            }
                        }
                        item("result-limit-note") {
                            if (results.size > 2) Text(
                                L10n.t("仅显示最近 2 张，全部见历史页", "Showing latest 2 of " + "${results.size}" + " - see History"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item("result-main") {
                            val recent = results.takeLast(2)
                            val index = selectedResult.coerceIn(recent.indices)
                            val result = recent[index]
                            val bitmap by produceState<Bitmap?>(null, result.localPath) {
                                value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) }
                            }
                            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clickable { viewingResult = index }, contentScale = ContentScale.FillWidth) }
                            Text("${index + 1} / ${results.size}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (results.size > 1) item("result-thumbs") {
                            val recentThumbs = results.takeLast(2)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(recentThumbs, key = { i, r -> "$i:${r.localPath}" }) { index, result ->
                                    val bitmap by produceState<Bitmap?>(null, result.localPath) {
                                        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 180) }
                                    }
                                    bitmap?.let {
                                        Image(
                                            it.asImageBitmap(), null,
                                            Modifier.size(72.dp).then(if (index == selectedResult) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier).clickable { selectedResult = index },
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }
                        item("result-meta") {
                            task?.seedInfo?.takeIf(String::isNotBlank)?.let { Text("seed: $it", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        item("result-actions") {
                            val index = selectedResult.coerceIn(results.indices)
                            val result = results[index]
                            val bitmap by produceState<Bitmap?>(null, result.localPath) { value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { shareImage(context, result.localPath) }) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share")) }
                                TextButton(onClick = { bitmap?.let(viewModel::saveResult) }) { Text(L10n.t("保存", "Save")) }
                                if (!running) TextButton(onClick = viewModel::generate) { Text(L10n.t("再生成", "Re-run")) }
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
            onApply = { viewModel.applyPreset(it); libraryOpen = false },
            onDelete = viewModel::deletePreset,
            onToggleFavorite = viewModel::togglePresetFavorite,
            onSaveCurrent = viewModel::saveCurrentPreset,
            onSaveWorkflow = viewModel::saveCurrentWorkflowPreset,
        )
    }
    if (compareOpen) CompareDialog(results) { compareOpen = false }
    viewingResult?.let { index ->
        results.getOrNull(index)?.let { result ->
            val bitmap by produceState<Bitmap?>(null, result.localPath) { value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1800) } }
            bitmap?.let {
                ImageViewerDialog(
                    bitmap = it,
                    onClose = { viewingResult = null },
                    onSave = { viewModel.saveResult(it) },
                    onShare = { shareImage(context, result.localPath) },
                )
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
                task?.nodeTitle?.takeIf(String::isNotBlank)?.let { Spacer(Modifier.width(8.dp)); Text(it, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) } ?: Spacer(Modifier.weight(1f))
                task?.queuePosition?.let { Text("#${it}", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
                    ?: task?.queueRemaining?.let { Text(L10n.t("队列 ", "Queue ") + it, fontFamily = Mono, fontSize = 10.sp) }
            }
            task?.recoveryNote?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task?.progress?.let { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.weight(1f))
                    Text("${(p * 100).toInt()}%", fontFamily = Mono, fontSize = 10.sp)
                }
            }
            if (running && task?.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
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

private fun statusLabel(task: GenerationTask?): String = when (task?.phase) {
    null -> L10n.t("待生成", "Ready")
    GenerationPhase.PREPARING -> L10n.t("准备中", "Preparing")
    GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> L10n.t("排队中", "Queued")
    GenerationPhase.RUNNING -> L10n.t("生成中", "Running")
    GenerationPhase.CANCELLING -> L10n.t("取消中", "Cancelling")
    GenerationPhase.CANCELLED -> L10n.t("已取消", "Cancelled")
    GenerationPhase.SUCCEEDED -> L10n.t("完成", "Done")
    GenerationPhase.FAILED -> L10n.t("失败", "Failed")
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
