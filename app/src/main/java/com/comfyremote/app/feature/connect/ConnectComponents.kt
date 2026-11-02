package com.comfyremote.app.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.ServerEntry
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.IrisProgressBar
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.ListRow
import com.comfyremote.app.ui.components.StatusDot

@Composable
internal fun ServerAddressField(url: String, onUrlChange: (String) -> Unit) {
    IrisTextField(
        value = url,
        onValueChange = onUrlChange,
        label = stringResource(R.string.connect_server_address),
        supportingText = stringResource(R.string.connect_server_address_hint),
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IconTile(icon: ImageVector) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(9.dp).size(20.dp))
    }
}

/**
 * A saved server. Tapping fills the address field; the trailing button opens the
 * rename / delete dialog. [statusColor] switches the leading visual to a status dot.
 */
@Composable
internal fun SavedServerRow(
    server: ServerEntry,
    onClick: () -> Unit,
    onManage: () -> Unit,
    statusColor: Color? = null,
) {
    ListRow(
        title = server.name.ifBlank { server.url },
        leading = {
            if (statusColor != null) StatusDot(statusColor, size = 10.dp) else IconTile(Icons.Outlined.Dns)
        },
        meta = if (server.name != server.url) {
            {
                Text(
                    server.url,
                    style = IrisType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else null,
        trailing = {
            IconButton(onClick = onManage) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.connect_manage_server),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
internal fun DiscoveredServerRow(found: ServerEntry, onClick: () -> Unit) {
    ListRow(
        title = stringResource(R.string.connect_found),
        leading = { IconTile(Icons.Outlined.Wifi) },
        meta = {
            Text(found.url, style = IrisType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        onClick = onClick,
    )
}

/** Progress, results and errors of a LAN scan. The trigger button lives with the caller. */
@Composable
internal fun ScanResults(state: ConnectUiState, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                IrisProgressBar(
                    progress = (state.scanProgress.toFloat() / state.scanTotal.coerceAtLeast(1)).coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${state.scanProgress}/${state.scanTotal}",
                    style = IrisType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.scanFound.forEach { found ->
            DiscoveredServerRow(found) { onPick(found.url) }
        }
        state.scanError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ManageServerDialog(
    server: ServerEntry,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var alias by remember(server) { mutableStateOf(server.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_manage_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(server.url, style = IrisType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IrisTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = stringResource(R.string.connect_alias),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(alias); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onRemove(); onDismiss() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
