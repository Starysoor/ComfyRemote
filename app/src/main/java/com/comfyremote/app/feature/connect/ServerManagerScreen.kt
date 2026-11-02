package com.comfyremote.app.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.data.repository.ServerSession
import com.comfyremote.app.ui.Iris
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BannerTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.InfoBanner
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisTonalButton
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusDot

/** Pushed from Create / Settings: current session, address entry, LAN scan and saved servers. */
@Composable
fun ServerManagerScreen(
    viewModel: ConnectViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var managing by remember { mutableStateOf<ServerEntry?>(null) }
    LaunchedEffect(viewModel) { viewModel.connectedEvents.collect { onConnected() } }

    IrisScaffold(
        topBar = { DetailTopBar(title = stringResource(R.string.connect_servers), onBack = onBack) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.session?.let { session ->
                item("session") { SessionCard(session) }
            }
            item("address") { ServerAddressField(url = state.url, onUrlChange = viewModel::setUrl) }
            item("actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    IrisTonalButton(
                        text = stringResource(if (state.connecting) R.string.connect_connecting else R.string.connect_connect),
                        onClick = { viewModel.connect() },
                        enabled = !state.connecting,
                        icon = Icons.Outlined.Link,
                    )
                    IrisTonalButton(
                        text = stringResource(if (state.scanning) R.string.connect_scanning else R.string.connect_find_nearby),
                        onClick = viewModel::scanLan,
                        enabled = !state.scanning,
                        icon = Icons.Outlined.Wifi,
                    )
                }
            }
            state.error?.let { error ->
                item("error") { InfoBanner(text = error, tone = BannerTone.Error) }
            }
            item("scan") { ScanResults(state = state, onPick = viewModel::setUrl) }
            if (state.servers.isNotEmpty()) {
                item("saved-heading") { SectionHeader(stringResource(R.string.connect_saved)) }
                items(state.servers, key = { it.url }) { server ->
                    SavedServerRow(
                        server = server,
                        statusColor = if (state.session?.url == server.url) Iris.colors.success else MaterialTheme.colorScheme.outline,
                        onClick = { viewModel.setUrl(server.url) },
                        onManage = { managing = server },
                    )
                }
            }
        }
    }

    managing?.let { server ->
        ManageServerDialog(
            server = server,
            onDismiss = { managing = null },
            onRename = { viewModel.renameServer(server.url, it) },
            onRemove = { viewModel.removeServer(server.url) },
        )
    }
}

@Composable
private fun SessionCard(session: ServerSession) {
    IrisCard(highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusDot(Iris.colors.success, size = 10.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    session.device.ifBlank { session.url },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${session.url} · ComfyUI ${session.version}${session.vramGb.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}",
                    style = IrisType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
