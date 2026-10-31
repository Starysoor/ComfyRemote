package com.comfyremote.app.feature.history


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
import com.comfyremote.app.feature.generate.shareImage

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
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var viewing by remember { mutableStateOf<HistoryItem?>(null) }
    val ctx = LocalContext.current

    fun exitSelection() { selecting = false; selected = mutableSetOf() }

    BackHandler {
        if (selecting) exitSelection() else vm.back()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) Text(L10n.t("已选 ", "Selected ") + "${selected.size}")
                    else Text(L10n.t("历史记录（", "History (") + "${s.history.size}" + L10n.t("）", ")"))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selecting) exitSelection() else vm.back()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = { selected = s.history.map { it.id }.toSet() }) {
                            Text(L10n.t("全选", "Select all"))
                        }
                        IconButton(onClick = {
                            vm.deleteHistoryItems(selected)
                            exitSelection()
                        }, enabled = selected.isNotEmpty()) {
                            Icon(Icons.Default.Delete, L10n.t("删除", "Delete"), tint = MaterialTheme.colorScheme.error)
                        }
                    } else if (s.history.isNotEmpty()) {
                        TextButton(onClick = { selecting = true }) {
                            Text(L10n.t("多选", "Select"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            )
        }
    ) { pad ->
        if (s.history.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(L10n.t("还没有生成记录", "No generations yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)) {
                items(s.history, key = { it.id }) { item ->
                    Card(
                        onClick = { if (selecting) {
                            selected = if (item.id in selected) selected - item.id else selected + item.id
                        } else viewing = item },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) {
                                    Checkbox(
                                        checked = item.id in selected,
                                        onCheckedChange = {
                                            selected = if (it) selected + item.id else selected - item.id
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
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
                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · " + item.images.size + L10n.t(" 张", " image(s)"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (!selecting) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm.reopenFromHistory(item) }) { Text(L10n.t("重新生成", "Re-generate")) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteHistoryItem(item.id) }) {
                                    Icon(Icons.Default.Delete, L10n.t("删除", "Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { item ->
        HistoryDetailDialog(item, onClose = { viewing = null }, onSave = { bmp -> vm.saveResult(bmp) }, onShare = { p -> shareImage(ctx, p) }, onBookmark = { vm.addBookmarkFromHistory(it) })
    }
}

@Composable
fun HistoryDetailDialog(item: HistoryItem, onClose: () -> Unit, onSave: (Bitmap) -> Unit, onShare: (String) -> Unit, onBookmark: (HistoryItem) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        android.widget.Toast.makeText(ctx, L10n.t("已复制", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.wfName, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · " + item.images.size + L10n.t(" 张", " image(s)"),
                color = Color.White.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)) {
                item(key = "prompts") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L10n.t("提示词", "Prompts"), fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                if (item.prompts.isNotEmpty()) {
                                    TextButton(onClick = { copy(item.prompts.joinToString("\n") { it.value }) }) {
                                        Text(L10n.t("复制全部", "Copy all"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (item.prompts.isEmpty()) {
                                Text(L10n.t("（此记录未保存完整提示词）", "(No saved prompts for this entry)"), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            item.prompts.forEach { p ->
                                Text(p.label, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(2.dp))
                                SelectionContainer {
                                    Text(p.value, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { copy(p.value) }) { Text(L10n.t("复制", "Copy"), style = MaterialTheme.typography.labelMedium) }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (item.seeds.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SEED", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.seeds, fontFamily = Mono, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                                    TextButton(onClick = { copy(item.seeds) }) { Text(L10n.t("复制", "Copy"), style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                items(item.images.size, key = { "img$it" }) { idx ->
                    val p = item.images[idx]
                    val bmp by produceState<Bitmap?>(null, p) {
                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 1600) }
                    }
                    bmp?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        Row {
                            Text(L10n.t("第 ", "Image ") + "${idx + 1}" + L10n.t(" 张", ""), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(8.dp))
                            TextButton(onClick = { onSave(it) }) { Text(L10n.t("保存到相册", "Save to gallery")) }
                            TextButton(onClick = { onShare(p) }) { Text(L10n.t("分享", "Share")) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ============================ GENERATE ============================

