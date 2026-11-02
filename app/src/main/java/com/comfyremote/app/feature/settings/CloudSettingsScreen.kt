package com.comfyremote.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.data.cloud.AutoDlConfig
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    viewModel: CloudSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var videoMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("云端服务", "Cloud services"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back"))
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(L10n.t("AutoDL.Art", "AutoDL.Art"))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("托管 ComfyUI 工作流", "Hosted ComfyUI workflows"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusChip(
                            if (state.configured) L10n.t("已配置", "READY") else L10n.t("未配置", "TOKEN"),
                            active = state.configured,
                        )
                    }
                    Text(
                        L10n.t(
                            "Token 在 autodl.art/large-model/tokens 创建，分组必须选择 ComfyUI。调用按次计费。",
                            "Create a token at autodl.art/large-model/tokens with the ComfyUI group. Calls are billed per run.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.token,
                        onValueChange = viewModel::updateToken,
                        label = { Text("AutoDL Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::saveToken, modifier = Modifier.weight(1f)) {
                            Text(L10n.t("保存 Token", "Save token"))
                        }
                        OutlinedButton(onClick = viewModel::refresh, enabled = !state.loading, modifier = Modifier.weight(1f)) {
                            if (state.loading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(6.dp))
                            }
                            Text(L10n.t("刷新目录", "Refresh catalogue"))
                        }
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        L10n.t("已发现 ${state.workflows.size} 个视频工作流", "${state.workflows.size} video workflows found"),
                        fontFamily = Mono,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        L10n.t("云端目录同时包含音频/TTS 工作流，可在创作页切换", "The cloud catalogue also includes audio/TTS workflows"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(L10n.t("默认视频工作流", "Default video workflow"))
            Text(
                L10n.t(
                    "在结果页点“生成视频”时使用。输入图片会自动作为首帧/参考图传给云端。",
                    "Used by the result page's “Make video” action. The image is passed as the first frame/reference.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { videoMenu = true },
                    enabled = state.workflows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        state.workflows.firstOrNull { it.id == state.defaultVideoId }?.name
                            ?: state.defaultVideoId.ifBlank { L10n.t("选择工作流", "Choose a workflow") },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("▾")
                }
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
            Text(
                "${AutoDlConfig.PATH_PREFIX}${state.defaultVideoId}",
                fontFamily = Mono,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(L10n.t("本地预估消耗", "Local spend estimate"))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L10n.t(
                                "累计 ${state.spendTotal} 平台单位 · ${state.spendCount} 次提交",
                                "${state.spendTotal} platform units · ${state.spendCount} submissions",
                            ),
                            fontFamily = Mono,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearSpend) { Text(L10n.t("清除", "Clear")) }
                    }
                    Text(
                        L10n.t(
                            "仅本地预估，不是账单；实际扣费以 AutoDL 为准。",
                            "Local estimate only, not a bill; AutoDL is authoritative.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(L10n.t("缓存", "Cache"))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { viewModel.clearCloudInputs() },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L10n.t("清除云端参考图副本", "Clear cloud reference copies"),
                        modifier = Modifier.weight(1f),
                    )
                    Text(L10n.t("清除", "Clear"), color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}
