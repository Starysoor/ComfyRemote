package com.comfyremote.app.feature.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.SegmentedControl
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction

/* Prompt preset library bottom-sheet content, plus its edit / import dialogs. */
@Composable
fun PromptLibrarySheetContent(
    presets: List<PromptPreset>,
    query: String,
    onQuery: (String) -> Unit,
    onApply: (PromptPreset, PresetApplyMode) -> Unit,
    onDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onUpdateMeta: (String, String, String, String) -> Unit,
    onSaveCurrent: () -> Unit,
    onSaveWorkflow: () -> Unit,
    onExport: () -> String,
    onImport: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var applyMode by remember { mutableStateOf(PresetApplyMode.ALL) }
    var editing by remember { mutableStateOf<PromptPreset?>(null) }
    var importing by remember { mutableStateOf(false) }
    val modes = listOf(PresetApplyMode.ALL, PresetApplyMode.PROMPTS, PresetApplyMode.PARAMETERS)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gen_preset_library), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextAction(stringResource(R.string.gen_save_prompt), onSaveCurrent)
            TextAction(stringResource(R.string.gen_save_setup), onSaveWorkflow)
        }
        SegmentedControl(
            options = listOf(
                stringResource(R.string.gen_mode_all),
                stringResource(R.string.gen_mode_prompts),
                stringResource(R.string.gen_mode_params),
            ),
            selectedIndex = modes.indexOf(applyMode),
            onSelect = { applyMode = modes[it] },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextAction(stringResource(R.string.gen_export_json), { clipboard.setText(AnnotatedString(onExport())) })
            TextAction(stringResource(R.string.gen_import_json), { importing = true })
        }
        IrisTextField(
            value = query,
            onValueChange = onQuery,
            label = stringResource(R.string.gen_preset_search),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (presets.isEmpty()) {
            EmptyState(title = stringResource(R.string.gen_presets_empty), icon = Icons.Outlined.Bookmarks)
        } else {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    PresetRow(
                        preset = preset,
                        onClick = { onApply(preset, applyMode) },
                        onEdit = { editing = preset },
                        onToggleFavorite = { onToggleFavorite(preset.id) },
                        onDelete = { onDelete(preset.id) },
                    )
                }
            }
        }
    }

    editing?.let { preset ->
        var title by remember(preset.id) { mutableStateOf(preset.title) }
        var tags by remember(preset.id) { mutableStateOf(preset.tags.joinToString(", ")) }
        var folder by remember(preset.id) { mutableStateOf(preset.folder) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.gen_edit_preset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IrisTextField(title, { title = it }, label = stringResource(R.string.gen_preset_title), singleLine = true)
                    IrisTextField(folder, { folder = it }, label = stringResource(R.string.gen_preset_folder), singleLine = true)
                    IrisTextField(tags, { tags = it }, label = stringResource(R.string.gen_preset_tags), singleLine = true)
                }
            },
            confirmButton = {
                TextAction(stringResource(R.string.action_save), {
                    onUpdateMeta(preset.id, title, tags, folder)
                    editing = null
                })
            },
            dismissButton = { DismissAction(stringResource(R.string.action_cancel)) { editing = null } },
        )
    }

    if (importing) {
        var raw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text(stringResource(R.string.gen_import_title)) },
            text = { IrisTextField(raw, { raw = it }, minLines = 8, maxLines = 14, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextAction(stringResource(R.string.gen_import), { onImport(raw); importing = false }, enabled = raw.isNotBlank())
            },
            dismissButton = { DismissAction(stringResource(R.string.action_cancel)) { importing = false } },
        )
    }
}

@Composable
private fun PresetRow(
    preset: PromptPreset,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    IrisCard(onClick = onClick, contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusChip(stringResource(if (preset.isWorkflowPreset) R.string.gen_tag_setup else R.string.gen_tag_prompt))
                    if (preset.lastUsedAt != null) StatusChip(stringResource(R.string.gen_recent), tone = ChipTone.Accent)
                }
                if (preset.folder.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = muted, modifier = Modifier.size(12.dp))
                        Text(preset.folder, style = IrisType.meta, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    preset.prompts.firstOrNull()?.value ?: preset.values.values.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preset.tags.isNotEmpty()) {
                    Text(preset.tags.joinToString(" · "), style = IrisType.meta, color = muted)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), tint = muted) }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (preset.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    stringResource(R.string.gen_favorite),
                    tint = if (preset.favorite) MaterialTheme.colorScheme.primary else muted,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete), tint = muted) }
        }
    }
}
