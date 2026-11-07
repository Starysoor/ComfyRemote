package com.comfyremote.app.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BottomActionBar
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisSwitch
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction
import kotlinx.coroutines.launch

/**
 * The shot workspace: shared style meta, per-shot timeline and the generate
 * flow. A slot inside the project, never a replacement for the create page.
 */
@Composable
fun ShotsWorkspaceScreen(
    viewModel: ProjectViewModel,
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
    var insertAfterShotId by remember { mutableStateOf<String?>(null) }
    var assetRoleDialog by remember { mutableStateOf(false) }
    var pendingAssetRole by remember { mutableStateOf<ReferenceRole?>(null) }
    var assetShotId by remember { mutableStateOf<String?>(null) }
    var selectedAssetIds by remember { mutableStateOf(setOf<String>()) }
    var shotWorkflowFor by remember { mutableStateOf<String?>(null) }
    var takesForShotId by remember { mutableStateOf<String?>(null) }
    var editingShotId by remember { mutableStateOf<String?>(null) }
    var framePick by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val role = pendingAssetRole
        if (uri != null && role != null) viewModel.addAsset(projectId, role, uri)
        pendingAssetRole = null
    }
    val allDoneText = stringResource(R.string.board_all_done)
    val runningText = stringResource(R.string.gen_vm_storyboard_running)
    val anyRunning = project?.shots?.any { it.status == "running" } == true
    val allDone = project?.shots?.isNotEmpty() == true && project.shots.all { it.status == "done" }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    IrisScaffold(
        snackbarHostState = snackbar,
        topBar = {
            DetailTopBar(
                title = project?.title ?: stringResource(R.string.proj_title),
                onBack = onBack,
                subtitle = project?.let { p ->
                    pluralStringResource(
                        R.plurals.board_progress,
                        p.shots.size,
                        p.shots.count { it.status == "done" },
                        p.shots.size,
                    )
                },
            )
        },
        bottomBar = if (project == null) null else ({
            BottomActionBar {
                IrisPrimaryButton(
                    text = when {
                        allDone -> allDoneText
                        anyRunning -> stringResource(R.string.board_status_running)
                        else -> stringResource(R.string.board_next_shot)
                    },
                    icon = Icons.Outlined.PlayArrow,
                    enabled = project.shots.isNotEmpty() && !anyRunning && !allDone,
                    onClick = {
                        viewModel.nextShot(project.id)?.let { shot ->
                            viewModel.openShot(project.id, shot.id)?.let { (path, name) ->
                                onOpenShot(path, name)
                            }
                        } ?: run {
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (allDone) allDoneText else runningText,
                                )
                            }
                        }
                    },
                )
            }
        }),
    ) { pad ->
        if (project == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.proj_not_found), icon = Icons.Outlined.Movie)
            }
            return@IrisScaffold
        }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("meta") {
                IrisCard {
                    Text(
                        workflows.firstOrNull {
                            it.path == project.workflowPath || it.workflowId == project.workflowId
                        }?.let { "${it.providerLabel} · ${it.name}" }
                            ?: project.workflowPath.ifBlank { project.workflowId },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IrisTextField(
                        value = project.stylePrompt,
                        onValueChange = { viewModel.setStyle(project.id, it) },
                        label = stringResource(R.string.board_style_prompt),
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
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
                            checked = project.chainFrames,
                            onCheckedChange = { enabled ->
                                viewModel.setChainFrames(project.id, enabled)
                            },
                        )
                    }
                }
            }

            item("overview") {
                val chips = listOf(
                    Triple(stringResource(R.string.board_status_running), project.shots.count { it.status == "running" }, ChipTone.Accent),
                    Triple(stringResource(R.string.board_status_draft), project.shots.count { it.status != "running" && it.status != "done" && it.status != "failed" }, ChipTone.Neutral),
                    Triple(stringResource(R.string.board_status_failed), project.shots.count { it.status == "failed" }, ChipTone.Error),
                    Triple(stringResource(R.string.board_status_done), project.shots.count { it.status == "done" }, ChipTone.Success),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    chips.filter { it.second > 0 }.forEach { (label, count, tone) ->
                        StatusChip("$label $count", tone = tone)
                    }
                }
            }

            item("assets-heading") {
                SectionHeader(stringResource(R.string.board_assets)) {
                    if (project.assets.isNotEmpty()) {
                        Text(
                            project.assets.size.toString(),
                            style = IrisType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                    }
                    TextAction(stringResource(R.string.board_add_asset), onClick = { assetRoleDialog = true })
                }
            }
            item("assets") {
                if (project.assets.isEmpty()) {
                    Text(
                        stringResource(R.string.board_assets_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(project.assets, key = { it.id }) { asset ->
                            AssetCard(
                                asset = asset,
                                label = assetDisplayLabel(project, asset),
                            ) { viewModel.removeAsset(project.id, asset.id) }
                        }
                    }
                }
            }

            item("shots-heading") {
                SectionHeader(stringResource(R.string.board_shots)) {
                    TextAction(stringResource(R.string.board_add_shot), onClick = { addingShot = true })
                }
            }

            if (project.shots.isEmpty()) {
                item("empty") {
                    EmptyState(
                        title = stringResource(R.string.board_no_shots),
                        detail = stringResource(R.string.board_no_shots_detail),
                        icon = Icons.Outlined.Movie,
                        action = {
                            TextAction(stringResource(R.string.board_add_shot), onClick = { addingShot = true })
                        },
                    )
                }
            } else {
                itemsIndexed(project.shots, key = { _, shot -> shot.id }) { index, shot ->
                    val previous = project.shots.getOrNull(index - 1)
                    val next = project.shots.getOrNull(index + 1)
                    ShotTimelineItem(
                        index = index,
                        shot = shot,
                        workflowLabel = shotWorkflowLabel(shot, project, workflows),
                        referenceAssets = shot.referenceAssetIds.mapNotNull { id ->
                            project.assets.firstOrNull { it.id == id }
                        },
                        referenceFromPrevious = !shot.referenceManual && shot.referencePath != null &&
                            shot.referencePath == previous?.lastFramePath,
                        feedsNext = project.chainFrames && shot.lastFramePath != null &&
                            next != null && !next.referenceManual &&
                            next.referenceAssetIds.isEmpty() &&
                            next.referencePath == shot.lastFramePath,
                        canMoveUp = index > 0,
                        canMoveDown = index < project.shots.lastIndex,
                        canUseAssets = project.assets.isNotEmpty(),
                        previousFramePath = previous?.lastFramePath,
                        onUsePreviousFrame = {
                            viewModel.usePreviousFrame(project.id, shot.id, previous?.lastFramePath)
                        },
                        onOpen = {
                            viewModel.openShot(project.id, shot.id)?.let { (path, name) ->
                                onOpenShot(path, name)
                            }
                        },
                        onDelete = { viewModel.deleteShot(project.id, shot.id) },
                        onMoveUp = { viewModel.moveShot(project.id, shot.id, -1) },
                        onMoveDown = { viewModel.moveShot(project.id, shot.id, 1) },
                        onDuplicate = { viewModel.duplicateShot(project.id, shot.id) },
                        onInsertAfter = { insertAfterShotId = shot.id },
                        onPickWorkflow = { shotWorkflowFor = shot.id },
                        onPickAsset = {
                            assetShotId = shot.id
                            selectedAssetIds = shot.referenceAssetIds.toSet()
                        },
                        onEditShot = { editingShotId = shot.id },
                        onPickFirstFrame = { framePick = shot.id to true },
                        onPickLastFrame = { framePick = shot.id to false },
                        onOpenTakes = if (shot.takes.isNotEmpty()) {
                            { takesForShotId = shot.id }
                        } else {
                            null
                        },
                        onClearReference = { viewModel.clearShotReference(project.id, shot.id) },
                    )
                }
            }
        }
    }

    if (addingShot) {
        AddShotDialog(
            onDismiss = { addingShot = false },
            onAdd = { prompt, notes ->
                viewModel.addShot(projectId, prompt, notes)
                addingShot = false
            },
        )
    }
    insertAfterShotId?.let { shotId ->
        AddShotDialog(
            onDismiss = { insertAfterShotId = null },
            onAdd = { prompt, notes ->
                viewModel.insertShotAfter(projectId, shotId, prompt, notes)
                insertAfterShotId = null
            },
        )
    }
    if (assetRoleDialog) {
        AssetRoleDialog(
            onDismiss = { assetRoleDialog = false },
            onRole = { role ->
                pendingAssetRole = role
                assetRoleDialog = false
                assetPicker.launch("image/*")
            },
        )
    }
    shotWorkflowFor?.let { shotId ->
        val currentPath = project?.shots?.firstOrNull { it.id == shotId }?.workflowPath
        ShotWorkflowDialog(
            currentPath = currentPath,
            workflows = workflows,
            onDismiss = { shotWorkflowFor = null },
            onFollowProject = {
                viewModel.setShotWorkflow(projectId, shotId, null)
                shotWorkflowFor = null
            },
            onPick = { wf ->
                viewModel.setShotWorkflow(projectId, shotId, wf.path)
                shotWorkflowFor = null
            },
        )
    }
    assetShotId?.let { shotId ->
        val current = project
        if (current != null) {
            AssetPickerDialog(
                project = current,
                selectedAssetIds = selectedAssetIds,
                onToggle = { id ->
                    selectedAssetIds = if (id in selectedAssetIds) {
                        selectedAssetIds - id
                    } else {
                        selectedAssetIds + id
                    }
                },
                onDismiss = { assetShotId = null },
                onApply = {
                    viewModel.assignAssetsToShot(projectId, shotId, selectedAssetIds.toList())
                    assetShotId = null
                },
            )
        } else {
            assetShotId = null
        }
    }
    editingShotId?.let { shotId ->
        val shot = project?.shots?.firstOrNull { it.id == shotId }
        if (shot == null) {
            editingShotId = null
        } else {
            EditShotDialog(
                shot = shot,
                onDismiss = { editingShotId = null },
                onSave = { prompt, notes ->
                    viewModel.updateShot(projectId, shotId, prompt)
                    viewModel.updateShotCameraNotes(projectId, shotId, notes.orEmpty())
                    editingShotId = null
                },
            )
        }
    }
    takesForShotId?.let { shotId ->
        val shot = project?.shots?.firstOrNull { it.id == shotId }
        if (shot == null) {
            takesForShotId = null
        } else {
            TakeWorkshopDialog(
                shot = shot,
                onDismiss = { takesForShotId = null },
                onSelect = { takeId ->
                    viewModel.switchToTake(projectId, shotId, takeId)
                },
                onToggleFavorite = { takeId, favorite ->
                    viewModel.setTakeFavorite(projectId, shotId, takeId, favorite)
                },
                onDiscard = { takeId ->
                    viewModel.discardTake(projectId, shotId, takeId)
                },
                onDelete = { takeId ->
                    viewModel.deleteTake(projectId, shotId, takeId)
                },
            )
        }
    }
    framePick?.let { (shotId, first) ->
        if (project == null) {
            framePick = null
        } else {
            AssetSinglePickerDialog(
                project = project,
                title = stringResource(
                    if (first) R.string.proj_shot_set_first_frame else R.string.proj_shot_set_last_frame,
                ),
                onDismiss = { framePick = null },
                onPick = { assetId ->
                    viewModel.setShotFrameAsset(projectId, shotId, assetId, first)
                    framePick = null
                },
            )
        }
    }
}
