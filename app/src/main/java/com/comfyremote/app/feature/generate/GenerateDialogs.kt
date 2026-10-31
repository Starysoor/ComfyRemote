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
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.feature.history.decodeSampled
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.comfyremote.app.*
import com.comfyremote.app.data.repository.PromptPreset

@Composable
fun AiResultDialog(initial: String, onDismiss: () -> Unit, onReplace: (String) -> Unit, onAppend: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("AI 生成结果", "AI result"), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("可直接修改后再应用；「追加」会拼在原提示词后面", "Edit freely before applying; Append joins it onto the existing prompt"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onReplace(text) }) { Text(L10n.t("替换", "Replace")) } },
        dismissButton = {
            TextButton(onClick = { onAppend(text) }) { Text(L10n.t("追加", "Append")) }
        }
    )
}

/** Saved prompt snippets; tap applies to the open workflow. */
@Composable
fun BookmarksSheetContent(
    bookmarks: List<PromptPreset>,
    onApply: (PromptPreset) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    Column {
            if (bookmarks.isEmpty()) {
                Text(
                    L10n.t("还没有书签。点右上角收藏当前提示词，或在历史详情里收藏。", "No bookmarks yet. Save the current prompts or bookmark from history."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { onApply(bm) }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(bm.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        bm.prompts.firstOrNull()?.value ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDelete(bm.id) }) {
                                    Icon(Icons.Default.Delete, L10n.t("删除", "Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun CompareImage(res: ResultImage, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, res.localPath) {
        value = withContext(Dispatchers.IO) { decodeSampled(res.localPath, 1600) }
    }
    if (bmp != null) {
        Image(bmp!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** Side-by-side A/B comparison of two results. */
@Composable
fun CompareDialog(results: List<ResultImage>, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(0) }
    var b by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.t("对比", "Compare"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                CompareImage(results[a.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
                CompareImage(results[b.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontFamily = Mono, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                var ea by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { ea = true }) { Text("A: ${a + 1}") }
                    DropdownMenu(expanded = ea, onDismissRequest = { ea = false }) {
                        results.indices.forEach { i -> DropdownMenuItem(text = { Text("${i + 1}") }, onClick = { a = i; ea = false }) }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Text("B", fontFamily = Mono, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                var eb by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { eb = true }) { Text("B: ${b + 1}") }
                    DropdownMenu(expanded = eb, onDismissRequest = { eb = false }) {
                        results.indices.forEach { i -> DropdownMenuItem(text = { Text("${i + 1}") }, onClick = { b = i; eb = false }) }
                    }
                }
            }
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
                    Text(value.ifEmpty { L10n.t("选择…", "Choose…") }, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
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

