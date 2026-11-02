package com.comfyremote.app.feature.tasks

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.label
import com.comfyremote.app.data.generation.uiLabel
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.feature.generate.MediaViewerDialog
import com.comfyremote.app.feature.generate.isNonImageMedia
import com.comfyremote.app.feature.generate.shareMedia
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.LocalAppSettings
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.ErrorCard
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val ui by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val tasks = ui.visible
    val hasFinished = ui.tasks.any { it.isTerminal }
    val hasQueued = ui.tasks.any { it.localPending || it.phase == GenerationPhase.QUEUEING || it.phase == GenerationPhase.QUEUED }
    val hasFailed = ui.tasks.any { it.phase == GenerationPhase.FAILED }
    var confirmClearFinished by remember { mutableStateOf(false) }
    var serverMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(L10n.t("任务", "Tasks"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${ui.tasks.count { it.isActive }}", fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TaskFilter.values().forEach { filter ->
                    val selected = ui.filter == filter
                    if (selected) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                filterLabel(filter),
                                modifier = Modifier.clickable { viewModel.setFilter(filter) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setFilter(filter) }) { Text(filterLabel(filter)) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { serverMenu = true }) {
                        Text(
                            ui.serverUrl?.let(viewModel::serverLabel) ?: L10n.t("全部服务器", "All servers"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(expanded = serverMenu, onDismissRequest = { serverMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(L10n.t("全部服务器", "All servers")) },
                            onClick = { viewModel.setServerFilter(null); serverMenu = false },
                        )
                        ui.serverUrls.forEach { url ->
                            DropdownMenuItem(
                                text = { Text(viewModel.serverLabel(url)) },
                                onClick = { viewModel.setServerFilter(url); serverMenu = false },
                            )
                        }
                    }
                }
                Text(
                    "LOCAL ${ui.tasks.count { it.localPending }}",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { actionMenu = true }) { Icon(Icons.Default.MoreVert, L10n.t("更多", "More")) }
                    DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (ui.localQueuePaused) L10n.t("继续本地队列", "Resume local queue") else L10n.t("暂停本地队列", "Pause local queue")) },
                            onClick = { viewModel.toggleLocalQueuePaused(); actionMenu = false },
                        )
                        if (hasQueued) {
                            DropdownMenuItem(
                                text = { Text(L10n.t("取消排队", "Cancel queued")) },
                                onClick = { viewModel.cancelQueued(); actionMenu = false },
                            )
                        }
                        if (hasFailed) {
                            DropdownMenuItem(
                                text = { Text(L10n.t("重试失败", "Retry failed")) },
                                onClick = { viewModel.retryFailed(); actionMenu = false },
                            )
                        }
                        if (hasFinished) {
                            DropdownMenuItem(
                                text = { Text(L10n.t("清理已结束任务", "Clear finished")) },
                                onClick = { confirmClearFinished = true; actionMenu = false },
                            )
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (ui.tasks.isEmpty()) L10n.t("还没有任务", "No tasks yet") else L10n.t("当前筛选没有任务", "No tasks match this filter"),
                        L10n.t("任务会持续运行，并在 App 重启后尝试恢复", "Jobs continue outside Create and recover after app restart"),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tasks.groupBy { it.request.serverUrl }.forEach { (server, serverTasks) ->
                        item("server:$server") {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.serverLabel(server), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${serverTasks.count { it.isActive }} active · ${serverTasks.size}", fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(serverTasks, key = { it.taskId }) { task ->
                            TaskCard(
                                task = task,
                                onClick = { onOpenTask(task.taskId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClearFinished) {
        AlertDialog(
            onDismissRequest = { confirmClearFinished = false },
            title = { Text(L10n.t("清理已完成任务？", "Clear finished tasks?")) },
            text = { Text(L10n.t("只会移除任务列表记录，结果库中的图片和历史记录不会删除。", "This only removes task-list records. Result Library images and history are kept.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFinished()
                    confirmClearFinished = false
                }) { Text(L10n.t("清理", "Clear")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFinished = false }) { Text(L10n.t("取消", "Cancel")) }
            },
        )
    }
}

@Composable
private fun TaskCard(
    task: GenerationTask,
    onClick: () -> Unit,
) {
    val compact = LocalAppSettings.current.compactUi
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.request.workflowName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                task.batchIndex?.let { StatusChip("$it/${task.batchSize ?: it}"); Spacer(Modifier.width(5.dp)) }
                if (task.recovered) {
                    StatusChip(L10n.t("已恢复", "RECOVERED"))
                    Spacer(Modifier.width(5.dp))
                }
                StatusChip(task.label(), active = task.isActive, error = task.phase == GenerationPhase.FAILED)
            }
            task.queuePosition?.let {
                Text(L10n.t("队列位置 #", "Queue position #") + it, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (task.localPending) {
                Text(L10n.t("等待手机本地队列放行", "Waiting in app-side queue"), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            task.nodeTitle?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RunningNodeProgress(task)
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
                Text(" · ", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ElapsedText(task)
                Spacer(Modifier.weight(1f))
                task.results.firstOrNull()?.let { result ->
                    MediaThumbnail(result.localPath, 44.dp)
                }
                Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ElapsedText(task: GenerationTask) {
    val elapsed by produceState(initialValue = task.elapsedMs(), task.taskId, task.isActive, task.completedAt) {
        value = task.elapsedMs()
        while (task.isActive) {
            delay(1_000)
            value = task.elapsedMs()
        }
    }
    Text(formatDuration(elapsed), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RunningNodeProgress(task: GenerationTask) {
    if (task.phase != GenerationPhase.RUNNING) return
    val p = task.progress?.coerceIn(0f, 1f)
    if (p == null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(L10n.t("当前节点执行中", "Current node running"), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.t("当前节点进度", "Current node progress"), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${(p * 100).toInt()}%", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
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
    val clipboard = LocalClipboardManager.current
    var viewingPath by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCloudCancel by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("任务详情", "Task detail")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) } },
            )
        }
    ) { pad ->
        if (task == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(L10n.t("任务已不在运行列表", "Task is no longer in runtime list"), L10n.t("完成结果仍可在结果库中查看", "Completed results remain in Result Library"))
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
                            task.batchIndex?.let { StatusChip("$it/${task.batchSize ?: it}"); Spacer(Modifier.width(5.dp)) }
                            if (task.recovered) {
                                StatusChip(L10n.t("恢复任务", "RECOVERED"))
                                Spacer(Modifier.width(5.dp))
                            }
                            StatusChip(task.label(), active = task.isActive, error = task.phase == GenerationPhase.FAILED)
                        }
                        Meta("SERVER", viewModel.serverLabel(task.request.serverUrl))
                        Meta("TASK", task.taskId)
                        task.batchId?.let { Meta("BATCH", "${task.batchIndex ?: "?"}/${task.batchSize ?: "?"} · $it") }
                        task.promptId?.let { Meta("PROMPT", it) }
                        task.queuePosition?.let { Meta("QUEUE", "#$it") }
                        if (task.localPending) Meta("QUEUE", "LOCAL")
                        task.nodeTitle?.takeIf(String::isNotBlank)?.let { Meta("NODE", "${task.nodeId ?: "?"} · $it") }
                        task.seedInfo.takeIf(String::isNotBlank)?.let { Meta("SEED", it) }
                        task.recoveryNote?.takeIf(String::isNotBlank)?.let { Meta("RECOVERY", it) }
                        Meta("CREATED", formatTimeFull(task.createdAt))
                        task.startedAt?.let { Meta("STARTED", formatTimeFull(it)) }
                        task.completedAt?.let { Meta("COMPLETED", formatTimeFull(it)) }
                        Row { Text("ELAPSED", fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(76.dp)); ElapsedText(task) }
                        RunningNodeProgress(task)
                    }
                }
            }
            task.error?.let { error -> item {
                ErrorCard(
                    error.uiLabel(),
                    listOfNotNull(error.nodeTitle, error.nodeType, error.nodeId?.let { "#$it" }, error.message).joinToString(" · ")
                )
            } }
            if (task.results.isNotEmpty()) {
                item { Text(L10n.t("结果", "Results"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(task.results, key = { it.id }) { result ->
                    if (isNonImageMedia(result.localPath)) {
                        MediaThumbnail(
                            path = result.localPath,
                            size = 220.dp,
                            modifier = Modifier.fillMaxWidth().clickable { viewingPath = result.localPath },
                        )
                    } else {
                        val bitmap by produceState<Bitmap?>(null, result.localPath) {
                            value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1200) }
                        }
                        if (bitmap == null) {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else {
                            Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxWidth().clickable { viewingPath = result.localPath }, contentScale = ContentScale.FillWidth)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.isActive) {
                            OutlinedButton(
                                onClick = {
                                    if (task.request.provider == AutoDlConfig.PROVIDER) confirmCloudCancel = true
                                    else viewModel.cancel(task.taskId)
                                },
                                enabled = task.phase != GenerationPhase.CANCELLING,
                                modifier = Modifier.weight(1f),
                            ) { Text(L10n.t("取消", "Cancel")) }
                        } else {
                            Button(
                                onClick = { viewModel.retry(task.taskId)?.let(onRetry) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    if (task.phase == GenerationPhase.SUCCEEDED) L10n.t("再生成", "Run again")
                                    else L10n.t("重新生成", "Retry")
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.prepareReuse(task.taskId)?.let(onReuse) },
                            modifier = Modifier.weight(1f),
                        ) { Text(L10n.t("复用参数", "Reuse")) }
                    }
                    if (!task.isActive && !task.promptId.isNullOrBlank()) {
                        OutlinedButton(onClick = { viewModel.recover(task.taskId) }, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.t("恢复原任务", "Recover original"))
                        }
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(viewModel.diagnostics(task.taskId)))
                            android.widget.Toast.makeText(context, L10n.t("诊断已复制", "Diagnostics copied"), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(L10n.t("复制诊断信息", "Copy diagnostics")) }
                    if (task.isTerminal) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(L10n.t("删除任务", "Delete task"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmCloudCancel) {
        AlertDialog(
            onDismissRequest = { confirmCloudCancel = false },
            title = { Text(L10n.t("取消云端任务？", "Cancel cloud task?")) },
            text = {
                Text(
                    L10n.t(
                        "这里只能停止手机端跟踪；AutoDL 平台侧任务可能继续执行并产生费用。",
                        "This only stops local tracking. The AutoDL job may keep running and billing.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloudCancel = false
                    task?.let { viewModel.cancel(it.taskId) }
                }) { Text(L10n.t("仍然取消", "Cancel anyway"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloudCancel = false }) { Text(L10n.t("继续等待", "Keep waiting")) }
            },
        )
    }

    if (confirmDelete && task != null && task.isTerminal) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L10n.t("删除这个任务？", "Delete this task?")) },
            text = { Text(L10n.t("只会从任务列表移除，结果库和生成图片不会删除。", "This removes it from Tasks only. Result Library entries and generated images are kept.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(task.taskId)
                    confirmDelete = false
                    onBack()
                }) { Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(L10n.t("取消", "Cancel")) }
            },
        )
    }

    viewingPath?.let { path ->
        MediaViewerDialog(
            path = path,
            onClose = { viewingPath = null },
            onSave = { viewModel.saveMedia(path) },
            onShare = { shareMedia(context, path) },
        )
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row {
        Text(label, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(76.dp))
        Text(value, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

private fun filterLabel(filter: TaskFilter): String = when (filter) {
    TaskFilter.ALL -> L10n.t("全部", "All")
    TaskFilter.ACTIVE -> L10n.t("进行中", "Active")
    TaskFilter.QUEUED -> L10n.t("排队", "Queued")
    TaskFilter.FAILED -> L10n.t("失败", "Failed")
    TaskFilter.DONE -> L10n.t("已结束", "Done")
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

private fun formatTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun formatTimeFull(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
