package com.comfyremote.app.feature.generate


import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.comfyremote.app.FieldKind
import com.comfyremote.app.GenState
import com.comfyremote.app.MainViewModel
import com.comfyremote.app.UiState
import com.comfyremote.app.data.generation.ErrorKind
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.feature.history.decodeSampled
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: MainViewModel, s: UiState) {
    val g = s.gen ?: return
    val ctx = LocalContext.current
    val tasks by vm.tasks.collectAsState()
    val task = tasks.firstOrNull { it.taskId == g.activeTaskId }
    val running = task?.isActive == true
    val results = task?.results ?: emptyList()

    var pendingKey by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewingIdx by remember { mutableStateOf<Int?>(null) }
    var compareOpen by remember { mutableStateOf(false) }
    var libraryOpen by remember { mutableStateOf(false) }
    var selectedResult by rememberSaveable { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val k = pendingKey
        pendingKey = null
        if (uri != null && k != null) vm.uploadImage(k, uri)
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.uploadImagesBatch(uris.toList())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        val k = pendingKey
        cameraUri = null
        if (ok && uri != null && k != null) vm.uploadImage(k, uri)
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler { vm.back() }

    LaunchedEffect(s.pendingShare, g.fields) {
        if (s.pendingShare) vm.tryConsumePendingShare()
    }

    val imageFields = g.fields.filter { it.kind == FieldKind.IMAGE }
    val textFields = g.fields.filter { it.kind == FieldKind.TEXT }
    val seedFields = g.fields.filter { it.kind == FieldKind.SEED }
    val paramFields = g.fields.filter { it.kind == FieldKind.PARAM }
    var advOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g.wfName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                }
            )
        },
        bottomBar = {
            if (running) {
                OutlinedButton(
                    onClick = { vm.cancelGenerate() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text(L10n.t("取消生成", "Cancel")) }
            } else {
                Button(
                    onClick = { vm.generate() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text(L10n.t("生  成", "Generate"), fontSize = 16.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            task?.error?.let { err ->
                item(key = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                errorKindLabel(err.kind),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            err.nodeType?.let {
                                Text(
                                    L10n.t("节点：", "Node: ") + it,
                                    fontFamily = Mono, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(err.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item(key = "status") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                statusLabel(task).uppercase(),
                                fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp,
                                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            task?.nodeTitle?.takeIf { running && it.isNotBlank() }?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(0.dp))
                            } ?: Spacer(Modifier.weight(1f))
                            task?.queueRemaining?.let { q ->
                                Text(L10n.t("队列: ", "Queue: ") + q, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        task?.progress?.let { p ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth().height(3.dp).weight(1f))
                                Text("${(p * 100).toInt()}%", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (running && task?.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                    }
                }
            }
            if (g.warnings.isNotEmpty()) {
                item(key = "warn") {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            g.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (g.notes.isNotEmpty()) {
                item(key = "notes") {
                    Card {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { notesOpen = !notesOpen }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(L10n.t("工作流说明", "Workflow notes"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (notesOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (notesOpen) {
                                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                                    g.notes.forEach { note ->
                                        Text(
                                            stripMarkdown(note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            task?.preview?.let { pb ->
                item(key = "preview") {
                    Column {
                        Text(L10n.t("实时预览", "Live preview"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Image(pb.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
            // ---- PROMPT: visual center of the page ----
            item(key = "promptheader") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("提示词", "Prompt"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { libraryOpen = true }) { Text(L10n.t("提示词库", "Library"), style = MaterialTheme.typography.labelMedium) }
                }
            }
            itemsIndexed(textFields, key = { _, f -> f.key }) { _, f ->
                Column {
                    OutlinedTextField(
                        value = g.values[f.key] ?: f.value,
                        onValueChange = { vm.updateValue(f.key, it) },
                        label = { Text(f.label) },
                        minLines = if (f.multiline) 4 else 1,
                        maxLines = if (f.multiline) 14 else 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (f.multiline) {
                        val sorted = remember(g.wfName, s.skills) { com.comfyremote.app.AiSkillDef.recommendedOrder(s.skills, g.wfName) }
                        val primarySkills = sorted.take(2)
                        val restSkills = sorted.drop(2)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            primarySkills.forEach { skill ->
                                val busyKey = "${f.key}|${skill.id}"
                                TextButton(
                                    onClick = { vm.aiExpand(f.key, g.values[f.key] ?: f.value, skill) },
                                    enabled = s.aiBusy == null && !running
                                ) {
                                    if (s.aiBusy == busyKey) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(L10n.t("生成中…", "Working…"))
                                    } else {
                                        Text(skill.label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (restSkills.isNotEmpty()) {
                                var moreOpen by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { moreOpen = true }, enabled = s.aiBusy == null && !running) {
                                        Text(L10n.t("更多 ▾", "More ▾"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                        restSkills.forEach { skill ->
                                            DropdownMenuItem(
                                                text = { Text(skill.label) },
                                                onClick = { moreOpen = false; vm.aiExpand(f.key, g.values[f.key] ?: f.value, skill) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ---- reference image slots ----
            if (imageFields.isNotEmpty()) {
                item(key = "imgheader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("参考图片", "Reference images"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        if (imageFields.size >= 2) {
                            TextButton(onClick = { multiPicker.launch("image/*") }) { Text(L10n.t("一次选多张", "Pick multiple")) }
                        }
                    }
                }
                itemsIndexed(imageFields, key = { _, f -> f.key }) { _, f ->
                    val cur = g.values[f.key] ?: f.value
                    Column {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cur.isNotBlank()) {
                                Box {
                                    InputThumb(vm, cur, Modifier.size(92.dp).clickable {
                                        pendingKey = f.key; picker.launch("image/*")
                                    })
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.size(20.dp).align(Alignment.TopEnd).clickable { vm.updateValue(f.key, "") }
                                    ) {
                                        Text(
                                            "×", Modifier.padding(1.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 12.sp, textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    Modifier.size(92.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .clickable { pendingKey = f.key; picker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) { Text("+", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            OutlinedButton(onClick = {
                                pendingKey = f.key
                                val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                                val f2 = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                cameraUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f2)
                                camera.launch(cameraUri!!)
                            }, modifier = Modifier.size(92.dp)) { Text(L10n.t("拍照", "Cam"), fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (seedFields.isNotEmpty()) {
                item(key = "seeds") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g.randomSeed, onCheckedChange = { vm.toggleRandomSeed() })
                            Column {
                                Text(L10n.t("每次生成使用随机种子", "Randomize seed every run"))
                                if (g.randomSeed) Text(
                                    L10n.t("生成后自动回填上次种子，关闭即可复现", "Last seed is filled back in after each run; untick to reproduce"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        seedFields.forEach { f ->
                            OutlinedTextField(
                                value = g.values[f.key] ?: f.value,
                                onValueChange = { vm.updateValue(f.key, it.filter { c -> c.isDigit() }) },
                                label = { Text(f.label + (if (g.randomSeed) L10n.t("（本次将随机）", " (randomized)") else "")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            if (paramFields.isNotEmpty()) {
                item(key = "params") {
                    Card {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { advOpen = !advOpen }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(L10n.t("高级参数（", "Advanced (") + "${paramFields.size}" + L10n.t("）", ")"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (advOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (advOpen) {
                                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    paramFields.forEach { f -> ParamField(f, g.values[f.key] ?: f.value) { vm.updateValue(f.key, it) } }
                                }
                            }
                        }
                    }
                }
            }
            // ---- results: image first ----
            if (results.isNotEmpty()) {
                item(key = "resheader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.t("生成结果（", "Results (") + "${results.size}" + L10n.t("）", ")"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        if (results.size >= 2) {
                            TextButton(onClick = { compareOpen = true }) { Text(L10n.t("对比", "Compare")) }
                        }
                    }
                }
                item(key = "resbig") {
                    val idx = selectedResult.coerceIn(results.indices)
                    val res = results[idx]
                    val bmp by produceState<Bitmap?>(null, res.localPath) {
                        value = withContext(Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
                    }
                    bmp?.let {
                        Image(
                            it.asImageBitmap(), null,
                            Modifier.fillMaxWidth().clickable { viewingIdx = idx },
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "${idx + 1} / ${results.size}",
                            fontFamily = Mono, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                item(key = "resthumbs") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(results, key = { i, r -> "th$i-${r.localPath}" }) { idx, res ->
                            val bmp by produceState<Bitmap?>(null, res.localPath) {
                                value = withContext(Dispatchers.IO) { decodeSampled(res.localPath, 200) }
                            }
                            bmp?.let {
                                Image(
                                    it.asImageBitmap(), null,
                                    Modifier
                                        .size(72.dp)
                                        .then(
                                            if (idx == selectedResult) Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                            else Modifier
                                        )
                                        .clickable { selectedResult = idx },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                item(key = "resmeta") {
                    Column {
                        task?.seedInfo?.takeIf { it.isNotBlank() }?.let {
                            Text("seed: $it", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = "resactions") {
                    val idx = selectedResult.coerceIn(results.indices)
                    val res = results[idx]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { shareImage(ctx, res.localPath) }) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share"))
                        }
                        val bmp by produceState<Bitmap?>(null, res.localPath) {
                            value = withContext(Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
                        }
                        TextButton(onClick = { bmp?.let { vm.saveResult(it) } }) { Text(L10n.t("保存到相册", "Save to gallery")) }
                        if (!running) {
                            TextButton(onClick = { vm.generate() }) { Text(L10n.t("再生成", "Re-run")) }
                        }
                    }
                }
            }
        }
    }
    viewingIdx?.let { idx ->
        results.getOrNull(idx)?.let { res ->
            val bmp by produceState<Bitmap?>(null, res.localPath) {
                value = withContext(Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
            }
            bmp?.let {
                ImageViewerDialog(
                    bitmap = it,
                    onClose = { viewingIdx = null },
                    onSave = { vm.saveResult(it) },
                    onShare = { shareImage(ctx, res.localPath) }
                )
            }
        }
    }
    s.aiResult?.let { result ->
        AiResultDialog(
            initial = result.text,
            onDismiss = { vm.aiDismiss() },
            onReplace = { vm.aiApply(replace = true, it) },
            onAppend = { vm.aiApply(replace = false, it) }
        )
    }
    if (compareOpen && results.size >= 2) {
        CompareDialog(results, onDismiss = { compareOpen = false })
    }
    if (libraryOpen) {
        val sortedPresets = remember(s.bookmarks, s.currentWfPath) {
            s.bookmarks.sortedByDescending { it.workflowPath == null || it.workflowPath == s.currentWfPath }
        }
        ModalBottomSheet(onDismissRequest = { libraryOpen = false }) {
            BookmarksSheetContent(
                bookmarks = sortedPresets,
                onApply = { vm.applyBookmark(it); vm.aiDismiss(); libraryOpen = false },
                onDelete = { vm.deleteBookmark(it) },
                onSaveCurrent = { vm.addBookmarkFromCurrent() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputThumb(vm: MainViewModel, name: String, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, name) {
        value = vm.loadInputBitmap(name)
    }
    if (bmp != null) {
        Image(bmp!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("?", fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
