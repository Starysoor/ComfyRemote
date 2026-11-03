package com.comfyremote.app.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.generation.label
import com.comfyremote.app.data.generation.uiLabel
import com.comfyremote.app.feature.generate.MediaThumbnail
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.LocalAppSettings
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisProgressBar
import com.comfyremote.app.ui.components.StatusChip
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One task in the list: thumbnail, name, status lines, chips and live progress. */
@Composable
internal fun TaskRow(task: GenerationTask, onClick: () -> Unit) {
    val compact = LocalAppSettings.current.compactUi
    IrisCard(
        onClick = onClick,
        highlighted = task.isActive,
        contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = if (compact) 10.dp else 12.dp, bottom = if (compact) 10.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskLeading(task)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    task.request.workflowName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TaskStatusLines(task)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskChips(task, recoveredLabel = stringResource(R.string.tasks_recovered))
                    Text(
                        formatTime(task.createdAt) + " ·",
                        style = IrisType.metaSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    ElapsedText(task, IrisType.metaSmall)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        RunningNodeProgress(task)
    }
}

@Composable
private fun TaskLeading(task: GenerationTask) {
    val result = task.results.firstOrNull()
    if (result != null) {
        Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MediaThumbnail(result.localPath, 48.dp, Modifier.clip(MaterialTheme.shapes.small))
        }
    } else {
        Box(
            Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                phaseIcon(task),
                contentDescription = null,
                tint = if (task.phase == GenerationPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Queue position, app-side wait, current node and error, each only when present. */
@Composable
private fun TaskStatusLines(task: GenerationTask) {
    task.queuePosition?.let {
        Text(stringResource(R.string.tasks_queue_position, it), style = IrisType.meta, color = MaterialTheme.colorScheme.primary)
    }
    if (task.localPending) {
        Text(stringResource(R.string.tasks_waiting_local), style = IrisType.meta, color = MaterialTheme.colorScheme.primary)
    }
    task.nodeTitle?.takeIf(String::isNotBlank)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    task.error?.let {
        Text(
            it.uiLabel() + " · " + it.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Status chip plus optional batch and recovered chips. */
@Composable
internal fun TaskChips(task: GenerationTask, recoveredLabel: String) {
    StatusChip(task.label(), tone = taskTone(task))
    task.batchIndex?.let { StatusChip("$it/${task.batchSize ?: it}") }
    if (task.recovered) StatusChip(recoveredLabel)
}

internal fun taskTone(task: GenerationTask): ChipTone = when {
    task.localPending -> ChipTone.Neutral
    else -> when (task.phase) {
        GenerationPhase.PREPARING, GenerationPhase.QUEUEING, GenerationPhase.QUEUED, GenerationPhase.RUNNING -> ChipTone.Accent
        GenerationPhase.CANCELLING -> ChipTone.Warning
        GenerationPhase.SUCCEEDED -> ChipTone.Success
        GenerationPhase.FAILED -> ChipTone.Error
        GenerationPhase.CANCELLED -> ChipTone.Neutral
    }
}

private fun phaseIcon(task: GenerationTask): ImageVector = when {
    task.localPending -> Icons.Outlined.HourglassEmpty
    else -> when (task.phase) {
        GenerationPhase.PREPARING, GenerationPhase.QUEUEING, GenerationPhase.QUEUED -> Icons.Outlined.HourglassEmpty
        GenerationPhase.RUNNING -> Icons.Outlined.AutoAwesome
        GenerationPhase.FAILED -> Icons.Outlined.ErrorOutline
        GenerationPhase.CANCELLING, GenerationPhase.CANCELLED -> Icons.Outlined.Block
        GenerationPhase.SUCCEEDED -> Icons.Outlined.Image
    }
}

/** Live elapsed time; ticks every second while the task is active. */
@Composable
internal fun ElapsedText(task: GenerationTask, style: TextStyle = IrisType.meta) {
    val elapsed by produceState(initialValue = task.elapsedMs(), task.taskId, task.isActive, task.completedAt) {
        value = task.elapsedMs()
        while (task.isActive) {
            delay(1_000)
            value = task.elapsedMs()
        }
    }
    Text(formatDuration(elapsed), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** Node progress for running tasks: determinate when the server reports it, a sweep otherwise. */
@Composable
internal fun RunningNodeProgress(task: GenerationTask) {
    if (task.phase != GenerationPhase.RUNNING) return
    val p = task.progress?.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (p == null) R.string.tasks_node_running else R.string.tasks_node_progress),
                style = IrisType.metaSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (p != null) Text("${(p * 100).toInt()}%", style = IrisType.metaSmall, color = MaterialTheme.colorScheme.primary)
        }
        IrisProgressBar(progress = p)
    }
}

@Composable
internal fun filterLabel(filter: TaskFilter): String = stringResource(
    when (filter) {
        TaskFilter.ALL -> R.string.tasks_filter_all
        TaskFilter.ACTIVE -> R.string.tasks_filter_active
        TaskFilter.QUEUED -> R.string.tasks_filter_queued
        TaskFilter.FAILED -> R.string.tasks_filter_failed
        TaskFilter.DONE -> R.string.tasks_filter_done
    }
)

internal fun formatDuration(ms: Long): String {
    val seconds = ms / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

internal fun formatTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
internal fun formatTimeFull(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
