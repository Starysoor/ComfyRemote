package com.comfyremote.app.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.CloudHealthStatus
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.ListRow
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction

@Composable
fun CloudServicesScreen(
    viewModel: CloudServicesViewModel,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    IrisScaffold(
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.cloud_services_title),
                onBack = onBack,
                actions = {
                    TextAction(
                        text = stringResource(R.string.cloud_status_refresh),
                        onClick = viewModel::checkAll,
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item("spend") {
                IrisCard {
                    Column(
                        Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.cloud_spend_summary),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                        ) {
                            SpendStat(
                                label = stringResource(R.string.cloud_spend_today),
                                value = stringResource(R.string.cloud_spend_units, state.spendToday),
                            )
                            SpendStat(
                                label = stringResource(R.string.cloud_spend_month),
                                value = stringResource(R.string.cloud_spend_units, state.spendMonth),
                            )
                        }
                        Text(
                            stringResource(R.string.cloud_spend_summary_note),
                            style = IrisType.metaSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item("providers-heading") {
                SectionHeader(stringResource(R.string.cloud_providers))
            }
            items(state.providers, key = { it.id }) { provider ->
                val status = provider.health?.status
                ListRow(
                    title = provider.label,
                    subtitle = provider.summary,
                    meta = {
                        StatusChip(
                            cloudStatusLabel(status),
                            tone = cloudStatusTone(status),
                        )
                        provider.health?.takeIf { it.status == CloudHealthStatus.READY && it.modelCount > 0 }
                            ?.let {
                                Text(
                                    stringResource(R.string.cloud_status_models, it.modelCount),
                                    style = IrisType.metaSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        state.spendByProvider[provider.id]?.takeIf { it > 0 }?.let { spend ->
                            Text(
                                stringResource(R.string.cloud_spend_provider_month, spend),
                                style = IrisType.metaSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    footer = {
                        val failure = provider.health
                            ?.takeIf { it.message.isNotBlank() && it.status.isFailure() }
                        if (failure != null) {
                            Text(
                                failure.message,
                                style = IrisType.metaSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                            )
                        }
                    },
                    onClick = { onOpenProvider(provider.id) },
                )
            }
        }
    }
}

@Composable
private fun SpendStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = IrisType.metaSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun cloudStatusLabel(status: CloudHealthStatus?): String = stringResource(
    when (status) {
        null -> R.string.cloud_status_unknown
        CloudHealthStatus.UNKNOWN -> R.string.cloud_status_unknown
        CloudHealthStatus.CHECKING -> R.string.cloud_status_checking
        CloudHealthStatus.READY -> R.string.cloud_status_ready
        CloudHealthStatus.CONFIGURED_UNVERIFIED -> R.string.cloud_status_unverified
        CloudHealthStatus.NOT_CONFIGURED -> R.string.cloud_provider_not_configured
        CloudHealthStatus.AUTH_FAILED -> R.string.cloud_status_auth_failed
        CloudHealthStatus.UNREACHABLE -> R.string.cloud_status_unreachable
        CloudHealthStatus.ERROR -> R.string.cloud_status_error
    },
)

private fun cloudStatusTone(status: CloudHealthStatus?): ChipTone = when (status) {
    CloudHealthStatus.READY -> ChipTone.Success
    CloudHealthStatus.CONFIGURED_UNVERIFIED -> ChipTone.Warning
    CloudHealthStatus.NOT_CONFIGURED -> ChipTone.Warning
    CloudHealthStatus.AUTH_FAILED,
    CloudHealthStatus.UNREACHABLE,
    CloudHealthStatus.ERROR -> ChipTone.Error
    else -> ChipTone.Neutral
}

private fun CloudHealthStatus.isFailure(): Boolean =
    this == CloudHealthStatus.AUTH_FAILED ||
        this == CloudHealthStatus.UNREACHABLE ||
        this == CloudHealthStatus.ERROR

@Composable
fun CloudProviderPlaceholderScreen(
    title: String,
    onBack: () -> Unit,
) {
    IrisScaffold(
        topBar = { DetailTopBar(title = title, onBack = onBack) },
    ) { pad ->
        Text(
            text = stringResource(R.string.cloud_provider_unavailable),
            modifier = Modifier.padding(pad).fillMaxSize(),
        )
    }
}
