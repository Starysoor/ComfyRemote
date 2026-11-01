package com.comfyremote.app.feature.workflows

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.WorkflowEntry
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(
    viewModel: WorkflowsViewModel,
    onOpenWorkflow: (WorkflowEntry) -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(L10n.t("创作", "Create"), fontWeight = FontWeight.Bold)
                        state.session?.let {
                            Text(
                                it.device.ifBlank { it.url },
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onServers) {
                        Text("● ", color = MaterialTheme.colorScheme.primary)
                        Text(L10n.t("服务器", "Server"), style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = viewModel::load) { Icon(Icons.Default.Refresh, L10n.t("刷新", "Refresh")) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, L10n.t("设置", "Settings")) }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(L10n.t("搜索工作流", "Search workflows")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading && state.workflows.isEmpty() -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.weight(1f))
                }
                state.error != null && state.workflows.isEmpty() -> {
                    Spacer(Modifier.weight(1f))
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                    Button(onClick = viewModel::load, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(L10n.t("重试", "Retry")) }
                    Spacer(Modifier.weight(1f))
                }
                state.visible.isEmpty() -> {
                    EmptyState(
                        if (state.query.isBlank()) L10n.t("没有工作流", "No workflows") else L10n.t("没有匹配结果", "No matches"),
                        L10n.t("可在电脑端保存 workflow 后刷新", "Save workflows on the server and refresh"),
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val recent = state.recentPaths.toSet()
                    items(state.visible, key = { it.path }) { wf ->
                        WorkflowCard(
                            workflow = wf,
                            favorite = wf.path in state.favoritePaths,
                            recent = wf.path in recent,
                            onToggleFavorite = { viewModel.toggleFavorite(wf.path) },
                            onClick = {
                                viewModel.opened(wf)
                                onOpenWorkflow(wf)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: WorkflowEntry,
    favorite: Boolean,
    recent: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(workflow.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(workflowType(workflow))
                    if (recent) Text(L10n.t("最近", "RECENT"), fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    if (workflow.sizeKb.isNotBlank()) Text(workflow.sizeKb, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onToggleFavorite) {
                Text(if (favorite) "★" else "☆", fontSize = 21.sp, color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
