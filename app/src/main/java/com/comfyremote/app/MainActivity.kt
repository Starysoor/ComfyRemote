package com.comfyremote.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when (s.screen) {
            Screen.CONNECT -> ConnectScreen(vm, s)
            Screen.WORKFLOWS -> WorkflowsScreen(vm, s)
            Screen.GENERATE -> GenerateScreen(vm, s)
            Screen.HISTORY -> HistoryScreen(vm, s)
        }
        s.busy?.let { msg ->
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(msg)
                    }
                }
            }
        }
    }
}

// ============================ CONNECT ============================

@Composable
fun ConnectScreen(vm: MainViewModel, s: UiState) {
    var url by rememberSaveable(s.url) { mutableStateOf(s.url) }
    Column(
        Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ComfyRemote", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("在手机上用电脑 ComfyUI 出图", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址") },
            supportingText = { Text("模拟器: http://10.0.2.2:8188；手机: Tailscale IP 或电脑局域网 IP（ComfyUI 需 --listen 0.0.0.0）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (s.servers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                s.servers.forEach { srv ->
                    AssistChip(onClick = { url = srv }, label = { Text(srv, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.connect(url) },
            enabled = !s.connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (s.connecting) "连接中…" else "连接") }
        Spacer(Modifier.height(16.dp))
        s.connError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        s.sysInfo?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("已连接", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ============================ WORKFLOWS ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作流（${s.workflows.size}）") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = { vm.openHistory() }) { Text("历史（${s.history.size}）") }
                    IconButton(onClick = { vm.loadWorkflows() }) { Icon(Icons.Default.Refresh, "刷新") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loadingWf -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.wfError != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.wfError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadWorkflows() }) { Text("重试") }
                }
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(s.workflows, key = { it.path }) { wf ->
                        Card(
                            onClick = { vm.openWorkflow(wf) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(wf.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (wf.sizeKb.isNotEmpty())
                                    Text(wf.sizeKb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================ HISTORY ============================

fun decodeSampled(path: String, target: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    var viewing by remember { mutableStateOf<HistoryItem?>(null) }
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录（${s.history.size}）") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
        }
    ) { pad ->
        if (s.history.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有生成记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp)) {
                items(s.history, key = { it.id }) { item ->
                    Card(
                        onClick = { viewing = item },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.images.firstOrNull()?.let { p ->
                                    val thumb by produceState<Bitmap?>(null, p) {
                                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 160) }
                                    }
                                    thumb?.let {
                                        Image(it.asImageBitmap(), null, Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.wfName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · " + item.images.size + " 张",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm.reopenFromHistory(item) }) { Text("重新生成") }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteHistoryItem(item.id) }) {
                                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { item ->
        HistoryDetailDialog(item, onClose = { viewing = null }, onSave = { bmp -> vm.saveResult(bmp) }, onShare = { p -> shareImage(ctx, p) })
    }
}

@Composable
fun HistoryDetailDialog(item: HistoryItem, onClose: () -> Unit, onSave: (Bitmap) -> Unit, onShare: (String) -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.wfName, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
            }
            Text(
                item.summary + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)),
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                items(item.images.size) { idx ->
                    val p = item.images[idx]
                    val bmp by produceState<Bitmap?>(null, p) {
                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 1600) }
                    }
                    bmp?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        Row {
                            Text("第 ${idx + 1} 张", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(8.dp))
                            TextButton(onClick = { onSave(it) }) { Text("保存到相册") }
                            TextButton(onClick = { onShare(p) }) { Text("分享") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ============================ GENERATE ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: MainViewModel, s: UiState) {
    val g = s.gen ?: return
    val ctx = LocalContext.current
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewingIdx by remember { mutableStateOf<Int?>(null) }

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
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler { vm.back() }

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
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
        },
        bottomBar = {
            if (g.running) {
                OutlinedButton(
                    onClick = { vm.cancelGenerate() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text("取消生成") }
            } else {
                Button(
                    onClick = { vm.generate() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text("生成", fontSize = 17.sp) }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            g.error?.let { err ->
                item(key = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "status") {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.status ?: "待生成", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            g.queueInfo?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        g.progress?.let { p -> LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth()) }
                        if (g.running && g.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                                Text("工作流说明", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (notesOpen) "收起" else "展开", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            g.preview?.let { pb ->
                item(key = "preview") {
                    Column {
                        Text("实时预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Image(pb.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
            if (imageFields.isNotEmpty()) {
                item(key = "imgheader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("参考图片", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        if (imageFields.size >= 2) {
                            TextButton(onClick = { multiPicker.launch("image/*") }) { Text("一次选多张") }
                        }
                    }
                }
                itemsIndexed(imageFields, key = { _, f -> f.key }) { _, f ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(f.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val cur = g.values[f.key] ?: f.value
                            Text("当前：$cur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { pendingKey = f.key; picker.launch("image/*") }) { Text("选择图片") }
                                OutlinedButton(onClick = {
                                    pendingKey = f.key
                                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                                    val f2 = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                    cameraUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f2)
                                    camera.launch(cameraUri!!)
                                }) { Text("拍照") }
                            }
                        }
                    }
                }
            }
            itemsIndexed(textFields, key = { _, f -> f.key }) { _, f ->
                OutlinedTextField(
                    value = g.values[f.key] ?: f.value,
                    onValueChange = { vm.updateValue(f.key, it) },
                    label = { Text(f.label) },
                    minLines = if (f.multiline) 3 else 1,
                    maxLines = if (f.multiline) 12 else 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (seedFields.isNotEmpty()) {
                item(key = "seeds") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g.randomSeed, onCheckedChange = { vm.toggleRandomSeed() })
                            Column {
                                Text("每次生成使用随机种子")
                                if (g.randomSeed) Text(
                                    "生成后自动回填上次种子，关闭即可复现",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        seedFields.forEach { f ->
                            OutlinedTextField(
                                value = g.values[f.key] ?: f.value,
                                onValueChange = { vm.updateValue(f.key, it.filter { c -> c.isDigit() }) },
                                label = { Text(f.label + (if (g.randomSeed) "（本次将随机）" else "")) },
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
                                Text("高级参数（${paramFields.size}）", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (advOpen) "收起" else "展开", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            if (g.results.isNotEmpty()) {
                item(key = "resheader") {
                    Text(
                        "生成结果（${g.results.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(g.results, key = { i, _ -> "res$i" }) { idx, bmp ->
                    Column {
                        Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxWidth().clickable { viewingIdx = idx },
                            contentScale = ContentScale.FillWidth
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("结果 ${idx + 1}（点击放大）", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            val p = g.resultPaths.getOrNull(idx)
                            if (p != null) TextButton(onClick = { shareImage(ctx, p) }) {
                                Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("分享")
                            }
                            TextButton(onClick = { vm.saveResult(bmp) }) { Text("保存到相册") }
                        }
                    }
                }
            }
        }
    }
    viewingIdx?.let { idx ->
        g.results.getOrNull(idx)?.let { bmp ->
            ImageViewerDialog(
                bitmap = bmp,
                onClose = { viewingIdx = null },
                onSave = { vm.saveResult(bmp) },
                onShare = { g.resultPaths.getOrNull(idx)?.let { shareImage(ctx, it) } }
            )
        }
    }
}

@Composable
fun ParamField(f: PromptField, value: String, onChange: (String) -> Unit) {
    when (f.ptype) {
        "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value.equals("true", true), onCheckedChange = { onChange(if (it) "true" else "false") })
            Text(f.label)
        }
        "combo" -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.ifEmpty { "选择…" }, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    f.options.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
                    }
                }
            }
            Text(f.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = {
                if (f.ptype == "int") onChange(it.filter { c -> c.isDigit() || c == '-' })
                else if (f.ptype == "float") onChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' })
                else onChange(it)
            },
            label = {
                val range = if (f.min != null || f.max != null) "（${f.min ?: "?"} ~ ${f.max ?: "?"}）" else ""
                Text(f.label + range)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (f.ptype) {
                    "int" -> KeyboardType.Number
                    "float" -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 轻量 Markdown 清理，用于展示工作流说明。 */
fun stripMarkdown(md: String): String = md
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace("**", "")
    .replace(Regex("`(.*?)`"), "$1")
    .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
    .trim()

// ============================ IMAGE VIEWER ============================

@Composable
fun ImageViewerDialog(bitmap: Bitmap, onClose: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Image(
                bitmap.asImageBitmap(), null,
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "分享", tint = Color.White) }
                IconButton(onClick = onSave) { Icon(Icons.Default.Add, "保存到相册", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
            }
            Text(
                "双击复位 · 双指缩放",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

// ============================ SHARE ============================

fun shareImage(context: Context, path: String) {
    val file = File(path)
    if (!file.isFile) return
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
    }
}
