package com.comfyremote.app.feature.generate

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comfyremote.app.PromptField
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AiResultDialog(
    initial: String,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
    onAppend: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("AI 生成结果", "AI result")) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth())
                Text(
                    L10n.t("可修改后替换或追加到原提示词", "Edit before replacing or appending"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onReplace(text) }) { Text(L10n.t("替换", "Replace")) } },
        dismissButton = { TextButton(onClick = { onAppend(text) }) { Text(L10n.t("追加", "Append")) } },
    )
}

@Composable
fun PromptLibrarySheetContent(
    presets: List<PromptPreset>,
    query: String,
    onQuery: (String) -> Unit,
    onApply: (PromptPreset) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.t("提示词库", "Prompt Library"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSaveCurrent) { Text(L10n.t("保存当前", "Save current")) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(L10n.t("搜索", "Search")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (presets.isEmpty()) {
            Text(
                L10n.t("还没有匹配的提示词预设", "No matching prompt presets"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { onApply(preset) },
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    preset.prompts.firstOrNull()?.value.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                preset.lastUsedAt?.let {
                                    Text(L10n.t("最近使用", "RECENT"), fontFamily = Mono, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { onDelete(preset.id) }) {
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
private fun CompareImage(result: ResultImage, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, result.localPath) {
        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun CompareDialog(results: List<ResultImage>, onDismiss: () -> Unit) {
    if (results.size < 2) return
    var a by remember { mutableStateOf(0) }
    var b by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.t("A/B 对比", "A/B Compare"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                CompareImage(results[a.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
                CompareImage(results[b.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ResultPicker("A", a, results.indices) { a = it }
                ResultPicker("B", b, results.indices) { b = it }
            }
        }
    }
}

@Composable
private fun ResultPicker(label: String, value: Int, indices: IntRange, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${value + 1}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            indices.forEach { index ->
                DropdownMenuItem(text = { Text("${index + 1}") }, onClick = { onChange(index); expanded = false })
            }
        }
    }
}

@Composable
fun ParamField(field: PromptField, value: String, onChange: (String) -> Unit) {
    when (field.ptype) {
        "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(value.equals("true", true), { onChange(if (it) "true" else "false") })
            Text(field.label)
        }
        "combo" -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.ifBlank { L10n.t("选择…", "Choose…") }, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▾")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
                    }
                }
            }
            Text(field.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = {
                when (field.ptype) {
                    "int" -> onChange(it.filter { c -> c.isDigit() || c == '-' })
                    "float" -> onChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' })
                    else -> onChange(it)
                }
            },
            label = { Text(field.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.ptype) {
                    "int" -> KeyboardType.Number
                    "float" -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
