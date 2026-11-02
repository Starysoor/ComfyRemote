package com.comfyremote.app.feature.storyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.data.storyboard.StoryboardProject
import com.comfyremote.app.data.storyboard.StoryboardShot
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.ui.L10n
import com.comfyremote.app.ui.Mono
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryboardScreen(
    viewModel: StoryboardViewModel,
    onOpenProject: (String) -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    val workflows by viewModel.workflows.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(L10n.t("分镜", "Storyboard"), fontWeight = FontWeight.Bold)
                        Text(
                            L10n.t("多镜头云端视频项目", "Multi-shot cloud video projects"),
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, L10n.t("新建分镜", "New storyboard")) }
                },
            )
        }
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    L10n.t("还没有分镜项目", "No storyboards yet"),
                    L10n.t("用多个镜头串起一段云端视频；上一镜尾帧会自动成为下一镜参考图", "Chain cloud video shots; the previous last frame becomes the next reference"),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    StoryboardCard(
                        project = project,
                        workflowName = workflows.firstOrNull { it.id == project.workflowId }?.name
                            ?: project.workflowId,
                        onClick = { onOpenProject(project.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        CreateStoryboardDialog(
            workflows = workflows,
            onDismiss = { creating = false },
            onCreate = { title, workflowId, style ->
                val project = viewModel.createProject(title, workflowId, style)
                creating = false
                project?.let { onOpenProject(it.id) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryboardDetailScreen(
    viewModel: StoryboardViewModel,
    projectId: String,
    onBack: () -> Unit,
    onOpenShot: (String, String) -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    val workflows by viewModel.workflows.collectAsState()
    val message by viewModel.message.collectAsState()
    val project = projects.firstOrNull { it.id == projectId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addingShot by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(project?.title ?: L10n.t("分镜", "Storyboard"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L10n.t("返回", "Back")) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, L10n.t("更多", "More")) }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(L10n.t("删除项目", "Delete project")) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.deleteProject(projectId)
                                    onBack()
                                },
                            )
                        }
                    }
                },
            )
        }
    ) { pad ->
        if (project == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(L10n.t("项目不存在", "Storyboard not found"))
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("meta") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            workflows.firstOrNull { it.id == project.workflowId }?.name ?: project.workflowId,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            L10n.t(
                                "${project.shots.count { it.status == "done" }} / ${project.shots.size} 镜完成",
                                "${project.shots.count { it.status == "done" }} / ${project.shots.size} shots done",
                            ),
                            fontFamily = Mono,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedTextField(
                            value = project.stylePrompt,
                            onValueChange = { viewModel.setStyle(project.id, it) },
                            label = { Text(L10n.t("统一风格提示词（会拼接到每个镜头前）", "Shared style prompt (prefixed to every shot)")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.nextShot(project.id)?.let { shot ->
                                        viewModel.openShot(project.id, shot.id)?.let { (path, name) ->
                                            onOpenShot(path, name)
                                        }
                                    } ?: run {
                                        scope.launch { snackbar.showSnackbar(L10n.t("所有镜头都已完成", "All shots are done")) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(L10n.t("生成下一镜", "Generate next shot")) }
                            OutlinedButton(onClick = { addingShot = true }, modifier = Modifier.weight(1f)) {
                                Text(L10n.t("添加镜头", "Add shot"))
                            }
                        }
                    }
                }
            }

            item("shots-heading") { SectionHeader(L10n.t("镜头", "Shots")) }

            if (project.shots.isEmpty()) {
                item("empty") {
                    EmptyState(
                        L10n.t("还没有镜头", "No shots yet"),
                        L10n.t("先添加第一个镜头描述", "Add the first shot prompt"),
                    )
                }
            } else {
                itemsIndexed(project.shots, key = { _, shot -> shot.id }) { index, shot ->
                    ShotCard(
                        index = index,
                        shot = shot,
                        onOpen = {
                            viewModel.openShot(project.id, shot.id)?.let { (path, name) ->
                                onOpenShot(path, name)
                            }
                        },
                        onDelete = { viewModel.deleteShot(project.id, shot.id) },
                    )
                }
            }
        }
    }

    if (addingShot) {
        AddShotDialog(
            onDismiss = { addingShot = false },
            onAdd = { prompt ->
                viewModel.addShot(projectId, prompt)
                addingShot = false
            },
        )
    }
}

@Composable
private fun StoryboardCard(project: StoryboardProject, workflowName: String, onClick: () -> Unit) {
    val done = project.shots.count { it.status == "done" }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(project.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(workflowName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("$done/${project.shots.size}")
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(project.updatedAt)),
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShotCard(
    index: Int,
    shot: StoryboardShot,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusLabel = when (shot.status) {
        "done" -> L10n.t("完成", "DONE")
        "running" -> L10n.t("生成中", "RUNNING")
        "failed" -> L10n.t("失败", "FAILED")
        else -> L10n.t("待生成", "DRAFT")
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(
                    shot.prompt.ifBlank { L10n.t("（空镜头）", "(empty shot)") },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(statusLabel, active = shot.status == "running", error = shot.status == "failed")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val reference = shot.referencePath ?: shot.lastFramePath
                if (reference != null) {
                    MediaThumbnail(reference, 44.dp)
                }
                shot.resultPath?.let { result -> MediaThumbnail(result, 44.dp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpen) {
                    Text(if (shot.status == "done") L10n.t("重新生成", "Regenerate") else L10n.t("准备生成", "Prepare"))
                }
                TextButton(onClick = onDelete) { Text(L10n.t("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
            if (shot.error != null) {
                Text(shot.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun CreateStoryboardDialog(
    workflows: List<CloudWorkflowSummary>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var workflowId by remember {
        mutableStateOf(
            workflows.firstOrNull { it.id == "minimax_h3_lightx2v_v5" }?.id
                ?: workflows.firstOrNull()?.id.orEmpty()
        )
    }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("新建分镜", "New storyboard")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(L10n.t("项目名称", "Title")) }, singleLine = true)
                OutlinedTextField(style, { style = it }, label = { Text(L10n.t("统一风格提示词（可选）", "Shared style prompt (optional)")) }, minLines = 2, maxLines = 4)
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            workflows.firstOrNull { it.id == workflowId }?.name ?: L10n.t("选择云端视频工作流", "Choose cloud video workflow"),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("▾")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        workflows.forEach { workflow ->
                            DropdownMenuItem(
                                text = { Text(workflow.name) },
                                onClick = { workflowId = workflow.id; menu = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, workflowId, style) }, enabled = workflowId.isNotBlank()) {
                Text(L10n.t("创建", "Create"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.t("取消", "Cancel")) } },
    )
}

@Composable
private fun AddShotDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("添加镜头", "Add shot")) },
        text = {
            OutlinedTextField(
                prompt,
                { prompt = it },
                label = { Text(L10n.t("镜头描述", "Shot prompt")) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(prompt) }, enabled = prompt.isNotBlank()) { Text(L10n.t("添加", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.t("取消", "Cancel")) } },
    )
}
