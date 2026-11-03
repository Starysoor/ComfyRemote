package com.comfyremote.app.feature.storyboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.reference.ProjectAsset
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.data.storyboard.StoryboardShot
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Sizes
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.AccentRail
import com.comfyremote.app.ui.components.BottomActionBar
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisPrimaryButton
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction
import kotlinx.coroutines.launch

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
    var insertAfterShotId by remember { mutableStateOf<String?>(null) }
    var assetRoleDialog by remember { mutableStateOf(false) }
    var pendingAssetRole by remember { mutableStateOf<ReferenceRole?>(null) }
    var assetShotId by remember { mutableStateOf<String?>(null) }
    var selectedAssetIds by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val role = pendingAssetRole
        if (uri != null && role != null) viewModel.addAsset(projectId, role, uri)
        pendingAssetRole = null
    }
    val allDoneText = stringResource(R.string.board_all_done)
    val runningText = stringResource(R.string.gen_vm_storyboard_running)
    val anyRunning = project?.shots?.any { it.status == "running" } == true

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    IrisScaffold(
        snackbarHostState = snackbar,
        topBar = {
            DetailTopBar(
                title = project?.title ?: stringResource(R.string.board_title),
                onBack = onBack,
                subtitle = project?.let { p ->
                    pluralStringResource(
                        R.plurals.board_progress,
                        p.shots.size,
                        p.shots.count { it.status == "done" },
                        p.shots.size,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.board_delete_project), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    if (viewModel.deleteProject(projectId)) onBack()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = if (project == null) null else ({
            BottomActionBar {
                IrisPrimaryButton(
                    text = stringResource(R.string.board_next_shot),
                    icon = Icons.Outlined.PlayArrow,
                    onClick = {
                        if (anyRunning) {
                            scope.launch { snackbar.showSnackbar(runningText) }
                        } else {
                            viewModel.nextShot(project.id)?.let { shot ->
                                viewModel.openShot(project.id, shot.id)?.let { (path, name) ->
                                    onOpenShot(path, name)
                                }
                            } ?: run {
                                scope.launch { snackbar.showSnackbar(allDoneText) }
                            }
                        }
                    },
                )
            }
        }),
    ) { pad ->
        if (project == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.board_not_found), icon = Icons.Outlined.Movie)
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
                        workflows.firstOrNull { it.id == project.workflowId }?.name ?: project.workflowId,
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
                    IrisTextField(
                        value = project.aspectRatio,
                        onValueChange = { viewModel.setAspectRatio(project.id, it) },
                        label = stringResource(R.string.board_aspect_ratio),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item("assets-heading") {
                SectionHeader(stringResource(R.string.board_assets)) {
                    TextAction(stringResource(R.string.board_add_asset), onClick = { assetRoleDialog = true })
                }
            }
            item("assets") {
                if (project.assets.isEmpty()) {
                    Text(
                        stringResource(R.string.board_assets_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(project.assets, key = { it.id }) { asset ->
                            AssetCard(asset) { viewModel.removeAsset(project.id, asset.id) }
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
                    ShotTimelineItem(
                        index = index,
                        shot = shot,
                        referenceFromPrevious = !shot.referenceManual && shot.referencePath != null &&
                            shot.referencePath == previous?.lastFramePath,
                        feedsNext = shot.lastFramePath != null && index < project.shots.lastIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < project.shots.lastIndex,
                        canUseAssets = project.assets.isNotEmpty(),
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
                        onPickAsset = {
                            assetShotId = shot.id
                            selectedAssetIds = shot.referenceAssetIds.toSet()
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
            onAdd = { prompt ->
                viewModel.addShot(projectId, prompt)
                addingShot = false
            },
        )
    }
    insertAfterShotId?.let { shotId ->
        AddShotDialog(
            onDismiss = { insertAfterShotId = null },
            onAdd = { prompt ->
                viewModel.insertShotAfter(projectId, shotId, prompt)
                insertAfterShotId = null
            },
        )
    }
    if (assetRoleDialog) {
        AlertDialog(
            onDismissRequest = { assetRoleDialog = false },
            title = { Text(stringResource(R.string.board_asset_role)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReferenceRole.entries.forEach { role ->
                        TextButton(onClick = {
                            pendingAssetRole = role
                            assetRoleDialog = false
                            assetPicker.launch("image/*")
                        }) { Text(referenceRoleLabel(role)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { assetRoleDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    assetShotId?.let { shotId ->
        val assets = project?.assets.orEmpty()
        AlertDialog(
            onDismissRequest = { assetShotId = null },
            title = { Text(stringResource(R.string.board_use_asset)) },
            text = {
                if (assets.isEmpty()) {
                    Text(stringResource(R.string.board_assets_empty))
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        assets.forEach { asset ->
                            val checked = asset.id in selectedAssetIds
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedAssetIds = if (checked) {
                                        selectedAssetIds - asset.id
                                    } else {
                                        selectedAssetIds + asset.id
                                    }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(
                                    "${referenceRoleLabel(asset.role)} · " +
                                        asset.label.ifBlank { asset.path.substringAfterLast('/') },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (assets.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.assignAssetsToShot(projectId, shotId, selectedAssetIds.toList())
                        assetShotId = null
                    }) { Text(stringResource(R.string.board_apply_assets)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { assetShotId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AssetCard(asset: ProjectAsset, onDelete: () -> Unit) {
    Column(
        Modifier.width(96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box {
            MediaThumbnail(asset.path, 96.dp, Modifier.clip(MaterialTheme.shapes.medium))
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Outlined.Delete,
                    stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            referenceRoleLabel(asset.role),
            style = IrisType.metaSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun referenceRoleLabel(role: ReferenceRole): String = when (role) {
    ReferenceRole.AUTO -> stringResource(R.string.reference_role_auto)
    ReferenceRole.START_FRAME -> stringResource(R.string.reference_role_start)
    ReferenceRole.END_FRAME -> stringResource(R.string.reference_role_end)
    ReferenceRole.CHARACTER_REFERENCE -> stringResource(R.string.reference_role_character)
    ReferenceRole.SCENE_REFERENCE -> stringResource(R.string.reference_role_scene)
    ReferenceRole.STYLE_REFERENCE -> stringResource(R.string.reference_role_style)
    ReferenceRole.OUTFIT_REFERENCE -> stringResource(R.string.reference_role_outfit)
    ReferenceRole.COMPOSITION_REFERENCE -> stringResource(R.string.reference_role_composition)
    ReferenceRole.ACTION_REFERENCE -> stringResource(R.string.reference_role_action)
    ReferenceRole.GENERIC_REFERENCE -> stringResource(R.string.reference_role_generic)
}

/** One shot on the timeline: accent rail, media, prompt, status and actions. */
@Composable
private fun ShotTimelineItem(
    index: Int,
    shot: StoryboardShot,
    referenceFromPrevious: Boolean,
    feedsNext: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canUseAssets: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onInsertAfter: () -> Unit,
    onPickAsset: () -> Unit,
    onClearReference: () -> Unit,
) {
    var shotMenu by remember { mutableStateOf(false) }
    val (statusLabel, tone) = when (shot.status) {
        "done" -> stringResource(R.string.board_status_done) to ChipTone.Success
        "running" -> stringResource(R.string.board_status_running) to ChipTone.Accent
        "failed" -> stringResource(R.string.board_status_failed) to ChipTone.Error
        else -> stringResource(R.string.board_status_draft) to ChipTone.Neutral
    }
    val reference = shot.referencePath ?: shot.lastFramePath
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        IrisCard {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                AccentRail()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.board_shot_number, index + 1),
                            style = IrisType.overline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(statusLabel, tone = tone)
                    }
                    Text(
                        shot.prompt.ifBlank { stringResource(R.string.board_empty_shot) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (reference != null || shot.resultPath != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            reference?.let {
                                ShotMedia(it, if (shot.referencePath != null) stringResource(R.string.board_reference) else null)
                            }
                            shot.resultPath?.let { ShotMedia(it, stringResource(R.string.board_result)) }
                        }
                    }
                    if (referenceFromPrevious) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                stringResource(R.string.board_reference_from_previous),
                                style = IrisType.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    shot.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        val running = shot.status == "running"
                        Box {
                            IconButton(onClick = { shotMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more))
                            }
                            DropdownMenu(shotMenu, { shotMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_move_up)) },
                                    enabled = canMoveUp && !running,
                                    onClick = { shotMenu = false; onMoveUp() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_move_down)) },
                                    enabled = canMoveDown && !running,
                                    onClick = { shotMenu = false; onMoveDown() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_duplicate)) },
                                    enabled = !running,
                                    onClick = { shotMenu = false; onDuplicate() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_insert_after)) },
                                    enabled = !running,
                                    onClick = { shotMenu = false; onInsertAfter() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_use_asset)) },
                                    enabled = canUseAssets && !running,
                                    onClick = { shotMenu = false; onPickAsset() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.board_clear_reference)) },
                                    enabled = shot.referencePath != null && !running,
                                    onClick = { shotMenu = false; onClearReference() },
                                )
                            }
                        }
                        TextAction(
                            text = when {
                                shot.status == "done" -> stringResource(R.string.board_regenerate)
                                running -> stringResource(R.string.phase_running)
                                else -> stringResource(R.string.board_prepare)
                            },
                            onClick = onOpen,
                            enabled = !running,
                        )
                        TextAction(
                            stringResource(R.string.action_delete),
                            onClick = onDelete,
                            enabled = !running,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        if (feedsNext) {
            Row(
                Modifier.padding(start = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(Icons.Outlined.ArrowDownward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.board_chain_next, index + 2),
                    style = IrisType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShotMedia(path: String, caption: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MediaThumbnail(path, Sizes.thumbMd, Modifier.clip(MaterialTheme.shapes.medium))
        caption?.let {
            Text(it, style = IrisType.metaSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AddShotDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
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
