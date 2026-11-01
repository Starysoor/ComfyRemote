package com.comfyremote.app.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    var advanced by rememberSaveable { mutableStateOf(false) }
    var thinkingMenu by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<AiSkillDef?>(null) }
    var addingSkill by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("AI 设置", "AI settings"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) } },
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                L10n.t("兼容 OpenAI 风格 /chat/completions 接口", "Works with OpenAI-compatible /chat/completions endpoints"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(settings.baseUrl, viewModel::updateBase, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.apiKey, viewModel::updateKey, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.model, viewModel::updateModel, label = { Text(L10n.t("模型", "Model")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Card {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("高级参数", "Advanced"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(if (advanced) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), color = MaterialTheme.colorScheme.primary)
                    }
                    if (advanced) Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                settings.temperatureText,
                                viewModel::updateTemp,
                                label = { Text(L10n.t("温度", "Temperature")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                settings.maxTokensText,
                                viewModel::updateMaxTokens,
                                label = { Text("Max tokens") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box {
                            OutlinedButton(onClick = { thinkingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(thinkingLabel(settings.thinkingMode), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                                Text("▾")
                            }
                            DropdownMenu(thinkingMenu, { thinkingMenu = false }) {
                                listOf("default", "glm_off", "glm_on", "low", "medium", "high").forEach { mode ->
                                    DropdownMenuItem(text = { Text(thinkingLabel(mode)) }, onClick = { viewModel.applyThinkingMode(mode); thinkingMenu = false })
                                }
                            }
                        }
                        OutlinedTextField(
                            settings.extraJson,
                            viewModel::updateExtra,
                            label = { Text(L10n.t("附加 JSON", "Extra JSON")) },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L10n.t("提示词技能（", "Prompt skills (") + state.skills.size + L10n.t("）", ")"), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { addingSkill = true }) { Text(L10n.t("＋新增", "+ New")) }
            }
            state.skills.forEach { skill ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { editingSkill = skill },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.label, fontWeight = FontWeight.SemiBold)
                        if (skill.builtin) {
                            Spacer(Modifier.width(8.dp))
                            Text(L10n.t("内置", "BUILT-IN"), fontFamily = Mono, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::test, enabled = !state.testing, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(if (state.testing) L10n.t("测试中…", "Testing…") else L10n.t("测试连接", "Test"))
                }
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f).height(50.dp)) { Text(L10n.t("保存", "Save")) }
            }
            state.testResult?.let {
                Text(it, color = if (it.startsWith(L10n.t("成功", "OK"))) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    editingSkill?.let { skill ->
        SkillEditDialog(
            skill = skill,
            onDismiss = { editingSkill = null },
            onSave = { viewModel.saveSkill(it); editingSkill = null },
            onDelete = if (!skill.builtin) ({ viewModel.deleteSkill(skill.id); editingSkill = null }) else null,
            onReset = if (skill.builtin) ({ viewModel.resetSkill(skill.id); editingSkill = null }) else null,
        )
    }
    if (addingSkill) {
        SkillEditDialog(
            skill = null,
            onDismiss = { addingSkill = false },
            onSave = { viewModel.addSkill(it.label, it.systemPrompt); addingSkill = false },
            onDelete = null,
            onReset = null,
        )
    }
}

@Composable
private fun SkillEditDialog(
    skill: AiSkillDef?,
    onDismiss: () -> Unit,
    onSave: (AiSkillDef) -> Unit,
    onDelete: (() -> Unit)?,
    onReset: (() -> Unit)?,
) {
    var label by remember(skill) { mutableStateOf(skill?.label.orEmpty()) }
    var prompt by remember(skill) { mutableStateOf(skill?.systemPrompt.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) L10n.t("新增技能", "New skill") else L10n.t("编辑技能", "Edit skill")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(L10n.t("名称", "Label")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prompt, { prompt = it }, label = { Text(L10n.t("系统提示词", "System prompt")) }, minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(AiSkillDef(skill?.id ?: "custom_${System.currentTimeMillis()}", label.trim(), prompt, skill?.builtin ?: false)) },
                enabled = label.isNotBlank() && prompt.isNotBlank(),
            ) { Text(L10n.t("保存", "Save")) }
        },
        dismissButton = {
            Row {
                onReset?.let { TextButton(onClick = it) { Text(L10n.t("恢复默认", "Reset")) } }
                onDelete?.let { TextButton(onClick = it) { Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text(L10n.t("取消", "Cancel")) }
            }
        },
    )
}

private fun thinkingLabel(mode: String): String = when (mode) {
    "glm_off" -> L10n.t("GLM · 关闭思考", "GLM · thinking off")
    "glm_on" -> L10n.t("GLM · 开启思考", "GLM · thinking on")
    "low" -> "reasoning: low"
    "medium" -> "reasoning: medium"
    "high" -> "reasoning: high"
    else -> L10n.t("跟随模型默认", "Model default")
}
