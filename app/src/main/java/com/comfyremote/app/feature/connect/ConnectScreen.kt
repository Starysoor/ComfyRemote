package com.comfyremote.app.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
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
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.ui.Iris
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BannerTone
import com.comfyremote.app.ui.components.InfoBanner
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisSecondaryButton
import com.comfyremote.app.ui.components.IrisTonalButton
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.TextAction

/** First-run screen: brand hero, server address and the ways in (LAN scan, saved, cloud only). */
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onConnected: () -> Unit,
    onCloudOnly: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var managing by remember { mutableStateOf<ServerEntry?>(null) }
    LaunchedEffect(viewModel) { viewModel.connectedEvents.collect { onConnected() } }

    IrisScaffold { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ConnectHero()

            ServerAddressField(url = state.url, onUrlChange = viewModel::setUrl)
            IrisPrimaryButton(
                text = stringResource(if (state.connecting) R.string.connect_connecting else R.string.connect_connect),
                onClick = { viewModel.connect() },
                loading = state.connecting,
            )
            state.error?.let { InfoBanner(text = it, tone = BannerTone.Error) }

            IrisTonalButton(
                text = stringResource(if (state.scanning) R.string.connect_scanning else R.string.connect_find_nearby),
                onClick = viewModel::scanLan,
                enabled = !state.scanning,
                icon = Icons.Outlined.Wifi,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            ScanResults(state = state, onPick = viewModel::setUrl)

            if (state.servers.isNotEmpty()) {
                SectionHeader(stringResource(R.string.connect_saved))
                state.servers.forEach { server ->
                    SavedServerRow(
                        server = server,
                        onClick = { viewModel.setUrl(server.url) },
                        onManage = { managing = server },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))
            IrisSecondaryButton(
                text = stringResource(R.string.connect_cloud_only),
                onClick = onCloudOnly,
                icon = Icons.Outlined.CloudQueue,
            )
            TextAction(
                stringResource(R.string.action_settings),
                onClick = onSettings,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
private fun ConnectHero() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(brush = Iris.colors.brand),
        )
        Text(
            stringResource(R.string.connect_tagline),
            style = IrisType.overline,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.connect_tagline_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
