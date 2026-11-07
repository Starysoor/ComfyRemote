package com.comfyremote.app.feature.project

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.project.DirectorChecks
import com.comfyremote.app.data.project.DirectorChecks.Severity
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Spacing
import com.comfyremote.app.ui.components.ChipTone
import com.comfyremote.app.ui.components.DetailTopBar
import com.comfyremote.app.ui.components.EmptyState
import com.comfyremote.app.ui.components.IrisCard
import com.comfyremote.app.ui.components.IrisScaffold
import com.comfyremote.app.ui.components.StatusChip

/**
 * The AI director hub: local rule checks, frame-strip continuity and the
 * suggestion-only shot planner. Everything here is advisory — nothing in this
 * screen submits paid work.
 */
@Composable
fun DirectorScreen(
    viewModel: ProjectViewModel,
    projectId: String,
    onBack: () -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    val message by viewModel.message.collectAsState()
    val project = projects.firstOrNull { it.id == projectId }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    IrisScaffold(
        snackbarHostState = snackbar,
        topBar = {
            DetailTopBar(
                title = stringResource(R.string.proj_director_title),
                onBack = onBack,
            )
        },
    ) { pad ->
        if (project == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.proj_not_found), icon = Icons.Outlined.Movie)
            }
            return@IrisScaffold
        }
        // Files that exist on disk drive the "missing media" rules; built once
        // per composition here and refreshed by recomposition on edits.
        val existingPaths = remember(project.updatedAt) {
            buildSet {
                project.shots.forEach { shot ->
                    shot.resultPath?.let(::add)
                    shot.lastFramePath?.let(::add)
                    shot.referencePath?.let(::add)
                }
                project.assets.forEach { add(it.path) }
            }.filter { it.isNotBlank() && java.io.File(it).isFile }.toSet()
        }
        val issues = remember(project.updatedAt) { DirectorChecks.analyze(project, existingPaths) }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("checks-heading") {
                SectionTitle(stringResource(R.string.proj_director_checks))
            }
            if (issues.isEmpty()) {
                item("checks-empty") {
                    Text(
                        stringResource(R.string.proj_director_checks_clean),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(issues.size, key = { "issue-$it" }) { index ->
                    val issue = issues[index]
                    IrisCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(
                                stringResource(
                                    if (issue.severity == Severity.WARNING) {
                                        R.string.proj_director_warning
                                    } else {
                                        R.string.proj_director_info
                                    },
                                ),
                                tone = if (issue.severity == Severity.WARNING) ChipTone.Warning else ChipTone.Neutral,
                            )
                            Text(
                                issue.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
            }
            // P3-2 (frame strips) and P3-3 (AI shot plan) add their sections here.
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(Icons.Outlined.Build, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
