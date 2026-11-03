package com.comfyremote.app.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.CloudHealth
import com.comfyremote.app.data.cloud.CloudHealthStatus
import com.comfyremote.app.data.cloud.CloudModelCapabilities
import com.comfyremote.app.data.cloud.CloudProvider
import com.comfyremote.app.data.cloud.CloudProviderSettingsStore
import com.comfyremote.app.data.cloud.CustomTemplateRepository
import com.comfyremote.app.data.cloud.TemplateValues
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BannerTone
import com.comfyremote.app.ui.components.BottomActionBar
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.CollapsibleCard
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.InfoBanner
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisProgressBar
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.IrisTonalButton
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dirty by remember(provider.id) { mutableStateOf(false) }
    var busy by remember(provider.id) { mutableStateOf(false) }
    var health by remember(provider.id) { mutableStateOf<CloudHealth?>(null) }
    var note by remember(provider.id) { mutableStateOf<String?>(null) }
    var failure by remember(provider.id) { mutableStateOf<String?>(null) }
    var disabledModels by remember(provider.id) { mutableStateOf(settings.disabledModels(provider.id)) }
    var defaultModel by remember(provider.id) { mutableStateOf(settings.defaultModel(provider.id)) }
    var modelQuery by remember(provider.id) { mutableStateOf("") }
    var advancedOpen by remember(provider.id) { mutableStateOf(false) }
    var headersText by remember(provider.id) { mutableStateOf(settings.customHeadersRaw(provider.id)) }
    var pollSecondsText by remember(provider.id) {
        mutableStateOf(settings.pollIntervalSeconds(provider.id).toString())
    }
    var timeoutMinutesText by remember(provider.id) {
        mutableStateOf(settings.taskTimeoutMinutes(provider.id).toString())
    }
    var retryCountText by remember(provider.id) {
        mutableStateOf(settings.pollRetryCount(provider.id).toString())
    }
    val saveBeforeTest = stringResource(R.string.cloud_status_save_before_test)
    val headersInvalid = stringResource(R.string.cloud_provider_headers_invalid)
    val runtimeInvalid = stringResource(R.string.cloud_provider_runtime_invalid)
    val modelList = models.split(',', '\n').map(String::trim).filter(String::isNotBlank).distinct()
    val visibleModels = modelList.filter { modelQuery.isBlank() || it.contains(modelQuery, true) }

    IrisScaffold(
        topBar = { DetailTopBar(title = provider.label, onBack = onBack) },
        bottomBar = {
            BottomActionBar {
                IrisPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        val pollSeconds = pollSecondsText.trim().toIntOrNull()
                        val timeoutMinutes = timeoutMinutesText.trim().toIntOrNull()
                        val retryCount = retryCountText.trim().toIntOrNull()
                        val runtimeOk = pollSeconds != null && pollSeconds in 2..60 &&
                            timeoutMinutes != null && timeoutMinutes in 1..240 &&
                            retryCount != null && retryCount in 0..5
                        val headersOk = !provider.supportsCustomHeaders ||
                            settings.saveCustomHeaders(provider.id, headersText)
                        if (!runtimeOk || !headersOk) {
                            saved = false
                            failure = if (!headersOk) headersInvalid else runtimeInvalid
                        } else {
                            settings.save(
                                provider.id,
                                baseUrl,
                                token,
                                models.split(',', '\n').map(String::trim).filter(String::isNotBlank),
                            )
                            settings.saveRuntimeSettings(
                                provider.id,
                                pollSeconds,
                                timeoutMinutes,
                                retryCount,
                            )
                            if (isTemplate) settings.saveExtra(provider.id, configText)
                            headersText = settings.customHeadersRaw(provider.id)
                            saved = true
                            dirty = false
                            health = null
                            note = null
                            failure = null
                        }
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
                    onValueChange = { baseUrl = it; saved = false; dirty = true },
                    label = stringResource(R.string.cloud_provider_base_url),
                    supportingText = stringResource(R.string.cloud_provider_base_url_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                    InfoBanner(
                        text = stringResource(R.string.cloud_provider_insecure),
                        tone = BannerTone.Warning,
                    )
                }
            }
            IrisTextField(
                value = token,
                onValueChange = { token = it; saved = false; dirty = true },
                label = stringResource(R.string.cloud_provider_api_key),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isTemplate) {
                IrisTextField(
                    value = configText,
                    onValueChange = { configText = it; saved = false; dirty = true; inferNote = null },
                    label = stringResource(R.string.cloud_template_config),
                    supportingText = stringResource(R.string.cloud_template_config_hint),
                    minLines = 10,
                    maxLines = 24,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = sampleRequest,
                    onValueChange = { sampleRequest = it; dirty = true; inferNote = null },
                    label = stringResource(R.string.cloud_template_sample_request),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = sampleResponse,
                    onValueChange = { sampleResponse = it; dirty = true; inferNote = null },
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
                        dirty = true
                    },
                )
                inferNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                IrisTextField(
                    value = models,
                    onValueChange = { models = it; saved = false; dirty = true },
                    label = stringResource(R.string.cloud_provider_models),
                    supportingText = stringResource(R.string.cloud_provider_models_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!isTemplate && modelList.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.cloud_provider_models_manage),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.cloud_provider_models_manage_hint),
                            style = IrisType.metaSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (disabledModels.isNotEmpty()) {
                        TextAction(
                            text = stringResource(R.string.cloud_provider_models_enable_all),
                            onClick = {
                                settings.setAllModelsEnabled(provider.id)
                                disabledModels = emptySet()
                            },
                        )
                    }
                }
                if (modelList.size > 6) {
                    IrisTextField(
                        value = modelQuery,
                        onValueChange = { modelQuery = it },
                        label = stringResource(R.string.cloud_provider_models_search),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                visibleModels.take(60).forEach { model ->
                    val enabled = model !in disabledModels
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                settings.setModelEnabled(provider.id, model, checked)
                                disabledModels = settings.disabledModels(provider.id)
                                if (!checked && defaultModel == model) {
                                    settings.setDefaultModel(provider.id, "")
                                    defaultModel = ""
                                }
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                model,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                CloudModelCapabilities.of(model)
                                    .map { capabilityLabel(it) }
                                    .joinToString(" · "),
                                style = IrisType.metaSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextAction(
                            text = if (defaultModel == model) stringResource(R.string.cloud_provider_models_default)
                            else stringResource(R.string.cloud_provider_models_set_default),
                            enabled = enabled,
                            onClick = {
                                if (defaultModel == model) {
                                    settings.setDefaultModel(provider.id, "")
                                    defaultModel = ""
                                } else {
                                    settings.setDefaultModel(provider.id, model)
                                    defaultModel = model
                                }
                            },
                        )
                    }
                }
                if (visibleModels.size > 60) {
                    Text(
                        stringResource(R.string.cloud_provider_models_more),
                        style = IrisType.metaSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IrisTonalButton(
                    text = stringResource(R.string.cloud_provider_test),
                    onClick = {
                        busy = true
                        failure = null
                        note = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { provider.check() }
                            health = result
                            busy = false
                        }
                    },
                    enabled = !busy && !dirty,
                )
                if (!isTemplate) {
                    IrisTonalButton(
                        text = stringResource(R.string.cloud_provider_refresh_models),
                        onClick = {
                            busy = true
                            failure = null
                            note = null
                            scope.launch {
                                val discovered = runCatching {
                                    withContext(Dispatchers.IO) { provider.discoverModels() }
                                }.getOrElse { error ->
                                    failure = error.message ?: error.javaClass.simpleName
                                    busy = false
                                    return@launch
                                }
                                if (discovered == null) {
                                    note = context.getString(R.string.cloud_status_discovery_unsupported)
                                } else if (discovered.isEmpty()) {
                                    note = context.getString(R.string.cloud_status_discovery_empty)
                                } else {
                                    models = discovered.joinToString(", ")
                                    dirty = true
                                    health = null
                                    note = context.getString(R.string.cloud_status_discovered, discovered.size)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy && !dirty,
                        icon = Icons.Outlined.Refresh,
                    )
                }
            }
            if (dirty) {
                Text(
                    saveBeforeTest,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) IrisProgressBar(progress = null)
            failure?.let { InfoBanner(text = it, tone = BannerTone.Error) }
            health?.let { result ->
                when (result.status) {
                    CloudHealthStatus.READY -> InfoBanner(
                        text = context.getString(R.string.cloud_status_ok, result.modelCount),
                        tone = BannerTone.Success,
                    )
                    CloudHealthStatus.CONFIGURED_UNVERIFIED -> InfoBanner(
                        text = stringResource(R.string.cloud_status_unverified_detail),
                        tone = BannerTone.Warning,
                    )
                    CloudHealthStatus.NOT_CONFIGURED -> InfoBanner(
                        text = stringResource(R.string.cloud_provider_not_configured),
                        tone = BannerTone.Warning,
                    )
                    else -> InfoBanner(
                        text = result.message.ifBlank {
                            context.getString(R.string.cloud_status_error)
                        },
                        tone = BannerTone.Error,
                    )
                }
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            CollapsibleCard(
                title = stringResource(R.string.cloud_provider_advanced),
                expanded = advancedOpen,
                onToggle = { advancedOpen = !advancedOpen },
            ) {
                Text(
                    stringResource(R.string.cloud_provider_capabilities),
                    style = IrisType.metaSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val capabilities = mutableListOf<String>()
                if (provider.supportsModelDiscovery) {
                    capabilities += stringResource(R.string.cloud_provider_cap_model_discovery)
                }
                if (provider.supportsCustomHeaders) {
                    capabilities += stringResource(R.string.cloud_provider_cap_custom_headers)
                }
                capabilities += stringResource(R.string.cloud_provider_cap_local_spend)
                Text(
                    capabilities.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (provider.supportsCustomHeaders) {
                    IrisTextField(
                        value = headersText,
                        onValueChange = { headersText = it; saved = false; dirty = true },
                        label = stringResource(R.string.cloud_provider_headers),
                        supportingText = stringResource(R.string.cloud_provider_headers_hint),
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.cloud_provider_runtime),
                    style = MaterialTheme.typography.titleSmall,
                )
                IrisTextField(
                    value = pollSecondsText,
                    onValueChange = { pollSecondsText = it; saved = false; dirty = true },
                    label = stringResource(R.string.cloud_provider_poll_interval),
                    supportingText = stringResource(R.string.cloud_provider_poll_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = timeoutMinutesText,
                    onValueChange = { timeoutMinutesText = it; saved = false; dirty = true },
                    label = stringResource(R.string.cloud_provider_task_timeout),
                    supportingText = stringResource(R.string.cloud_provider_timeout_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = retryCountText,
                    onValueChange = { retryCountText = it; saved = false; dirty = true },
                    label = stringResource(R.string.cloud_provider_retry_count),
                    supportingText = stringResource(R.string.cloud_provider_retry_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

@Composable
private fun capabilityLabel(tag: String): String = stringResource(
    when (tag) {
        CloudModelCapabilities.VIDEO -> R.string.cloud_cap_video
        CloudModelCapabilities.EDIT -> R.string.cloud_cap_edit
        CloudModelCapabilities.IMAGE_TO_VIDEO -> R.string.cloud_cap_i2v
        CloudModelCapabilities.TEXT_TO_VIDEO -> R.string.cloud_cap_t2v
        CloudModelCapabilities.AUDIO -> R.string.cloud_cap_audio
        else -> R.string.cloud_cap_image
    },
)
