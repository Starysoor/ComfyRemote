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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.comfyremote.app.FieldKind
import com.comfyremote.app.GenState
import com.comfyremote.app.MainViewModel
import com.comfyremote.app.UiState
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.feature.history.decodeSampled
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: MainViewModel, s: UiState) {
    val g = s.gen ?: return
    val ctx = LocalContext.current
    val tasks by vm.tasks.collectAsState()
    val task = tasks.firstOrNull { it.taskId == g.activeTaskId }
    val running = task?.isActive == true

    var pendingKey by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewingIdx by remember { mutableStateOf<Int?>(null) }
    var compareOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }

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
    val results = task?.results ?: emptyList()
    var advOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g.wfName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    TextButton(onClick = { bookmarksOpen = true }) {
                        Text(L10n.t("书签", "Bookmarks"), style = MaterialTheme.typography.labelLarge)
                    }
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
                        Column(Modifier.padding(12.dp)) {
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
                            Spacer(Modifier.weight(1f))
                            task?.queueRemaining?.let { q ->
                                Text(L10n.t("队列: ", "Queue: ") + q, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        task?.progress?.let { p -> LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth().height(3.dp)) }
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
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(f.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val cur = g.values[f.key] ?: f.value
                            Text(L10n.t("当前：", "Current: ") + cur, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { pendingKey = f.key; picker.launch("image/*") }) { Text(L10n.t("选择图片", "Pick image")) }
                                OutlinedButton(onClick = {
                                    pendingKey = f.key
                                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                                    val f2 = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                    cameraUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f2)
                                    camera.launch(cameraUri!!)
                                }) { Text(L10n.t("拍照", "Camera")) }
                            }
                        }
                    }
                }
            }
            itemsIndexed(textFields, key = { _, f -> f.key }) { _, f ->
                Column {
                    OutlinedTextField(
                        value = g.values[f.key] ?: f.value,
                        onValueChange = { vm.updateValue(f.key, it) },
                        label = { Text(f.label) },
                        minLines = if (f.multiline) 3 else 1,
                        maxLines = if (f.multiline) 12 else 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (f.multiline) {
                        val skills = remember(g.wfName, s.skills) { com.comfyremote.app.AiSkillDef.recommendedOrder(s.skills, g.wfName) }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            skills.forEach { skill ->
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
                itemsIndexed(results, key = { i, _ -> "res$i" }) { idx, res ->
                    val bmp by produceState<Bitmap?>(null, res.localPath) {
                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
                    }
                    bmp?.let {
                        Column {
                            Image(
                                it.asImageBitmap(), null,
                                Modifier.fillMaxWidth().clickable { viewingIdx = idx },
                                contentScale = ContentScale.FillWidth
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L10n.t("结果 ", "Result ") + "${idx + 1}" + L10n.t("（点击放大）", " (tap to zoom)"), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { shareImage(ctx, res.localPath) }) {
                                    Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share"))
                                }
                                TextButton(onClick = { it.let { b -> vm.saveResult(b) } }) { Text(L10n.t("保存到相册", "Save to gallery")) }
                            }
                        }
                    }
                }
            }
        }
    }
    viewingIdx?.let { idx ->
        results.getOrNull(idx)?.let { res ->
            val bmp by produceState<Bitmap?>(null, res.localPath) {
                value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
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
    if (bookmarksOpen) {
        BookmarksDialog(
            bookmarks = s.bookmarks,
            onDismiss = { bookmarksOpen = false },
            onApply = { vm.applyBookmark(it); bookmarksOpen = false },
            onDelete = { vm.deleteBookmark(it) },
            onSaveCurrent = { vm.addBookmarkFromCurrent() }
        )
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
