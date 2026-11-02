package com.comfyremote.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comfyremote.app.data.repository.AppSettings
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("应用与界面", "App & interface"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back"))
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(L10n.t("外观", "Appearance")) {
                SettingsChoice(
                    title = L10n.t("主题", "Theme"),
                    detail = L10n.t("浅色、深色或跟随系统", "Light, dark or system"),
                    options = listOf(
                        "system" to L10n.t("跟随系统", "System"),
                        "light" to L10n.t("浅色", "Light"),
                        "dark" to L10n.t("深色", "Dark"),
                    ),
                    selected = settings.themeMode,
                    onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
                )
                SettingsSwitch(
                    title = L10n.t("紧凑界面", "Compact layout"),
                    detail = L10n.t("减少卡片留白，让列表显示更多内容", "Less card padding, more content on screen"),
                    checked = settings.compactUi,
                    onChange = { value -> viewModel.update { it.copy(compactUi = value) } },
                )
                SettingsStepper(
                    title = L10n.t("最近结果数量", "Recent results"),
                    detail = L10n.t("创作页显示最近结果的数量（0-4）", "How many recent results appear on Create (0-4)"),
                    value = settings.recentResultsCount,
                    range = 0..4,
                    onChange = { value -> viewModel.update { it.copy(recentResultsCount = value) } },
                )
                SettingsStepper(
                    title = L10n.t("最近预设数量", "Recent setups"),
                    detail = L10n.t("创作页显示最近整套预设的数量（0-4）", "How many recent setups appear on Create (0-4)"),
                    value = settings.recentPresetsCount,
                    range = 0..4,
                    onChange = { value -> viewModel.update { it.copy(recentPresetsCount = value) } },
                )
                SettingsSwitch(
                    title = L10n.t("显示服务器工作台", "Show server workspace"),
                    detail = L10n.t("在创作页顶部显示服务器切换条", "Show server switcher on the Create screen"),
                    checked = settings.showServerWorkspace,
                    onChange = { value -> viewModel.update { it.copy(showServerWorkspace = value) } },
                )
            }

            SettingsSection(L10n.t("详情页折叠状态", "Detail sections")) {
                SettingsSwitch(
                    title = L10n.t("默认展开工作流说明", "Workflow notes open by default"),
                    detail = L10n.t("MarkdownNote 说明是否默认展开", "Whether MarkdownNote notes start expanded"),
                    checked = settings.notesExpandedByDefault,
                    onChange = { value -> viewModel.update { it.copy(notesExpandedByDefault = value) } },
                )
                SettingsSwitch(
                    title = L10n.t("默认展开高级参数", "Advanced params open by default"),
                    detail = L10n.t("步数、CFG、采样器等高级参数是否默认展开", "Whether steps/CFG/sampler params start expanded"),
                    checked = settings.advancedExpandedByDefault,
                    onChange = { value -> viewModel.update { it.copy(advancedExpandedByDefault = value) } },
                )
            }

            SettingsSection(L10n.t("创作默认值", "Creation defaults")) {
                SettingsStepper(
                    title = L10n.t("默认批量数量", "Default batch count"),
                    detail = L10n.t("新工作流默认生成几张（1-16）", "Default variations for a new workflow (1-16)"),
                    value = settings.defaultBatchCount,
                    range = 1..16,
                    onChange = { value -> viewModel.update { it.copy(defaultBatchCount = value) } },
                )
                SettingsSwitch(
                    title = L10n.t("默认使用本地队列", "Use local queue by default"),
                    detail = L10n.t("批量任务逐个发送，避免塞满服务器", "Submit batch jobs one at a time"),
                    checked = settings.defaultLocalQueue,
                    onChange = { value -> viewModel.update { it.copy(defaultLocalQueue = value) } },
                )
                SettingsSwitch(
                    title = L10n.t("默认随机种子", "Random seed by default"),
                    detail = L10n.t("关闭后保留工作流内的 seed", "Off keeps the workflow's seed"),
                    checked = settings.defaultRandomSeed,
                    onChange = { value -> viewModel.update { it.copy(defaultRandomSeed = value) } },
                )
                SettingsSwitch(
                    title = L10n.t("自动保存草稿", "Auto-save drafts"),
                    detail = L10n.t("按工作流与服务器记住上次填写的参数", "Remember last values per workflow and server"),
                    checked = settings.autoSaveDrafts,
                    onChange = { value -> viewModel.update { it.copy(autoSaveDrafts = value) } },
                )
                SettingsChoice(
                    title = L10n.t("上传图片最大边长", "Max upload dimension"),
                    detail = L10n.t("大于该边长的参考图会等比压缩", "Larger reference images are downscaled"),
                    options = listOf(
                        "1024" to "1024 px",
                        "1536" to "1536 px",
                        "2048" to "2048 px",
                    ),
                    selected = settings.maxImageDimension.toString(),
                    onSelect = { value ->
                        viewModel.update { it.copy(maxImageDimension = value.toIntOrNull() ?: 1536) }
                    },
                )
            }

            SettingsSection(L10n.t("云端提交", "Cloud submissions")) {
                SettingsSwitch(
                    title = L10n.t("提交前确认费用", "Confirm price before submit"),
                    detail = L10n.t("显示预计平台计价并二次确认，避免误扣费", "Show the estimated platform price and ask for confirmation"),
                    checked = settings.confirmCloudSubmit,
                    onChange = { value -> viewModel.update { it.copy(confirmCloudSubmit = value) } },
                )
            }

            SettingsSection(L10n.t("任务与通知", "Tasks & notifications")) {
                SettingsSwitch(
                    title = L10n.t("完成后通知", "Notify when finished"),
                    detail = L10n.t("生成完成、失败或取消时发系统通知", "System notification on finish/fail/cancel"),
                    checked = settings.notifyOnFinish,
                    onChange = { value -> viewModel.update { it.copy(notifyOnFinish = value) } },
                )
            }

            SettingsSection(L10n.t("存储", "Storage")) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(L10n.t("云端输入图片缓存", "Cloud input cache"), fontWeight = FontWeight.SemiBold)
                            Text(
                                L10n.t("清除云端工作流使用的参考图副本", "Remove reference-image copies used by cloud workflows"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = viewModel::clearCloudInputs) { Text(L10n.t("清除", "Clear")) }
                    }
                }
            }

            Text(
                "IRIS · ${L10n.t("设置即时生效", "Settings apply immediately")}",
                fontFamily = Mono,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SectionHeader(title)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsChoice(
    title: String,
    detail: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val active = value == selected
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSelect(value) },
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsStepper(
    title: String,
    detail: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Text("−", modifier = Modifier.size(width = 16.dp, height = 16.dp))
        }
        Text("$value", fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Text("+")
        }
    }
}
