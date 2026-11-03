package com.comfyremote.app.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.CloudProviderInfo
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.ListRow
import com.comfyremote.app.ui.components.StatusChip

@Composable
fun CloudServicesScreen(
    providers: List<CloudProviderInfo>,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    IrisScaffold(
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.cloud_services_title),
                onBack = onBack,
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
            items(providers, key = { it.id }) { provider ->
                ListRow(
                    title = provider.label,
                    subtitle = provider.summary,
                    meta = {
                        StatusChip(
                            stringResource(
                                if (provider.configured) R.string.cloud_provider_configured
                                else R.string.cloud_provider_not_configured
                            ),
                            tone = if (provider.configured) ChipTone.Success else ChipTone.Warning,
                        )
                    },
                    onClick = { onOpenProvider(provider.id) },
                )
            }
        }
    }
}

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
