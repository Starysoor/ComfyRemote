package com.comfyremote.app.feature.tasks

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.label
import com.comfyremote.app.data.generation.uiLabel
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.feature.generate.ImageViewerDialog
import com.comfyremote.app.feature.generate.shareImage
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.ErrorCard
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenTask: (String) -> Unit,
) {
    val allTasks by viewModel.tasks.collectAsState()
    val tasks = remember(allTasks) {
        allTasks.sortedWith(compareByDescending<GenerationTask> { it.isActive }.thenByDescending { it.createdAt })
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(L10n.t("任务", "Tasks"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("${tasks.count { it.isActive }}", fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            })
        }
    ) { pad ->
        if (tasks.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    L10n.t("还没有任务", "No tasks yet"),
                    L10n.t("提交生成后会出现在这里，离开创作页也会继续运行", "Submitted jobs keep running when you leave Create"),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.taskId }) { task ->
                    TaskCard(task, onClick = { onOpenTask(task.taskId) })
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: GenerationTask, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.request.workflowName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                StatusChip(task.label(), active = task.isActive, error = task.phase == GenerationPhase.FAILED)
            }
            task.nodeTitle?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
            task.error?.let {
                Text(
                    it.uiLabel() + " · " + it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(task.createdAt), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                task.results.firstOrNull()?.let { result ->
                    val thumb by produceState<Bitmap?>(null, result.localPath) {
                        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 96) }
                    }
                    thumb?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp), contentScale = ContentScale.Crop) }
                }
                Text("  ›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TasksViewModel,
    taskId: String,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onReuse: (DraftTransfer) -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val message by viewModel.message.collectAsState()
    val task = tasks.firstOrNull { it.taskId == taskId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var viewingPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("任务详情", "Task detail")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back"))
                    }
                },
            )
        }
    ) { pad ->
        if (task == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    L10n.t("任务已不在内存中", "Task is no longer in memory"),
                    L10n.t("完成结果仍可在历史记录中查看", "Completed results remain in History"),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(task.request.workflowName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusChip(task.label(), active = task.isActive, error = task.phase == GenerationPhase.FAILED)
                        }
                        Meta("SERVER", task.request.serverUrl)
                        Meta("TASK", task.taskId)
                        task.promptId?.let { Meta("PROMPT", it) }
                        task.nodeTitle?.takeIf(String::isNotBlank)?.let { Meta("NODE", "${task.nodeId ?: "?"} · $it") }
                        task.seedInfo.takeIf(String::isNotBlank)?.let { Meta("SEED", it) }
                        Meta("CREATED", formatTimeFull(task.createdAt))
                        task.startedAt?.let { Meta("STARTED", formatTimeFull(it)) }
                        task.completedAt?.let { Meta("COMPLETED", formatTimeFull(it)) }
                        task.progress?.let { p ->
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Text("${(p * 100).toInt()}%", fontFamily = Mono, fontSize = 10.sp)
                        }
                    }
                }
            }
            task.error?.let { error ->
                item {
                    ErrorCard(
                        error.uiLabel(),
                        listOfNotNull(error.nodeTitle, error.nodeType, error.nodeId?.let { "#$it" }, error.message).joinToString(" · "),
                    )
                }
            }
            if (task.results.isNotEmpty()) {
                item { Text(L10n.t("结果", "Results"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(task.results, key = { it.id }) { result ->
                    val bitmap by produceState<Bitmap?>(null, result.localPath) {
                        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1200) }
                    }
                    if (bitmap == null) {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        Image(
                            bitmap!!.asImageBitmap(),
                            null,
                            Modifier.fillMaxWidth().clickable { viewingPath = result.localPath },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        task.isActive -> OutlinedButton(
                            onClick = { viewModel.cancel(task.taskId) },
                            enabled = task.phase != GenerationPhase.CANCELLING,
                            modifier = Modifier.weight(1f),
                        ) { Text(L10n.t("取消", "Cancel")) }
                        task.phase == GenerationPhase.FAILED || task.phase == GenerationPhase.CANCELLED -> Button(
                            onClick = { viewModel.retry(task.taskId)?.let(onRetry) },
                            modifier = Modifier.weight(1f),
                        ) { Text(L10n.t("重试", "Retry")) }
                    }
                    OutlinedButton(
                        onClick = { viewModel.prepareReuse(task.taskId)?.let(onReuse) },
                        modifier = Modifier.weight(1f),
                    ) { Text(L10n.t("复用参数", "Reuse parameters")) }
                }
            }
        }
    }

    viewingPath?.let { path ->
        val bitmap by produceState<Bitmap?>(null, path) {
            value = withContext(Dispatchers.IO) { decodeSampled(path, 1800) }
        }
        bitmap?.let { image ->
            ImageViewerDialog(
                bitmap = image,
                onClose = { viewingPath = null },
                onSave = { viewModel.saveResult(image) },
                onShare = { shareImage(context, path) },
            )
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row {
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(76.dp))
        Text(value, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

private fun formatTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun formatTimeFull(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
