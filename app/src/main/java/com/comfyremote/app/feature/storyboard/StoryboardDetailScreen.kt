package com.comfyremote.app.feature.storyboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
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
    var menuOpen by remember { mutableStateOf(false) }
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
                                    viewModel.deleteProject(projectId)
                                    onBack()
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
                        referenceFromPrevious = shot.referencePath != null &&
                            shot.referencePath == previous?.lastFramePath,
                        feedsNext = shot.lastFramePath != null && index < project.shots.lastIndex,
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

/** One shot on the timeline: accent rail, media, prompt, status and actions. */
@Composable
private fun ShotTimelineItem(
    index: Int,
    shot: StoryboardShot,
    referenceFromPrevious: Boolean,
    feedsNext: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
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
                        TextAction(
                            text = when {
                                shot.status == "done" -> stringResource(R.string.board_regenerate)
                                running -> stringResource(R.string.phase_running)
                                else -> stringResource(R.string.board_prepare)
                            },
                            onClick = onOpen,
                            enabled = !running,
                        )
                        TextAction(stringResource(R.string.action_delete), onClick = onDelete, color = MaterialTheme.colorScheme.error)
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
