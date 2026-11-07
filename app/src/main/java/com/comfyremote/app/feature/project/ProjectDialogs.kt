package com.comfyremote.app.feature.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.comfyremote.app.data.project.VideoProject
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.IrisSwitch
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.TextAction

/** Field-like dropdown for picking the cloud video workflow. */
@Composable
internal fun WorkflowPicker(
    workflows: List<ProjectWorkflowOption>,
    selectedPath: String,
    onSelect: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    workflows.firstOrNull { it.path == selectedPath }?.let {
                        "${it.providerLabel} · ${it.name}"
                    } ?: stringResource(R.string.board_choose_workflow),
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
                    text = { Text("${workflow.providerLabel} · ${workflow.name}") },
                    onClick = { onSelect(workflow.path); menu = false },
                )
            }
        }
    }
}

@Composable
internal fun CreateProjectDialog(
    workflows: List<ProjectWorkflowOption>,
    onDismiss: () -> Unit,
    onCreate: (title: String, workflowPath: String, style: String, synopsis: String, chainFrames: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var synopsis by remember { mutableStateOf("") }
    var chainFrames by remember { mutableStateOf(true) }
    var workflowPath by remember {
        mutableStateOf(
            workflows.firstOrNull { it.workflowId == "minimax_h3_lightx2v_v5" }?.path
                ?: workflows.firstOrNull()?.path.orEmpty()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proj_new)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IrisTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.board_field_title),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = stringResource(R.string.proj_field_synopsis),
                    minLines = 2,
                    maxLines = 4,
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
                    selectedPath = workflowPath,
                    onSelect = { workflowPath = it },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.board_chain_frames),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.board_chain_frames_hint),
                            style = IrisType.metaSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IrisSwitch(
                        checked = chainFrames,
                        onCheckedChange = { chainFrames = it },
                    )
                }
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.board_create),
                onClick = { onCreate(title, workflowPath, style, synopsis, chainFrames) },
                enabled = workflowPath.isNotBlank(),
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

@Composable
internal fun ProjectSettingsDialog(
    project: VideoProject,
    workflows: List<ProjectWorkflowOption>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        synopsis: String,
        workflowPath: String,
        aspectRatio: String,
        defaults: Map<String, String>,
        targetDurationSeconds: Int?,
    ) -> Unit,
) {
    var title by remember(project.title) { mutableStateOf(project.title) }
    var synopsis by remember(project.synopsis) { mutableStateOf(project.synopsis) }
    var workflowPath by remember(project.workflowPath) {
        mutableStateOf(project.workflowPath.ifBlank { project.workflowId })
    }
    var aspectRatio by remember(project.aspectRatio) { mutableStateOf(project.aspectRatio) }
    var durationText by remember(project.targetDurationSeconds) {
        mutableStateOf(project.targetDurationSeconds?.toString().orEmpty())
    }
    var defaultsText by remember(project.defaultValues) {
        mutableStateOf(project.defaultValues.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    var workflowMenu by remember { mutableStateOf(false) }
    val workflowName = workflows.firstOrNull {
        it.path == workflowPath || it.workflowId == workflowPath
    }?.let { "${it.providerLabel} · ${it.name}" } ?: workflowPath

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_project_settings)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IrisTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.board_project_title),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = stringResource(R.string.proj_field_synopsis),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    TextAction(
                        text = workflowName.ifBlank { stringResource(R.string.board_project_workflow) },
                        onClick = { workflowMenu = true },
                    )
                    DropdownMenu(workflowMenu, { workflowMenu = false }) {
                        workflows.forEach { wf ->
                            DropdownMenuItem(
                                text = { Text("${wf.providerLabel} · ${wf.name}") },
                                onClick = {
                                    workflowPath = wf.path
                                    workflowMenu = false
                                },
                            )
                        }
                    }
                }
                IrisTextField(
                    value = aspectRatio,
                    onValueChange = { aspectRatio = it },
                    label = stringResource(R.string.board_aspect_ratio),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.proj_field_duration),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = defaultsText,
                    onValueChange = { defaultsText = it },
                    label = stringResource(R.string.board_default_params),
                    supportingText = stringResource(R.string.board_default_params_hint),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val defaults = defaultsText.lineSequence().mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else {
                        val key = line.substring(0, index).trim()
                        val value = line.substring(index + 1).trim()
                        if (key.isBlank()) null else key to value
                    }
                }.toMap()
                onSave(title, synopsis, workflowPath, aspectRatio, defaults, durationText.toIntOrNull())
            }) { Text(stringResource(R.string.board_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun AddShotDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_add_shot)) },
        icon = { Icon(Icons.Outlined.Add, null) },
        text = {
            IrisTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = stringResource(R.string.board_shot_prompt),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextAction(stringResource(R.string.board_add), onClick = { onAdd(prompt) }, enabled = prompt.isNotBlank())
        },
        dismissButton = {
            TextAction(stringResource(R.string.action_cancel), onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
internal fun AssetRoleDialog(
    onDismiss: () -> Unit,
    onRole: (ReferenceRole) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_asset_role)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReferenceRole.entries.forEach { role ->
                    TextButton(onClick = { onRole(role) }) { Text(referenceRoleLabel(role)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun ShotWorkflowDialog(
    currentPath: String?,
    workflows: List<ProjectWorkflowOption>,
    onDismiss: () -> Unit,
    onFollowProject: () -> Unit,
    onPick: (ProjectWorkflowOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_shot_workflow)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.board_shot_workflow_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onFollowProject) {
                    Text(
                        (if (currentPath.isNullOrBlank()) "✓ " else "") +
                            stringResource(R.string.board_follow_project),
                    )
                }
                if (workflows.isEmpty()) {
                    Text(
                        stringResource(R.string.board_no_shot_workflows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                workflows.forEach { wf ->
                    TextButton(onClick = { onPick(wf) }) {
                        Text(
                            (if (currentPath == wf.path) "✓ " else "") +
                                "${wf.providerLabel} · ${wf.name}",
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun AssetPickerDialog(
    project: VideoProject,
    selectedAssetIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val assets = project.assets
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_use_asset)) },
        text = {
            if (assets.isEmpty()) {
                Text(stringResource(R.string.board_assets_empty))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    assets.forEach { asset ->
                        val checked = asset.id in selectedAssetIds
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(asset.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                "${referenceRoleLabel(asset.role)} · " +
                                    assetDisplayLabel(project, asset).ifBlank {
                                        asset.path.substringAfterLast('/')
                                    },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (assets.isNotEmpty()) {
                TextButton(onClick = onApply) { Text(stringResource(R.string.board_apply_assets)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
