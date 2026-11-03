package com.comfyremote.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.data.cloud.CloudHealthStatus
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BannerTone
import com.comfyremote.app.ui.components.BottomActionBar
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.InfoBanner
import com.comfyremote.app.ui.components.IrisDivider
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisProgressBar
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisSecondaryButton
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.IrisTonalButton
import com.comfyremote.app.ui.components.ScreenContentPadding
import com.comfyremote.app.ui.components.SettingRow
import com.comfyremote.app.ui.components.SettingsGroup
import com.comfyremote.app.ui.components.StatusChip

@Composable
fun CloudSettingsScreen(
    viewModel: CloudSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var videoMenu by remember { mutableStateOf(false) }
    var confirmClearSpend by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    val healthFallback = stringResource(R.string.cloud_status_error)

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    IrisScaffold(
        snackbarHostState = snackbar,
        topBar = { DetailTopBar(title = stringResource(R.string.settings_cloud), onBack = onBack) },
        bottomBar = {
            BottomActionBar {
                IrisPrimaryButton(
                    text = stringResource(R.string.settings_cloud_save_token),
                    onClick = viewModel::saveToken,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // AutoDL.Art account: token, catalogue refresh.
            SettingsGroup(
                title = stringResource(R.string.settings_cloud_autodl),
                footer = stringResource(R.string.settings_cloud_token_hint),
            ) {
                SettingRow(
                    title = stringResource(R.string.settings_cloud_hosted),
                    trailing = {
                        StatusChip(
                            if (state.configured) stringResource(R.string.settings_cloud_ready)
                            else stringResource(R.string.settings_cloud_no_token),
                            tone = if (state.configured) ChipTone.Success else ChipTone.Warning,
                        )
                    },
                )
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    IrisTextField(
                        value = state.token,
                        onValueChange = viewModel::updateToken,
                        label = stringResource(R.string.settings_cloud_token_label),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IrisTonalButton(
                            text = stringResource(R.string.settings_cloud_refresh),
                            onClick = viewModel::refresh,
                            enabled = !state.loading,
                            icon = Icons.Outlined.Refresh,
                        )
                        Text(
                            pluralStringResource(R.plurals.settings_cloud_found, state.workflows.size, state.workflows.size),
                            style = IrisType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        IrisTonalButton(
                            text = stringResource(R.string.cloud_provider_test),
                            onClick = viewModel::testConnection,
                            enabled = !state.testing,
                        )
                        if (state.testing) IrisProgressBar(progress = null)
                    }
                    state.health?.let { result ->
                        when (result.status) {
                            CloudHealthStatus.READY -> InfoBanner(
                                text = stringResource(R.string.cloud_status_ok, result.modelCount),
                                tone = BannerTone.Success,
                            )
                            CloudHealthStatus.NOT_CONFIGURED -> InfoBanner(
                                text = stringResource(R.string.cloud_provider_not_configured),
                                tone = BannerTone.Warning,
                            )
                            else -> InfoBanner(
                                text = result.message.ifBlank { healthFallback },
                                tone = BannerTone.Error,
                            )
                        }
                    }
                    if (state.loading) IrisProgressBar(progress = null)
                    state.error?.let { InfoBanner(text = it, tone = BannerTone.Error) }
                    Text(
                        stringResource(R.string.settings_cloud_audio_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Default video workflow used by "Make video".
            SettingsGroup(
                title = stringResource(R.string.settings_cloud_default_video),
                footer = stringResource(R.string.settings_cloud_default_video_detail),
            ) {
                Box {
                    SettingRow(
                        title = state.workflows.firstOrNull { it.id == state.defaultVideoId }?.name
                            ?: state.defaultVideoId.ifBlank { stringResource(R.string.settings_cloud_choose_workflow) },
                        onClick = if (state.workflows.isNotEmpty()) ({ videoMenu = true }) else null,
                        trailing = {
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (state.workflows.isNotEmpty()) 1f else 0.4f,
                                ),
                            )
                        },
                        divider = false,
                    )
                    DropdownMenu(videoMenu, { videoMenu = false }) {
                        state.workflows.forEach { workflow ->
                            DropdownMenuItem(
                                text = { Text(workflow.name) },
                                onClick = {
                                    viewModel.setDefaultVideo(workflow.id)
                                    videoMenu = false
                                },
                            )
                        }
                    }
                }
                IrisDivider(Modifier.padding(start = Spacing.lg))
                Text(
                    "${AutoDlConfig.PATH_PREFIX}${state.defaultVideoId}",
                    style = IrisType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }

            // Local spend estimate.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SettingsGroup(title = stringResource(R.string.settings_cloud_spend)) {
                    SettingRow(
                        title = stringResource(R.string.settings_cloud_spend_units),
                        value = state.spendTotal.toString(),
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_cloud_spend_count),
                        value = state.spendCount.toString(),
                        divider = false,
                    )
                }
                InfoBanner(text = stringResource(R.string.settings_cloud_spend_note), tone = BannerTone.Warning)
                IrisSecondaryButton(
                    text = stringResource(R.string.settings_cloud_spend_clear),
                    onClick = { confirmClearSpend = true },
                    destructive = true,
                    icon = Icons.Outlined.DeleteOutline,
                    height = 48.dp,
                )
            }

            // Cache.
            SettingsGroup(title = stringResource(R.string.settings_cloud_cache)) {
                SettingRow(
                    title = stringResource(R.string.settings_cloud_clear_refs),
                    onClick = { confirmClearCache = true },
                    trailing = {
                        Text(
                            stringResource(R.string.action_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = false,
                )
            }
        }
    }

    if (confirmClearSpend) {
        AlertDialog(
            onDismissRequest = { confirmClearSpend = false },
            title = { Text(stringResource(R.string.cloud_clear_spend_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_clear_spend_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSpend()
                    confirmClearSpend = false
                }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSpend = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.cloud_clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_clear_cache_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCloudInputs()
                    confirmClearCache = false
                }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
