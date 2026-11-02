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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.HistoryItem
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.WorkflowEntry
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.data.repository.ServerHealth
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.SectionHeader
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                    IconButton(onClick = {
                        viewModel.load()
                        viewModel.refreshServerHealth()
                    }) { Icon(Icons.Default.Refresh, L10n.t("刷新", "Refresh")) }
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

            if (state.query.isBlank() && state.servers.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(L10n.t("服务器工作台", "Server workspace"), modifier = Modifier.weight(1f))
                    if (state.probingServers) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                    TextButton(onClick = viewModel::switchToLeastBusy, enabled = !state.probingServers && state.switchingServer == null) {
                        Text(L10n.t("最空闲", "Least busy"))
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.servers, key = { it.url }) { server ->
                        ServerWorkspaceCard(
                            server = server,
                            health = state.serverHealth[server.url],
                            selected = state.session?.url == server.url,
                            appActive = state.appActiveByServer[server.url] ?: 0,
                            switching = state.switchingServer == server.url,
                            onClick = { viewModel.switchServer(server.url) },
                        )
                    }
                }
            }

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
                state.visible.isEmpty() && state.query.isNotBlank() -> {
                    EmptyState(L10n.t("没有匹配结果", "No matches"))
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.query.isBlank() && state.recentPresets.isNotEmpty()) {
                        item("preset-heading") { SectionHeader(L10n.t("最近预设", "Recent setups")) }
                        items(state.recentPresets, key = { "preset:${it.id}" }) { preset ->
                            RecentPresetCard(preset) { viewModel.preparePreset(preset)?.let(onOpenWorkflow) }
                        }
                    }
                    if (state.query.isBlank() && state.recentResults.isNotEmpty()) {
                        item("result-heading") { SectionHeader(L10n.t("最近结果", "Recent results")) }
                        items(state.recentResults, key = { "result:${it.id}" }) { result ->
                            RecentResultCard(result) { viewModel.prepareResult(result)?.let(onOpenWorkflow) }
                        }
                    }
                    if (state.query.isBlank()) item("workflow-heading") { SectionHeader(L10n.t("工作流", "Workflows")) }
                    if (state.visible.isEmpty()) {
                        item("empty-workflow") {
                            EmptyState(
                                L10n.t("没有工作流", "No workflows"),
                                L10n.t("可在电脑端保存 workflow 后刷新", "Save workflows on the server and refresh"),
                            )
                        }
                    } else {
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
}

@Composable
private fun ServerWorkspaceCard(
    server: ServerEntry,
    health: ServerHealth?,
    selected: Boolean,
    appActive: Int,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(230.dp).clickable(enabled = !switching, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name.ifBlank { server.url }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (switching) CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
                else StatusChip(if (health?.online == true) L10n.t("在线", "ONLINE") else L10n.t("离线", "OFFLINE"))
            }
            Text(server.url, fontFamily = Mono, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (health?.online == true) {
                Text(listOf(health.device, health.vramGb).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Comfy ${health.version} · run ${health.running} · queue ${health.pending} · app $appActive · ${health.latencyMs ?: 0}ms",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(health?.error?.take(70) ?: L10n.t("尚未检测", "Not checked"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecentPresetCard(preset: PromptPreset, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    preset.prompts.firstOrNull()?.value.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preset.favorite) Text("★ ", color = MaterialTheme.colorScheme.primary)
            StatusChip(L10n.t("整套", "SETUP"))
            Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentResultCard(item: HistoryItem, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.wfName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.favorite) Text("★ ", color = MaterialTheme.colorScheme.primary)
            Text(L10n.t("复用 ›", "Reuse ›"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
