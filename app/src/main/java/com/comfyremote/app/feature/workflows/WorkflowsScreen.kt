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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.comfyremote.app.ui.LocalAppSettings
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
    val settings = LocalAppSettings.current
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
                        val subtitle = when {
                            state.source == WorkflowSource.CLOUD ->
                                if (state.autoDlConfigured) "AutoDL · READY" else "AutoDL · TOKEN NEEDED"
                            state.session != null -> state.session!!.device.ifBlank { state.session!!.url }
                            else -> L10n.t("未连接本地服务器", "No local server")
                        }
                        Text(
                            subtitle,
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.source == WorkflowSource.LOCAL) {
                        TextButton(onClick = onServers) {
                            Text("● ", color = MaterialTheme.colorScheme.primary)
                            Text(L10n.t("服务器", "Server"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = {
                        if (state.source == WorkflowSource.CLOUD) viewModel.loadCloud(force = true)
                        else {
                            viewModel.load()
                            viewModel.refreshServerHealth()
                        }
                    }) { Icon(Icons.Default.Refresh, L10n.t("刷新", "Refresh")) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, L10n.t("设置", "Settings")) }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(L10n.t("搜索工作流", "Search workflows")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item("source") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip(
                        label = L10n.t("本地 ComfyUI", "Local ComfyUI"),
                        selected = state.source == WorkflowSource.LOCAL,
                        onClick = { viewModel.setSource(WorkflowSource.LOCAL) },
                    )
                    SourceChip(
                        label = L10n.t("云端模型", "Cloud models"),
                        selected = state.source == WorkflowSource.CLOUD,
                        onClick = { viewModel.setSource(WorkflowSource.CLOUD) },
                    )
                }
            }

            if (state.source == WorkflowSource.LOCAL && state.query.isBlank() &&
                settings.showServerWorkspace && state.servers.isNotEmpty()
            ) {
                item("servers-heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(L10n.t("服务器工作台", "Server workspace"), modifier = Modifier.weight(1f))
                        if (state.probingServers) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                        TextButton(
                            onClick = viewModel::switchToLeastBusy,
                            enabled = !state.probingServers && state.switchingServer == null,
                        ) { Text(L10n.t("最空闲", "Least busy")) }
                    }
                }
                item("servers") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.servers, key = { it.url }) { server ->
                            ServerChip(
                                server = server,
                                health = state.serverHealth[server.url],
                                selected = state.session?.url == server.url,
                                appActive = state.activeByServer(server.url),
                                switching = state.switchingServer == server.url,
                                onClick = { viewModel.switchServer(server.url) },
                            )
                        }
                    }
                }
            }

            if (state.source == WorkflowSource.CLOUD && !state.autoDlConfigured) {
                item("cloud-token") {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                L10n.t(
                                    "可浏览云端工作流；提交前需配置 AutoDL Token",
                                    "Browse cloud workflows now; add an AutoDL token before running",
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onSettings) { Text(L10n.t("去设置", "Settings")) }
                        }
                    }
                }
            }

            if (state.query.isBlank()) {
                val cloud = state.source == WorkflowSource.CLOUD
                val presets = state.recentPresets.filter { preset ->
                    if (cloud) preset.workflowPath?.startsWith("autodl:") == true
                    else preset.workflowPath?.startsWith("autodl:") != true
                }
                val results = state.recentResults.filter { item ->
                    if (cloud) item.serverUrl == com.comfyremote.app.data.cloud.AutoDlConfig.BASE_URL
                    else state.session == null || item.serverUrl.isBlank() ||
                        item.serverUrl == state.session?.url
                }
                if (settings.recentPresetsCount > 0 && presets.isNotEmpty()) {
                    item("preset-heading") { SectionHeader(L10n.t("最近预设", "Recent setups")) }
                    items(presets.take(settings.recentPresetsCount), key = { "preset:${it.id}" }) { preset ->
                        RecentPresetCard(preset) { viewModel.preparePreset(preset)?.let(onOpenWorkflow) }
                    }
                }
                if (settings.recentResultsCount > 0 && results.isNotEmpty()) {
                    item("result-heading") { SectionHeader(L10n.t("最近结果", "Recent results")) }
                    items(results.take(settings.recentResultsCount), key = { "result:${it.id}" }) { result ->
                        RecentResultCard(result) { viewModel.prepareResult(result)?.let(onOpenWorkflow) }
                    }
                }
            }

            item("workflow-heading") {
                SectionHeader(
                    if (state.source == WorkflowSource.CLOUD) L10n.t("云端工作流", "Cloud workflows")
                    else L10n.t("工作流", "Workflows")
                )
            }

            val loading = if (state.source == WorkflowSource.CLOUD) state.cloudLoading else state.loading
            val error = if (state.source == WorkflowSource.CLOUD) state.cloudError else state.error
            if (loading && state.visible.isEmpty()) {
                item("loading") {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (error != null && state.visible.isEmpty()) {
                item("error") {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = viewModel::load) { Text(L10n.t("重试", "Retry")) }
                    }
                }
            } else if (state.visible.isEmpty()) {
                item("empty") {
                    EmptyState(
                        if (state.source == WorkflowSource.CLOUD) {
                            L10n.t("云端没有可用工作流", "No cloud workflows available")
                        } else {
                            L10n.t("没有工作流", "No workflows")
                        },
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

private fun WorkflowsUiState.activeByServer(url: String): Int = appActiveByServer[url] ?: 0

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                label,
                modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                label,
                modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerChip(
    server: ServerEntry,
    health: ServerHealth?,
    selected: Boolean,
    appActive: Int,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(190.dp).clickable(enabled = !switching, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name.ifBlank { server.url },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (switching) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                else Text(
                    if (health?.online == true) "●" else "○",
                    color = if (health?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp,
                )
            }
            if (health?.online == true) {
                Text(
                    "run ${health.running} · q ${health.pending} · app $appActive",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    health?.error?.take(40) ?: L10n.t("未检测", "Not checked"),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentPresetCard(preset: PromptPreset, onClick: () -> Unit) {
    val compact = LocalAppSettings.current.compactUi
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
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
    val compact = LocalAppSettings.current.compactUi
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.wfName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
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
    val compact = LocalAppSettings.current.compactUi
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 11.dp else 15.dp, vertical = if (compact) 9.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
                Text(
                    workflow.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (compact) 14.sp else 15.sp,
                )
                if (workflow.cloud && workflow.description.isNotBlank()) {
                    Text(
                        workflow.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(workflowType(workflow))
                    if (recent) Text(L10n.t("最近", "RECENT"), fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    if (workflow.priceLabel.isNotBlank()) {
                        Text(
                            workflow.priceLabel,
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (workflow.priceType.isNotBlank()) {
                        Text(
                            workflow.priceType.replace('_', ' '),
                            fontFamily = Mono,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (workflow.sizeKb.isNotBlank()) Text(workflow.sizeKb, fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onToggleFavorite) {
                Text(
                    if (favorite) "★" else "☆",
                    fontSize = 19.sp,
                    color = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
