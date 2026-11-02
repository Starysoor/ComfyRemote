package com.comfyremote.app.feature.generate

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.comfyremote.app.ui.components.IrisSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comfyremote.app.PromptField
import com.comfyremote.app.R
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.BannerTone
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.InfoBanner
import com.comfyremote.app.ui.components.IrisChip
import com.comfyremote.app.ui.components.IrisTextField
import com.comfyremote.app.ui.components.StatusChip
import com.comfyremote.app.ui.components.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI request to let a vision model look at one image and rewrite the prompt. */
data class VisionRequest(
    val fieldKey: String?,
    val imageValue: String,
    val label: String,
)

/** Neutral dismiss action for dialogs, so only the confirm action carries the accent. */
@Composable
internal fun DismissAction(text: String, onClick: () -> Unit) {
    TextAction(text, onClick, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Cloud jobs keep running (and billing) on AutoDL; make the user acknowledge that before cancelling. */
@Composable
internal fun CancelCloudTaskDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gen_cancel_cloud_title)) },
        text = { Text(stringResource(R.string.gen_cancel_cloud_body)) },
        confirmButton = {
            TextAction(stringResource(R.string.gen_cancel_anyway), onConfirm, color = MaterialTheme.colorScheme.error)
        },
        dismissButton = { DismissAction(stringResource(R.string.gen_keep_waiting), onDismiss) },
    )
}

@Composable
fun VisionInstructionDialog(
    targetLabel: String,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
) {
    val defaultInstruction = stringResource(R.string.gen_vision_default_instruction)
    var instruction by remember { mutableStateOf(defaultInstruction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gen_vision_rewrite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(targetLabel, ChipTone.Accent)
                IrisTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = stringResource(R.string.gen_vision_instruction_label),
                    minLines = 3,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.gen_vision_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextAction(stringResource(R.string.gen_rewrite), { onRun(instruction) }) },
        dismissButton = { DismissAction(stringResource(R.string.action_cancel), onDismiss) },
    )
}

@Composable
fun CloudConfirmDialog(
    confirmation: CloudConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gen_cloud_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(confirmation.workflowName, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.gen_cloud_price, confirmation.priceLabel),
                    color = MaterialTheme.colorScheme.primary,
                    style = IrisType.meta,
                )
                confirmation.estimatedCost?.let {
                    Text(stringResource(R.string.gen_cloud_estimate, it), style = IrisType.meta)
                }
                if (confirmation.duration.isNotBlank() || confirmation.resolution.isNotBlank()) {
                    val duration = confirmation.duration.takeIf { it.isNotBlank() }?.let { stringResource(R.string.gen_cloud_duration, it) }
                    Text(
                        listOfNotNull(duration, confirmation.resolution.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoBanner(text = stringResource(R.string.gen_cloud_billing_note), tone = BannerTone.Warning)
            }
        },
        confirmButton = { TextAction(stringResource(R.string.gen_submit), onConfirm) },
        dismissButton = { DismissAction(stringResource(R.string.action_cancel), onDismiss) },
    )
}

@Composable
fun AiResultDialog(
    initial: String,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
    onAppend: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gen_ai_result_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IrisTextField(text, { text = it }, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.gen_ai_result_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextAction(stringResource(R.string.gen_replace), { onReplace(text) }) },
        dismissButton = { TextAction(stringResource(R.string.gen_append), { onAppend(text) }) },
    )
}

@Composable
private fun CompareImage(result: ResultImage, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, result.localPath) {
        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) }
    }
    val loaded = bitmap
    if (loaded != null) Image(loaded.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Full-screen side-by-side comparison. Black backdrop like the media viewer. */
@Composable
fun CompareDialog(results: List<ResultImage>, onDismiss: () -> Unit) {
    if (results.size < 2) return
    var a by remember { mutableStateOf(0) }
    var b by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.gen_compare_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close), tint = Color.White) }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                CompareImage(results[a.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
                CompareImage(results[b.coerceIn(results.indices)], Modifier.weight(1f).fillMaxSize().padding(2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                ResultPicker("A", a, results.indices) { a = it }
                ResultPicker("B", b, results.indices) { b = it }
            }
        }
    }
}

@Composable
private fun ResultPicker(label: String, value: Int, indices: IntRange, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IrisChip(label = "$label: ${value + 1}", selected = false, onClick = { expanded = true }, leadingIcon = Icons.Outlined.ExpandMore)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            indices.forEach { index ->
                DropdownMenuItem(text = { Text("${index + 1}") }, onClick = { onChange(index); expanded = false })
            }
        }
    }
}

@Composable
fun ParamField(field: PromptField, value: String, onChange: (String) -> Unit) {
    when (field.ptype) {
        "bool" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IrisSwitch(checked = value.equals("true", true), onCheckedChange = { onChange(if (it) "true" else "false") })
        }
        "combo" -> {
            var expanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    val shape = MaterialTheme.shapes.medium
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button) { expanded = true },
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                value.ifBlank { stringResource(R.string.gen_choose) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        field.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
                        }
                    }
                }
            }
        }
        else -> {
            val range = listOfNotNull(field.min, field.max)
            IrisTextField(
                value = value,
                onValueChange = {
                    when (field.ptype) {
                        "int" -> onChange(it.filter { c -> c.isDigit() || c == '-' })
                        "float" -> onChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' })
                        else -> onChange(it)
                    }
                },
                label = field.label,
                supportingText = range.takeIf { it.isNotEmpty() }?.joinToString(" – "),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.ptype) {
                        "int" -> KeyboardType.Number
                        "float" -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
