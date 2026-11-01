package com.comfyremote.app.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("软件更新", "Software update"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) } },
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(L10n.t("当前版本 v", "Current version v") + state.currentVersion, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                state.token,
                viewModel::updateToken,
                label = { Text(L10n.t("GitHub Token（可选）", "GitHub Token (optional)")) },
                supportingText = { Text(L10n.t("公开仓库无需填写", "Not needed for a public repository")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = viewModel::check,
                enabled = !state.checking && !state.downloading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (state.checking) L10n.t("检查中…", "Checking…") else L10n.t("检查更新", "Check updates")) }

            state.release?.let { release ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(release.name.ifBlank { release.tag }, fontWeight = FontWeight.Bold)
                        Text(release.tag, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
                        release.notes.takeIf(String::isNotBlank)?.let {
                            Text(it.take(1600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                        }
                        if (state.downloading) {
                            LinearProgressIndicator(progress = { state.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                            Text("${((state.progress ?: 0f) * 100).toInt()}%", fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Button(onClick = viewModel::downloadAndInstall, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(L10n.t("下载并安装", "Download & install")) }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(release.browserUrl)) }) { Text(L10n.t("复制下载链接", "Copy download link")) }
                        }
                    }
                }
            }
            state.status?.let {
                Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                L10n.t("Android 首次覆盖安装时可能请求“允许安装未知应用”。", "Android may request permission to install unknown apps on the first in-app update."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
