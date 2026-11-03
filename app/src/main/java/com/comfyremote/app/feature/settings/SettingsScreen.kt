package com.comfyremote.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.comfyremote.app.R
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.ScreenContentPadding
import com.comfyremote.app.ui.components.SettingRow
import com.comfyremote.app.ui.components.SettingsGroup

@Composable
fun SettingsScreen(
    version: String,
    onBack: () -> Unit,
    onApp: () -> Unit,
    onServers: () -> Unit,
    onAi: () -> Unit,
    onCloud: () -> Unit,
    onUpdate: () -> Unit,
) {
    IrisScaffold(
        topBar = { DetailTopBar(title = stringResource(R.string.action_settings), onBack = onBack) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_connections)) {
                SettingRow(
                    title = stringResource(R.string.settings_servers),
                    detail = stringResource(R.string.settings_servers_detail),
                    icon = Icons.Outlined.Dns,
                    onClick = onServers,
                )
                SettingRow(
                    title = stringResource(R.string.settings_cloud),
                    detail = stringResource(R.string.settings_cloud_detail),
                    icon = Icons.Outlined.Cloud,
                    onClick = onCloud,
                    divider = false,
                )
            }
            SettingsGroup(title = stringResource(R.string.settings_group_ai_creation)) {
                SettingRow(
                    title = stringResource(R.string.settings_ai),
                    detail = stringResource(R.string.settings_ai_detail),
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = onAi,
                )
                SettingRow(
                    title = stringResource(R.string.settings_app),
                    detail = stringResource(R.string.settings_app_detail),
                    icon = Icons.Outlined.Tune,
                    onClick = onApp,
                    divider = false,
                )
            }
            SettingsGroup(title = stringResource(R.string.settings_group_about)) {
                SettingRow(
                    title = stringResource(R.string.settings_update),
                    detail = stringResource(R.string.settings_update_detail),
                    icon = Icons.Outlined.SystemUpdate,
                    onClick = onUpdate,
                )
                SettingRow(
                    title = stringResource(R.string.settings_version),
                    icon = Icons.Outlined.Info,
                    value = stringResource(R.string.settings_version_value, version),
                    divider = false,
                )
            }
        }
    }
}
