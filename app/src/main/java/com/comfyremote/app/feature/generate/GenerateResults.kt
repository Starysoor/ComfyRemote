package com.comfyremote.app.feature.generate

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comfyremote.app.R
import com.comfyremote.app.data.generation.ResultImage
import com.comfyremote.app.data.media.decodeSampled
import com.comfyremote.app.ui.IrisType
import com.comfyremote.app.ui.Sizes
import com.comfyremote.app.ui.components.IconAction
import com.comfyremote.app.ui.components.SectionHeader
import com.comfyremote.app.ui.components.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Callbacks for the result action row; every action receives the selected result's local path. */
internal class ResultActions(
    val onCompare: () -> Unit,
    val onView: (index: Int) -> Unit,
    val onSelect: (index: Int) -> Unit,
    val onShare: (String) -> Unit,
    val onSave: (String) -> Unit,
    val onVision: (String) -> Unit,
    val onMakeVideo: (String) -> Unit,
    val onUseAsReference: (String) -> Unit,
    val onRerun: () -> Unit,
)

/**
 * Results block: header (+ Compare), hero of the selected result, thumbnail
 * strip, seed line and the action toolbar. [visibleResults] is the tail of
 * [results] that this screen shows; [selected] indexes into it.
 */
internal fun LazyListScope.resultsSection(
    results: List<ResultImage>,
    visibleResults: List<ResultImage>,
    selected: Int,
    seedInfo: String?,
    running: Boolean,
    hasImageFields: Boolean,
    aiIdle: Boolean,
    actions: ResultActions,
) {
    if (visibleResults.isEmpty()) return
    val index = selected.coerceIn(visibleResults.indices)
    val result = visibleResults[index]

    item("result-header") {
        Column {
            SectionHeader(stringResource(R.string.gen_results)) {
                if (results.size > 1 && results.none { isNonImageMedia(it.localPath) }) {
                    TextAction(stringResource(R.string.gen_compare), actions.onCompare)
                }
            }
            if (results.size > visibleResults.size) {
                Text(
                    stringResource(R.string.gen_results_limit, results.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item("result-main") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultHero(result) { actions.onView(index) }
            val globalIndex = results.size - visibleResults.size + index
            Text(
                stringResource(R.string.gen_result_index, globalIndex + 1, results.size),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = IrisType.metaSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (visibleResults.size > 1) item("result-thumbs") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleResults.forEachIndexed { i, r ->
                val shape = MaterialTheme.shapes.medium
                MediaThumbnail(
                    path = r.localPath,
                    size = Sizes.thumbMd,
                    modifier = Modifier
                        .clip(shape)
                        .then(if (i == index) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                        .clickable { actions.onSelect(i) },
                )
            }
        }
    }
    seedInfo?.takeIf(String::isNotBlank)?.let { seed ->
        item("result-meta") {
            Text(stringResource(R.string.gen_seed_line, seed), style = IrisType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item("result-actions") {
        val path = result.localPath
        val visual = !isNonImageMedia(path)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconAction(Icons.Outlined.Share, stringResource(R.string.action_share), { actions.onShare(path) })
            IconAction(Icons.Outlined.SaveAlt, stringResource(R.string.action_save), { actions.onSave(path) })
            IconAction(
                Icons.Outlined.AutoAwesome,
                stringResource(R.string.gen_vision_rewrite_short),
                { actions.onVision(path) },
                enabled = visual && aiIdle,
            )
            if (visual) IconAction(Icons.Outlined.Movie, stringResource(R.string.gen_make_video), { actions.onMakeVideo(path) })
            if (visual && hasImageFields) {
                IconAction(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.gen_use_as_reference), { actions.onUseAsReference(path) })
            }
            if (!running) IconAction(Icons.Outlined.Replay, stringResource(R.string.gen_rerun), actions.onRerun)
        }
    }
}

@Composable
private fun ResultHero(result: ResultImage, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    if (isNonImageMedia(result.localPath)) {
        MediaThumbnail(
            path = result.localPath,
            size = 220.dp,
            modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
        )
        return
    }
    val bitmap by produceState<Bitmap?>(null, result.localPath) {
        value = withContext(Dispatchers.IO) { decodeSampled(result.localPath, 1600) }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            loaded.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        val w = result.width
        val h = result.height
        val placeholder = if (w != null && h != null && w > 0 && h > 0) {
            Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h)
        } else {
            Modifier.fillMaxWidth().height(220.dp)
        }
        Box(placeholder.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
