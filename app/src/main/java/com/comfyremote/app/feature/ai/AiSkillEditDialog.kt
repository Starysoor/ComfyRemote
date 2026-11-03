package com.comfyremote.app.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.R
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.TextAction

/** Create or edit a prompt skill and its workflow keyword bindings. */
@Composable
internal fun SkillEditDialog(
    skill: AiSkillDef?,
    initialPatterns: List<String>,
    onDismiss: () -> Unit,
    onSave: (AiSkillDef, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onReset: (() -> Unit)?,
) {
    var label by remember(skill) { mutableStateOf(skill?.label.orEmpty()) }
    var prompt by remember(skill) { mutableStateOf(skill?.systemPrompt.orEmpty()) }
    var patterns by remember(skill, initialPatterns) { mutableStateOf(initialPatterns.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (skill == null) stringResource(R.string.ai_skill_new_title)
                else stringResource(R.string.ai_skill_edit_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IrisTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.ai_skill_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = patterns,
                    onValueChange = { patterns = it },
                    label = stringResource(R.string.ai_skill_patterns),
                    supportingText = stringResource(R.string.ai_skill_patterns_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                IrisTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = stringResource(R.string.ai_skill_prompt),
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.action_save),
                onClick = {
                    val parsed = patterns.split(',', '，', '\n').map(String::trim).filter(String::isNotBlank)
                    onSave(
                        AiSkillDef(skill?.id ?: "custom_${System.currentTimeMillis()}", label.trim(), prompt, skill?.builtin ?: false),
                        parsed,
                    )
                },
                enabled = label.isNotBlank() && prompt.isNotBlank(),
            )
        },
        dismissButton = {
            Row {
                onReset?.let { TextAction(stringResource(R.string.ai_reset), onClick = it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                onDelete?.let { TextAction(stringResource(R.string.action_delete), onClick = it, color = MaterialTheme.colorScheme.error) }
                TextAction(stringResource(R.string.action_cancel), onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
