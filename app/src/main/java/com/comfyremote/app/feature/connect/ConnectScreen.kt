package com.comfyremote.app.feature.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onConnected: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.connectedEvents.collect { onConnected() } }

    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Default.Settings, L10n.t("设置", "Settings"))
        }
        ServerEditor(
            state = state,
            viewModel = viewModel,
            onboarding = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagerScreen(
    viewModel: ConnectViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.connectedEvents.collect { onConnected() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("服务器", "Servers"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back"))
                    }
                }
            )
        }
    ) { pad ->
        ServerEditor(
            state = state,
            viewModel = viewModel,
            onboarding = false,
            modifier = Modifier.padding(pad).fillMaxSize(),
        )
    }
}

@Composable
private fun ServerEditor(
    state: ConnectUiState,
    viewModel: ConnectViewModel,
    onboarding: Boolean,
    modifier: Modifier,
) {
    var managing by remember { mutableStateOf<ServerEntry?>(null) }
    Column(
        modifier.imePadding().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = if (onboarding) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onboarding) {
            Text("ComfyRemote", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                "COMFYUI · REMOTE CONTROL",
                fontFamily = Mono,
                fontSize = 10.sp,
                letterSpacing = 2.3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(30.dp))
        } else {
            state.session?.let { session ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.device.ifBlank { session.url }, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${session.url} · ComfyUI ${session.version}${session.vramGb.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}",
                                fontFamily = Mono,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::setUrl,
            label = { Text(L10n.t("服务器地址", "Server address")) },
            supportingText = {
                Text(L10n.t("局域网 / Tailscale 地址，ComfyUI 需允许远程监听", "LAN / Tailscale address; ComfyUI must listen remotely"))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.connect() },
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.connecting) L10n.t("连接中…", "Connecting…") else L10n.t("连接", "Connect"))
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (state.servers.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                L10n.t("已保存", "Saved"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.servers.forEach { server ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { viewModel.setUrl(server.url) },
                            onLongClick = { managing = server },
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp))
                            if (server.name != server.url) Text(
                                server.url,
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 190.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = viewModel::scanLan, enabled = !state.scanning) {
            Text(if (state.scanning) L10n.t("扫描局域网…", "Scanning LAN…") else L10n.t("查找附近的 ComfyUI", "Find nearby ComfyUI"))
        }
        if (state.scanning) {
            LinearProgressIndicator(
                progress = { (state.scanProgress.toFloat() / state.scanTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${state.scanProgress}/${state.scanTotal}",
                fontFamily = Mono,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.scanFound.forEach { found ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { viewModel.setUrl(found.url) },
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(L10n.t("发现 ComfyUI", "ComfyUI found"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(found.url, fontFamily = Mono, fontSize = 10.sp)
                    }
                }
            }
        }
        state.scanError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    managing?.let { server ->
        var alias by remember(server) { mutableStateOf(server.name) }
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(L10n.t("管理服务器", "Manage server")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(server.url, fontFamily = Mono, fontSize = 10.sp)
                    OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text(L10n.t("别名", "Alias")) })
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameServer(server.url, alias); managing = null }) { Text(L10n.t("保存", "Save")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.removeServer(server.url); managing = null }) {
                        Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { managing = null }) { Text(L10n.t("取消", "Cancel")) }
                }
            }
        )
    }
}
