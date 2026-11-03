package com.comfyremote.app.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.CloudProvider
import com.comfyremote.app.data.cloud.CloudProviderSettingsStore
import com.comfyremote.app.data.cloud.CustomTemplateRepository
import com.comfyremote.app.data.cloud.TemplateValues
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BottomActionBar
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.TextAction

@Composable
fun CloudProviderSettingsScreen(
    provider: CloudProvider,
    settings: CloudProviderSettingsStore,
    onBack: () -> Unit,
) {
    val account = remember(provider.id) {
        settings.account(provider.id, provider.baseUrl, provider.defaultModels)
    }
    var baseUrl by remember(provider.id) { mutableStateOf(account.baseUrl) }
    var token by remember(provider.id) { mutableStateOf(account.token) }
    var models by remember(provider.id) { mutableStateOf(account.models.joinToString(", ")) }
    var saved by remember(provider.id) { mutableStateOf(false) }
    val isTemplate = provider.id == CustomTemplateRepository.PROVIDER
    var configText by remember(provider.id) { mutableStateOf(settings.extra(provider.id)) }
    var sampleRequest by remember(provider.id) { mutableStateOf("") }
    var sampleResponse by remember(provider.id) { mutableStateOf("") }
    var inferNote by remember(provider.id) { mutableStateOf<String?>(null) }
    val inferredText = stringResource(R.string.cloud_template_inferred)

    IrisScaffold(
        topBar = { DetailTopBar(title = provider.label, onBack = onBack) },
        bottomBar = {
            BottomActionBar {
                IrisPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        settings.save(
                            provider.id,
                            baseUrl,
                            token,
                            models.split(',', '\n').map(String::trim).filter(String::isNotBlank),
                        )
                        if (isTemplate) settings.saveExtra(provider.id, configText)
                        saved = true
                    },
                )
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                provider.settingsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isTemplate) {
                IrisTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    label = stringResource(R.string.cloud_provider_base_url),
                    supportingText = stringResource(R.string.cloud_provider_base_url_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IrisTextField(
                value = token,
                onValueChange = { token = it; saved = false },
                label = stringResource(R.string.cloud_provider_api_key),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isTemplate) {
                IrisTextField(
                    value = configText,
                    onValueChange = { configText = it; saved = false; inferNote = null },
                    label = stringResource(R.string.cloud_template_config),
                    supportingText = stringResource(R.string.cloud_template_config_hint),
                    minLines = 10,
                    maxLines = 24,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = sampleRequest,
                    onValueChange = { sampleRequest = it; inferNote = null },
                    label = stringResource(R.string.cloud_template_sample_request),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = sampleResponse,
                    onValueChange = { sampleResponse = it; inferNote = null },
                    label = stringResource(R.string.cloud_template_sample_response),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextAction(
                    text = stringResource(R.string.cloud_template_infer),
                    onClick = {
                        val inferred = TemplateValues.inferConfig(sampleRequest, sampleResponse).toString(2)
                        configText = inferred
                        inferNote = inferredText
                        saved = false
                    },
                )
                inferNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                IrisTextField(
                    value = models,
                    onValueChange = { models = it; saved = false },
                    label = stringResource(R.string.cloud_provider_models),
                    supportingText = stringResource(R.string.cloud_provider_models_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (saved) {
                Text(
                    stringResource(R.string.ai_saved),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
