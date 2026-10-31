package com.comfyremote.app.feature.ai


import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.comfyremote.app.ui.ComfyTheme
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.comfyremote.app.*

@Composable
fun AiSettingsScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    var editingSkill by remember { mutableStateOf<AiSkillDef?>(null) }
    var addingSkill by remember { mutableStateOf(false) }
    var thinkingOpen by remember { mutableStateOf(false) }
    val thinkingLabels = mapOf(
        "default" to L10n.t("跟随模型默认", "Model default"),
        "glm_off" to L10n.t("GLM · 关闭思考", "GLM · thinking off"),
        "glm_on" to L10n.t("GLM · 开启思考", "GLM · thinking on"),
        "low" to "reasoning: low",
        "medium" to "reasoning: medium",
        "high" to "reasoning: high",
    )
    Column(
        Modifier.fillMaxSize().imePadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(L10n.t("AI 提示词扩写", "AI prompt expansion"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            L10n.t("兼容 OpenAI 接口格式。示例：DeepSeek https://api.deepseek.com/v1 · 智谱 https://open.bigmodel.cn/api/paas/v4 · OpenAI https://api.openai.com/v1 · 本地 Ollama http://电脑IP:11434/v1", "OpenAI-compatible endpoints. e.g. DeepSeek https://api.deepseek.com/v1 · Zhipu https://open.bigmodel.cn/api/paas/v4 · OpenAI https://api.openai.com/v1 · local Ollama http://PC-IP:11434/v1"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = s.aiBase, onValueChange = vm::updateAiBase,
            label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.aiKey, onValueChange = vm::updateAiKey,
            label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.aiModel, onValueChange = vm::updateAiModel,
            label = { Text(L10n.t("模型名（如 deepseek-chat / glm-4-flash）", "Model (e.g. deepseek-chat / glm-4-flash)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        // ---- generation tunables ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = s.aiTemp,
                onValueChange = { vm.updateAiTemp(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text(L10n.t("温度 0~2", "Temperature 0-2")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = s.aiMaxTok,
                onValueChange = { vm.updateAiMaxTok(it) },
                label = { Text(L10n.t("最大输出 tokens", "Max output tokens")) },
                supportingText = { Text(L10n.t("空=不限", "empty = uncapped"), fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        // thinking mode dropdown
        Box {
            OutlinedButton(onClick = { thinkingOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(L10n.t("思考模式：", "Thinking mode: ") + (thinkingLabels[s.aiThinking] ?: s.aiThinking), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = thinkingOpen, onDismissRequest = { thinkingOpen = false }) {
                thinkingLabels.forEach { (k, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { vm.applyThinkingMode(k); thinkingOpen = false })
                }
            }
        }
        OutlinedTextField(
            value = s.aiExtra,
            onValueChange = { vm.updateAiExtra(it) },
            label = { Text(L10n.t("附加请求参数（JSON，可选）", "Extra request params (JSON, optional)")) },
            supportingText = { Text(L10n.t("会合并进请求体顶层，如 {\"top_p\":0.9}；思考模式的字段也写在这里", "Merged into the request body, e.g. {\"top_p\":0.9}; thinking-mode fields live here too")) },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        // ---- skills management ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.t("扩写技能（", "Skills (") + "${s.skills.size}" + L10n.t("）", ")"), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { addingSkill = true }) { Text(L10n.t("＋ 新增", "+ New")) }
        }
        s.skills.forEach { skill ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { editingSkill = skill }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(skill.label, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    if (skill.builtin) {
                        Text(L10n.t("内置", "built-in"), fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(L10n.t("编辑 ›", "Edit ›"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.testAi() },
                enabled = !s.aiTesting,
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text(if (s.aiTesting) L10n.t("测试中…", "Testing…") else L10n.t("测试连接", "Test connection")) }
            Button(
                onClick = { vm.saveAiSettings() },
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text(L10n.t("保存", "Save")) }
        }
        s.aiTest?.let {
            Text(
                it,
                color = if (it.startsWith(L10n.t("成功", "OK"))) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            L10n.t("Key 与技能仅保存在手机本地。思考模式对不支持的 API 会报错——用「跟随模型默认」即可。", "Key and skills stay on this device. Thinking mode may error on APIs that do not support it - keep Model default."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    editingSkill?.let { skill ->
        SkillEditDialog(
            skill = skill,
            onDismiss = { editingSkill = null },
            onSave = { vm.saveSkill(it); editingSkill = null },
            onDelete = if (!skill.builtin) ({ vm.deleteSkill(skill.id); editingSkill = null }) else null,
            onReset = if (skill.builtin) ({ vm.resetSkill(skill.id); editingSkill = null }) else null,
        )
    }
    if (addingSkill) {
        SkillEditDialog(
            skill = null,
            onDismiss = { addingSkill = false },
            onSave = { vm.addSkill(it.label, it.systemPrompt); addingSkill = false },
            onDelete = null,
            onReset = null,
        )
    }
}

@Composable
fun SkillEditDialog(
    skill: AiSkillDef?,
    onDismiss: () -> Unit,
    onSave: (AiSkillDef) -> Unit,
    onDelete: (() -> Unit)?,
    onReset: (() -> Unit)?,
) {
    var label by remember(skill) { mutableStateOf(skill?.label ?: "") }
    var prompt by remember(skill) { mutableStateOf(skill?.systemPrompt ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) L10n.t("新增技能", "New skill") else L10n.t("编辑技能", "Edit skill"), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(L10n.t("按钮名称", "Button label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(L10n.t("系统提示词（技能的内容）", "System prompt (the skill content)")) },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("提示词写你想要的行为即可，内容自己负责。", "Describe the behavior you want; you are responsible for the content."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank() && prompt.isNotBlank()) onSave(AiSkillDef(skill?.id ?: "custom_${System.currentTimeMillis()}", label.trim(), prompt, skill?.builtin ?: false)) },
                enabled = label.isNotBlank() && prompt.isNotBlank()
            ) { Text(L10n.t("保存", "Save")) }
        },
        dismissButton = {
            Row {
                if (onReset != null) TextButton(onClick = onReset) { Text(L10n.t("恢复默认", "Reset")) }
                if (onDelete != null) TextButton(onClick = onDelete) { Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(L10n.t("取消", "Cancel")) }
            }
        }
    )
}

// ============================ UPDATE ============================

