package com.comfyremote.app.feature.history

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.feature.generate.shareImage
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onReuse: (DraftTransfer) -> Unit,
) {
    val history by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var viewingId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) Text(L10n.t("已选 ", "Selected ") + selected.size)
                    else Text(L10n.t("结果库", "Result Library"), fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = { selected = history.map { it.id }.toSet() }) { Text(L10n.t("全选", "All")) }
                        IconButton(
                            onClick = { viewModel.deleteMany(selected); selecting = false; selected = emptySet() },
                            enabled = selected.isNotEmpty(),
                        ) { Icon(Icons.Default.Delete, L10n.t("删除", "Delete"), tint = MaterialTheme.colorScheme.error) }
                    } else if (history.isNotEmpty() || query.isNotBlank()) {
                        TextButton(onClick = { selecting = true }) { Text(L10n.t("多选", "Select")) }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(L10n.t("搜索结果、提示词、标签、Seed", "Search results, prompts, tags, seed")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::toggleFavoritesOnly) {
                    Text(if (favoritesOnly) "★" else "☆")
                }
            }
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (query.isBlank() && !favoritesOnly) L10n.t("还没有生成记录", "No generations yet")
                        else L10n.t("没有匹配结果", "No matching results")
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            serverLabel = viewModel.serverLabel(item.serverUrl),
                            selecting = selecting,
                            selected = item.id in selected,
                            onToggle = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                            onOpen = { viewingId = item.id },
                            onReuse = { viewModel.prepareReuse(item)?.let(onReuse) },
                            onFavorite = { viewModel.toggleFavorite(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
            }
        }
    }

    val viewing = viewingId?.let { id -> history.firstOrNull { it.id == id } }
    viewing?.let { item ->
        HistoryDetailDialog(
            item = item,
            serverLabel = viewModel.serverLabel(item.serverUrl),
            onClose = { viewingId = null },
            onSave = viewModel::save,
            onShare = { shareImage(context, it) },
            onBookmark = { viewModel.bookmark(item) },
            onReuse = { viewModel.prepareReuse(item)?.let(onReuse); viewingId = null },
            onFavorite = { viewModel.toggleFavorite(item.id) },
            onSetTags = { viewModel.setTags(item.id, it) },
        )
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    serverLabel: String,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onReuse: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = { if (selecting) onToggle() else onOpen() }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle() })
                    Spacer(Modifier.width(4.dp))
                }
                item.images.firstOrNull()?.let { path ->
                    val bitmap by produceState<Bitmap?>(null, path) {
                        value = withContext(Dispatchers.IO) { decodeSampled(path, 160) }
                    }
                    bitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.size(68.dp), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.wfName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (item.favorite) Text("★", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · ${item.images.size}" + L10n.t(" 张", " image(s)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (serverLabel.isNotBlank()) Text(serverLabel, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.seeds.takeIf(String::isNotBlank)?.let { Text("seed: $it", fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    if (item.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.take(3).forEach { StatusChip(it) }
                    }
                }
            }
            if (!selecting) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onFavorite) { Text(if (item.favorite) L10n.t("取消收藏", "Unfavorite") else L10n.t("收藏", "Favorite")) }
                TextButton(onClick = onReuse) { Text(L10n.t("复用", "Reuse")) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun HistoryDetailDialog(
    item: HistoryItem,
    serverLabel: String,
    onClose: () -> Unit,
    onSave: (Bitmap) -> Unit,
    onShare: (String) -> Unit,
    onBookmark: () -> Unit,
    onReuse: () -> Unit,
    onFavorite: () -> Unit,
    onSetTags: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var tagsText by remember(item.id, item.tags) { mutableStateOf(item.tags.joinToString(", ")) }
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        android.widget.Toast.makeText(context, L10n.t("已复制", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.wfName, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onFavorite) { Text(if (item.favorite) "★" else "☆", color = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(item.images, key = { it }) { path ->
                    val bitmap by produceState<Bitmap?>(null, path) {
                        value = withContext(Dispatchers.IO) { decodeSampled(path, 1600) }
                    }
                    bitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        Row(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onSave(it) }) { Text(L10n.t("保存", "Save")) }
                            TextButton(onClick = { onShare(path) }) { Text(L10n.t("分享", "Share")) }
                        }
                    }
                }
                item("metadata") {
                    Surface(shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (serverLabel.isNotBlank()) Text("SERVER · $serverLabel", fontFamily = Mono, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                            Row {
                                Text(L10n.t("提示词", "Prompts"), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (item.prompts.isNotEmpty()) TextButton(onClick = { copy(item.prompts.joinToString("\n") { it.value }) }) { Text(L10n.t("复制全部", "Copy all")) }
                            }
                            item.prompts.forEach { prompt ->
                                Text(prompt.label, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Text(prompt.value, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                            }
                            item.seeds.takeIf(String::isNotBlank)?.let {
                                Text("SEED · $it", fontFamily = Mono, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                            }
                            OutlinedTextField(
                                value = tagsText,
                                onValueChange = { tagsText = it },
                                label = { Text(L10n.t("标签，用逗号分隔", "Tags, comma separated")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = onReuse) { Text(L10n.t("复用参数", "Reuse parameters")) }
                                TextButton(onClick = onBookmark) { Text(L10n.t("存提示词", "Save prompt")) }
                                TextButton(onClick = { onSetTags(tagsText) }) { Text(L10n.t("保存标签", "Save tags")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
