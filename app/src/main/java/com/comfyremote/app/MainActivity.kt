package com.comfyremote.app

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

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComfyTheme {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when (s.screen) {
            Screen.CONNECT -> ConnectScreen(vm, s)
            Screen.AI_SETTINGS -> AiSettingsScreen(vm, s)
            Screen.UPDATE -> UpdateScreen(vm, s)
            Screen.WORKFLOWS -> WorkflowsScreen(vm, s)
            Screen.GENERATE -> GenerateScreen(vm, s)
            Screen.HISTORY -> HistoryScreen(vm, s)
        }
        s.busy?.let { msg ->
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(msg)
                    }
                }
            }
        }
    }
}

// ============================ CONNECT ============================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectScreen(vm: MainViewModel, s: UiState) {
    var url by rememberSaveable(s.url) { mutableStateOf(s.url) }
    var managing by remember { mutableStateOf<ServerEntry?>(null) }
    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 28.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ComfyRemote", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "COMFYUI · REMOTE CONTROL",
            fontFamily = Mono, fontSize = 10.sp, letterSpacing = 2.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(L10n.t("服务器地址", "Server address")) },
            supportingText = { Text(L10n.t("模拟器: http://10.0.2.2:8188；手机: Tailscale IP 或电脑局域网 IP（ComfyUI 需 --listen 0.0.0.0）", "Emulator: http://10.0.2.2:8188; phone: Tailscale/LAN IP (ComfyUI needs --listen 0.0.0.0)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (s.servers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                s.servers.forEach { srv ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { url = srv.url },
                            onLongClick = { managing = srv }
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                srv.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            if (srv.name != srv.url) {
                                Text(
                                    srv.url, fontFamily = Mono, fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                L10n.t("长按可重命名或删除", "Long-press to rename or remove"),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.connect(url) },
            enabled = !s.connecting,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (s.connecting) L10n.t("连接中…", "Connecting…") else L10n.t("连  接", "Connect"), letterSpacing = 1.sp) }
        Spacer(Modifier.height(14.dp))
        s.connError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        s.sysInfo?.let { info ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(info, style = MaterialTheme.typography.bodySmall, fontFamily = Mono, fontSize = 11.sp)
                }
            }
        }
        // update banner
        s.update?.release?.let { rel ->
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { vm.openUpdateScreen() }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("新版本 ${rel.tag} 可用", "New version ${rel.tag} available"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    Text(L10n.t("更新 →", "Update →"), color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = Mono, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TextButton(onClick = { vm.openAiSettings() }) {
                Text(L10n.t("AI 设置", "AI settings"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { vm.openUpdateScreen() }) {
                Text(L10n.t("检查更新", "Check updates"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "v${vm.currentVersionPublic()}",
            fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
    managing?.let { srv ->
        var alias by remember(srv) { mutableStateOf(srv.name) }
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(L10n.t("管理服务器", "Manage server"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(srv.url, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(L10n.t("别名", "Alias")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (alias.isNotBlank()) vm.renameServer(srv.url, alias.trim())
                    managing = null
                }) { Text(L10n.t("重命名", "Rename")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.removeServer(srv.url)
                        managing = null
                    }) { Text(L10n.t("删除", "Remove"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { managing = null }) { Text(L10n.t("取消", "Cancel")) }
                }
            }
        )
    }
}

// ============================ AI SETTINGS ============================

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

@Composable
fun UpdateScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    val context = LocalContext.current
    val u = s.update
    Column(
        Modifier.fillMaxSize().imePadding().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(L10n.t("软件更新", "Software update"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(L10n.t("当前版本 v", "Current version v") + vm.currentVersionPublic(), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = s.ghToken,
            onValueChange = { vm.updateGhToken(it) },
            label = { Text(L10n.t("GitHub Token（可选）", "GitHub Token (optional)")) },
            supportingText = {
                Text(L10n.t("仓库公开时无需填写。若仍是私有：GitHub → Settings → Developer settings → Fine-grained tokens，仅授权 ComfyRemote 仓库、Contents:Read-only", "Not needed for a public repo. If still private: GitHub → Settings → Developer settings → Fine-grained tokens, scoped to ComfyRemote with Contents:Read-only"))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.checkUpdate() },
                enabled = u?.downloading != true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).height(50.dp)
            ) { Text(L10n.t("检查更新", "Check updates")) }
        }
        Spacer(Modifier.height(18.dp))
        when {
            u?.release != null -> {
                val rel = u.release
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(rel.name.ifEmpty { rel.tag }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(rel.tag, fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        if (rel.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                rel.notes.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        if (u.downloading) {
                            LinearProgressIndicator(progress = u.progress ?: 0f, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                (L10n.t("下载中 ", "Downloading ") + "${(u.progress ?: 0f) * 100}%").let { if (u.progress == null) L10n.t("下载中…", "Downloading…") else it },
                                fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Button(
                                onClick = { vm.downloadUpdate() },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text(L10n.t("下载并安装", "Download & install")) }
                            val clipboard = LocalClipboardManager.current
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(rel.browserUrl))
                                android.widget.Toast.makeText(context, L10n.t("链接已复制", "Link copied"), android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(L10n.t("复制下载链接（浏览器打开更快）", "Copy download link (faster in a browser)"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            u?.status != null -> Text(
                u.status,
                color = if (u.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            L10n.t("首次安装时系统会请求「允许安装未知应用」权限，同意一次即可。更新包签名与本应用一致，正常覆盖安装不会丢数据。", "Android will ask for the install-unknown-apps permission once. Updates share this app signature, so existing data is kept."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ============================ WORKFLOWS ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(L10n.t("工作流", "Workflows"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${s.workflows.size}", fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    TextButton(onClick = { vm.openHistory() }) {
                        Text(L10n.t("历史", "History"), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Text("${s.history.size}", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { vm.loadWorkflows() }) { Icon(Icons.Default.Refresh, L10n.t("刷新", "Refresh")) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loadingWf -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.wfError != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.wfError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadWorkflows() }) { Text(L10n.t("重试", "Retry")) }
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    items(s.workflows, key = { it.path }) { wf ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clickable { vm.openWorkflow(wf) }
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(wf.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (wf.sizeKb.isNotEmpty())
                                        Text(wf.sizeKb, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================ HISTORY ============================

fun decodeSampled(path: String, target: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, s: UiState) {
    BackHandler { vm.back() }
    var viewing by remember { mutableStateOf<HistoryItem?>(null) }
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("历史记录（", "History (") + "${s.history.size}" + L10n.t("）", ")")) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                }
            )
        }
    ) { pad ->
        if (s.history.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(L10n.t("还没有生成记录", "No generations yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp)) {
                items(s.history, key = { it.id }) { item ->
                    Card(
                        onClick = { viewing = item },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.images.firstOrNull()?.let { p ->
                                    val thumb by produceState<Bitmap?>(null, p) {
                                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 160) }
                                    }
                                    thumb?.let {
                                        Image(it.asImageBitmap(), null, Modifier.size(64.dp), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.wfName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · " + item.images.size + L10n.t(" 张", " image(s)"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { vm.reopenFromHistory(item) }) { Text(L10n.t("重新生成", "Re-generate")) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteHistoryItem(item.id) }) {
                                    Icon(Icons.Default.Delete, L10n.t("删除", "Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { item ->
        HistoryDetailDialog(item, onClose = { viewing = null }, onSave = { bmp -> vm.saveResult(bmp) }, onShare = { p -> shareImage(ctx, p) })
    }
}

@Composable
fun HistoryDetailDialog(item: HistoryItem, onClose: () -> Unit, onSave: (Bitmap) -> Unit, onShare: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        android.widget.Toast.makeText(ctx, L10n.t("已复制", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.wfName, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)) + " · " + item.images.size + L10n.t(" 张", " image(s)"),
                color = Color.White.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                item(key = "prompts") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L10n.t("提示词", "Prompts"), fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                if (item.prompts.isNotEmpty()) {
                                    TextButton(onClick = { copy(item.prompts.joinToString("\n") { it.value }) }) {
                                        Text(L10n.t("复制全部", "Copy all"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (item.prompts.isEmpty()) {
                                Text(L10n.t("（此记录未保存完整提示词）", "(No saved prompts for this entry)"), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                            }
                            item.prompts.forEach { p ->
                                Text(p.label, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(2.dp))
                                SelectionContainer {
                                    Text(p.value, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { copy(p.value) }) { Text(L10n.t("复制", "Copy"), style = MaterialTheme.typography.labelMedium) }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (item.seeds.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SEED", fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.seeds, fontFamily = Mono, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                                    TextButton(onClick = { copy(item.seeds) }) { Text(L10n.t("复制", "Copy"), style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                items(item.images.size, key = { "img$it" }) { idx ->
                    val p = item.images[idx]
                    val bmp by produceState<Bitmap?>(null, p) {
                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(p, 1600) }
                    }
                    bmp?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        Row {
                            Text(L10n.t("第 ", "Image ") + "${idx + 1}" + L10n.t(" 张", ""), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(8.dp))
                            TextButton(onClick = { onSave(it) }) { Text(L10n.t("保存到相册", "Save to gallery")) }
                            TextButton(onClick = { onShare(p) }) { Text(L10n.t("分享", "Share")) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ============================ GENERATE ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: MainViewModel, s: UiState) {
    val g = s.gen ?: return
    val ctx = LocalContext.current
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewingIdx by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val k = pendingKey
        pendingKey = null
        if (uri != null && k != null) vm.uploadImage(k, uri)
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.uploadImagesBatch(uris.toList())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        val k = pendingKey
        cameraUri = null
        if (ok && uri != null && k != null) vm.uploadImage(k, uri)
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    BackHandler { vm.back() }

    val imageFields = g.fields.filter { it.kind == FieldKind.IMAGE }
    val textFields = g.fields.filter { it.kind == FieldKind.TEXT }
    val seedFields = g.fields.filter { it.kind == FieldKind.SEED }
    val paramFields = g.fields.filter { it.kind == FieldKind.PARAM }
    var advOpen by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g.wfName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                }
            )
        },
        bottomBar = {
            if (g.running) {
                OutlinedButton(
                    onClick = { vm.cancelGenerate() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text(L10n.t("取消生成", "Cancel")) }
            } else {
                Button(
                    onClick = { vm.generate() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) { Text(L10n.t("生  成", "Generate"), fontSize = 16.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            g.error?.let { err ->
                item(key = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "status") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ((g.status ?: L10n.t("待生成", "Ready"))).uppercase(),
                                fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp,
                                color = if (g.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            g.queueInfo?.let {
                                Text(it, fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        g.progress?.let { p -> LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth().height(3.dp)) }
                        if (g.running && g.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                    }
                }
            }
            if (g.warnings.isNotEmpty()) {
                item(key = "warn") {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            g.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (g.notes.isNotEmpty()) {
                item(key = "notes") {
                    Card {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { notesOpen = !notesOpen }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(L10n.t("工作流说明", "Workflow notes"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (notesOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (notesOpen) {
                                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                                    g.notes.forEach { note ->
                                        Text(
                                            stripMarkdown(note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            g.preview?.let { pb ->
                item(key = "preview") {
                    Column {
                        Text(L10n.t("实时预览", "Live preview"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Image(pb.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
            if (imageFields.isNotEmpty()) {
                item(key = "imgheader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("参考图片", "Reference images"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        if (imageFields.size >= 2) {
                            TextButton(onClick = { multiPicker.launch("image/*") }) { Text(L10n.t("一次选多张", "Pick multiple")) }
                        }
                    }
                }
                itemsIndexed(imageFields, key = { _, f -> f.key }) { _, f ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(f.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val cur = g.values[f.key] ?: f.value
                            Text(L10n.t("当前：", "Current: ") + cur, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { pendingKey = f.key; picker.launch("image/*") }) { Text(L10n.t("选择图片", "Pick image")) }
                                OutlinedButton(onClick = {
                                    pendingKey = f.key
                                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                                    val f2 = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                    cameraUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f2)
                                    camera.launch(cameraUri!!)
                                }) { Text(L10n.t("拍照", "Camera")) }
                            }
                        }
                    }
                }
            }
            itemsIndexed(textFields, key = { _, f -> f.key }) { _, f ->
                Column {
                    OutlinedTextField(
                        value = g.values[f.key] ?: f.value,
                        onValueChange = { vm.updateValue(f.key, it) },
                        label = { Text(f.label) },
                        minLines = if (f.multiline) 3 else 1,
                        maxLines = if (f.multiline) 12 else 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (f.multiline) {
                        val skills = remember(g.wfName, s.skills) { AiSkillDef.recommendedOrder(s.skills, g.wfName) }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            skills.forEach { skill ->
                                val busyKey = "${f.key}|${skill.id}"
                                TextButton(
                                    onClick = { vm.aiExpand(f.key, g.values[f.key] ?: f.value, skill) },
                                    enabled = s.aiBusy == null && !g.running
                                ) {
                                    if (s.aiBusy == busyKey) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(L10n.t("生成中…", "Working…"))
                                    } else {
                                        Text(skill.label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (seedFields.isNotEmpty()) {
                item(key = "seeds") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g.randomSeed, onCheckedChange = { vm.toggleRandomSeed() })
                            Column {
                                Text(L10n.t("每次生成使用随机种子", "Randomize seed every run"))
                                if (g.randomSeed) Text(
                                    L10n.t("生成后自动回填上次种子，关闭即可复现", "Last seed is filled back in after each run; untick to reproduce"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        seedFields.forEach { f ->
                            OutlinedTextField(
                                value = g.values[f.key] ?: f.value,
                                onValueChange = { vm.updateValue(f.key, it.filter { c -> c.isDigit() }) },
                                label = { Text(f.label + (if (g.randomSeed) L10n.t("（本次将随机）", " (randomized)") else "")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            if (paramFields.isNotEmpty()) {
                item(key = "params") {
                    Card {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { advOpen = !advOpen }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(L10n.t("高级参数（", "Advanced (") + "${paramFields.size}" + L10n.t("）", ")"), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(if (advOpen) L10n.t("收起", "Hide") else L10n.t("展开", "Show"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (advOpen) {
                                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    paramFields.forEach { f -> ParamField(f, g.values[f.key] ?: f.value) { vm.updateValue(f.key, it) } }
                                }
                            }
                        }
                    }
                }
            }
            if (g.results.isNotEmpty()) {
                item(key = "resheader") {
                    Text(
                        L10n.t("生成结果（", "Results (") + "${g.results.size}" + L10n.t("）", ")"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(g.results, key = { i, _ -> "res$i" }) { idx, bmp ->
                    Column {
                        Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxWidth().clickable { viewingIdx = idx },
                            contentScale = ContentScale.FillWidth
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L10n.t("结果 ", "Result ") + "${idx + 1}" + L10n.t("（点击放大）", " (tap to zoom)"), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            val p = g.resultPaths.getOrNull(idx)
                            if (p != null) TextButton(onClick = { shareImage(ctx, p) }) {
                                Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(L10n.t("分享", "Share"))
                            }
                            TextButton(onClick = { vm.saveResult(bmp) }) { Text(L10n.t("保存到相册", "Save to gallery")) }
                        }
                    }
                }
            }
        }
    }
    viewingIdx?.let { idx ->
        g.results.getOrNull(idx)?.let { bmp ->
            ImageViewerDialog(
                bitmap = bmp,
                onClose = { viewingIdx = null },
                onSave = { vm.saveResult(bmp) },
                onShare = { g.resultPaths.getOrNull(idx)?.let { shareImage(ctx, it) } }
            )
        }
    }
    s.aiResult?.let { result ->
        AiResultDialog(
            initial = result.text,
            onDismiss = { vm.aiDismiss() },
            onReplace = { vm.aiApply(replace = true, it) },
            onAppend = { vm.aiApply(replace = false, it) }
        )
    }
}

@Composable
fun AiResultDialog(initial: String, onDismiss: () -> Unit, onReplace: (String) -> Unit, onAppend: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("AI 生成结果", "AI result"), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("可直接修改后再应用；「追加」会拼在原提示词后面", "Edit freely before applying; Append joins it onto the existing prompt"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onReplace(text) }) { Text(L10n.t("替换", "Replace")) } },
        dismissButton = {
            TextButton(onClick = { onAppend(text) }) { Text(L10n.t("追加", "Append")) }
        }
    )
}

@Composable
fun ParamField(f: PromptField, value: String, onChange: (String) -> Unit) {
    when (f.ptype) {
        "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value.equals("true", true), onCheckedChange = { onChange(if (it) "true" else "false") })
            Text(f.label)
        }
        "combo" -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(value.ifEmpty { L10n.t("选择…", "Choose…") }, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    f.options.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
                    }
                }
            }
            Text(f.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = {
                if (f.ptype == "int") onChange(it.filter { c -> c.isDigit() || c == '-' })
                else if (f.ptype == "float") onChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' })
                else onChange(it)
            },
            label = {
                val range = if (f.min != null || f.max != null) "（${f.min ?: "?"} ~ ${f.max ?: "?"}）" else ""
                Text(f.label + range)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (f.ptype) {
                    "int" -> KeyboardType.Number
                    "float" -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 轻量 Markdown 清理，用于展示工作流说明。 */
fun stripMarkdown(md: String): String = md
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace("**", "")
    .replace(Regex("`(.*?)`"), "$1")
    .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
    .trim()

// ============================ IMAGE VIEWER ============================

@Composable
fun ImageViewerDialog(bitmap: Bitmap, onClose: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Image(
                bitmap.asImageBitmap(), null,
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, L10n.t("分享", "Share"), tint = Color.White) }
                IconButton(onClick = onSave) { Icon(Icons.Default.Add, L10n.t("保存到相册", "Save"), tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, L10n.t("关闭", "Close"), tint = Color.White) }
            }
            Text(
                L10n.t("双击复位 · 双指缩放", "Double-tap to reset · pinch to zoom"),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

// ============================ SHARE ============================

fun shareImage(context: Context, path: String) {
    val file = File(path)
    if (!file.isFile) return
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, L10n.t("分享图片", "Share image")))
    }
}
