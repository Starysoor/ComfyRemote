package com.comfyremote.app.feature.storyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.cloud.CloudWorkflowSummary
import com.comfyremote.app.data.storyboard.StoryboardProject
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.IrisTonalButton
import com.comfyremote.app.ui.components.LargeTopBar
import com.comfyremote.app.ui.components.ListRow
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ProjectThumbSize = 56.dp

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

    IrisScaffold(
        snackbarHostState = snackbar,
        topBar = {
            LargeTopBar(
                title = stringResource(R.string.board_title),
                subtitle = stringResource(R.string.board_subtitle, projects.size),
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.board_new))
                    }
                },
            )
        },
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.board_empty_title),
                    detail = stringResource(R.string.board_empty_detail),
                    icon = Icons.Outlined.Movie,
                    action = {
                        IrisTonalButton(
                            text = stringResource(R.string.board_new),
                            onClick = { creating = true },
                            icon = Icons.Outlined.Add,
                        )
                    },
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(projects, key = { it.id }) { project ->
                    StoryboardProjectRow(
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

@Composable
private fun StoryboardProjectRow(project: StoryboardProject, workflowName: String, onClick: () -> Unit) {
    val done = project.shots.count { it.status == "done" }
    val total = project.shots.size
    val tone = when {
        project.shots.any { it.status == "running" } -> ChipTone.Accent
        project.shots.any { it.status == "failed" } -> ChipTone.Error
        total > 0 && done == total -> ChipTone.Success
        else -> ChipTone.Neutral
    }
    val cover = project.shots.lastOrNull { it.resultPath != null }?.resultPath
        ?: project.shots.lastOrNull { it.lastFramePath != null }?.lastFramePath
    ListRow(
        title = project.title,
        subtitle = workflowName,
        onClick = onClick,
        leading = { ProjectCover(cover) },
        meta = {
            StatusChip("$done/$total", tone = tone)
            Text(
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(project.updatedAt)),
                style = IrisType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun ProjectCover(path: String?) {
    if (path != null) {
        MediaThumbnail(path, ProjectThumbSize, Modifier.clip(MaterialTheme.shapes.medium))
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(ProjectThumbSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IrisTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.board_field_title),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = style,
                    onValueChange = { style = it },
                    label = stringResource(R.string.board_field_style_optional),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                WorkflowPicker(
                    workflows = workflows,
                    selectedId = workflowId,
                    onSelect = { workflowId = it },
                )
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.board_create),
                onClick = { onCreate(title, workflowId, style) },
                enabled = workflowId.isNotBlank(),
            )
        },
        dismissButton = {
            TextAction(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Field-like dropdown for picking the cloud video workflow. */
@Composable
private fun WorkflowPicker(
    workflows: List<CloudWorkflowSummary>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { menu = true },
        ) {
            Row(
                Modifier.padding(start = Spacing.lg, end = Spacing.md, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workflows.firstOrNull { it.id == selectedId }?.name
                        ?: stringResource(R.string.board_choose_workflow),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(menu, { menu = false }) {
            workflows.forEach { workflow ->
                DropdownMenuItem(
                    text = { Text(workflow.name) },
                    onClick = { onSelect(workflow.id); menu = false },
                )
            }
        }
    }
}
