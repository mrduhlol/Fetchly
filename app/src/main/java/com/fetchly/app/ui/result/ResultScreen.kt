package com.fetchly.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.ui.components.EmptyState
import com.fetchly.app.ui.components.QualityRow

/**
 * What the link turned out to be, and the one choice left: quality. A tap on
 * Download hands the option to the queue and lands on Downloads.
 */
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onBack: () -> Unit,
    onQueued: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.queued) {
        if (state.queued) {
            viewModel.consumeQueued()
            onQueued()
        }
    }

    val media = state.media
    if (media == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = "Nothing to show",
                subtitle = "Fetch a link first, then pick a quality here.",
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Ready to fetch", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(16.dp))

        Thumbnail(media)

        Spacer(Modifier.height(16.dp))

        Text(
            media.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            factsFor(media),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text("Choose a quality", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        media.options.forEach { option ->
            QualityRow(
                option = option,
                selected = option.id == state.selectedId,
                onSelect = { viewModel.select(option.id) },
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = viewModel::download,
            enabled = state.selected != null && !state.enqueuing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.enqueuing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("Starting…")
            } else {
                Text("Download")
            }
        }
    }
}

@Composable
private fun Thumbnail(media: MediaInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = media.thumbnailUrl
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun factsFor(media: MediaInfo): String = buildList {
    media.uploader?.let(::add)
    media.durationSeconds?.let { add(formatDuration(it)) }
    add(PlatformDetector.label(media.platform))
}.joinToString(" · ")

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
