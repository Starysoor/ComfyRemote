package com.comfyremote.app.feature.generate


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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.comfyremote.app.ui.ComfyTheme
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.comfyremote.app.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: MainViewModel, s: UiState) {
    val g = s.gen ?: return
    val ctx = LocalContext.current
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
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                },
                actions = {
                    TextButton(onClick = { bookmarksOpen = true }) {
                        Text(L10n.t("书签", "Bookmarks"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        },
        bottomBar = {
            if (g.running) {
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
            g.error?.let { err ->
                item(key = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "status") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ((g.status ?: L10n.t("待生成", "Ready"))).uppercase(),
                                fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp,
                                color = if (g.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            g.queueInfo?.let {
                                Text(it, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        g.progress?.let { p -> LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth().height(3.dp)) }
                        if (g.running && g.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
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
            g.preview?.let { pb ->
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
                        val skills = remember(g.wfName, s.skills) { AiSkillDef.recommendedOrder(s.skills, g.wfName) }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            skills.forEach { skill ->
                                val busyKey = "${f.key}|${skill.id}"
                                TextButton(
                                    onClick = { vm.aiExpand(f.key, g.values[f.key] ?: f.value, skill) },
                                    enabled = s.aiBusy == null && !g.running
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
            if (g.results.isNotEmpty()) {
                item(key = "resheader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.t("生成结果（", "Results (") + "${g.results.size}" + L10n.t("）", ")"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        if (g.results.size >= 2) {
                            TextButton(onClick = { compareOpen = true }) { Text(L10n.t("对比", "Compare")) }
                        }
                    }
                }
                itemsIndexed(g.results, key = { i, _ -> "res$i" }) { idx, bmp ->
                    Column {
                        Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxWidth().clickable { viewingIdx = idx },
                            contentScale = ContentScale.FillWidth
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L10n.t("结果 ", "Result ") + "${idx + 1}" + L10n.t("（点击放大）", " (tap to zoom)"), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            val p = g.resultPaths.getOrNull(idx)
                            if (p != null) TextButton(onClick = { shareImage(ctx, p) }) {
                                Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share"))
                            }
                            TextButton(onClick = { vm.saveResult(bmp) }) { Text(L10n.t("保存到相册", "Save to gallery")) }
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
    s.aiResult?.let { result ->
        AiResultDialog(
            initial = result.text,
            onDismiss = { vm.aiDismiss() },
            onReplace = { vm.aiApply(replace = true, it) },
            onAppend = { vm.aiApply(replace = false, it) }
        )
    }
    if (compareOpen && g.results.size >= 2) {
        CompareDialog(g.results, onDismiss = { compareOpen = false })
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

