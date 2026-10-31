package com.comfyremote.app.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.MainViewModel
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.label
import com.comfyremote.app.feature.history.decodeSampled
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsState()
    var viewingPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(L10n.t("任务", "Tasks"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${tasks.count { it.isActive }}", fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { pad ->
        if (tasks.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(L10n.t("还没有生成任务", "No generation tasks yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    L10n.t("提交生成后任务会显示在这里，离开页面也会继续执行", "Submitted jobs keep running even if you leave the page"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp)
            ) {
                items(tasks, key = { it.taskId }) { t ->
                    TaskCard(
                        task = t,
                        onCancel = { vm.cancelTask(t.taskId) },
                        onRetry = { vm.retryTask(t.taskId) },
                        onOpenResult = { viewingPath = t.results.firstOrNull()?.localPath },
                    )
                }
            }
        }
    }
    viewingPath?.let { p ->
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, p) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 1600) }
        }
        bmp?.let {
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewingPath = null }) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: GenerationTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenResult: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.request.workflowName,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    task.label().uppercase(),
                    fontFamily = Mono, fontSize = 11.sp,
                    color = if (task.isActive) MaterialTheme.colorScheme.primary
                    else if (task.phase == GenerationPhase.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            task.nodeTitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.progress?.let { p ->
                LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            task.error?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(task.createdAt)),
                    fontFamily = Mono, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.seedInfo.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "seed: ${task.seedInfo.take(20)}",
                        fontFamily = Mono, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                task.results.firstOrNull()?.let { res ->
                    val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, res.localPath) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(res.localPath, 96) }
                    }
                    thumb?.let {
                        Image(
                            it.asImageBitmap(), null,
                            Modifier.size(44.dp).clickable { onOpenResult() },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                when {
                    task.phase == GenerationPhase.RUNNING || task.phase == GenerationPhase.CANCELLING ->
                        OutlinedButton(onClick = onCancel, enabled = task.phase == GenerationPhase.RUNNING) {
                            Text(L10n.t("取消", "Cancel"))
                        }
                    task.phase == GenerationPhase.QUEUED || task.phase == GenerationPhase.QUEUEING ->
                        Text(L10n.t("排队等待…", "Waiting…"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    task.phase == GenerationPhase.FAILED || task.phase == GenerationPhase.CANCELLED ->
                        OutlinedButton(onClick = onRetry) { Text(L10n.t("重试", "Retry")) }
                    task.phase == GenerationPhase.SUCCEEDED && task.results.isNotEmpty() ->
                        OutlinedButton(onClick = onOpenResult) { Text(L10n.t("看图", "Open")) }
                }
            }
        }
    }
}
