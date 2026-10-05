package com.fetchly.app.presentation.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fetchly.app.presentation.components.FetchButton
import com.fetchly.app.presentation.components.FormatSelector
import com.fetchly.app.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onDownloadStarted: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val info = ui.info

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fetchly") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (info == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Nothing to show. Go back and paste a link.")
                Spacer(Modifier.height(12.dp))
                FetchButton(label = "BACK TO HOME", onClick = onBack)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = "Thumbnail for ${info.title}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
            Text(info.platform.label, style = MaterialTheme.typography.labelLarge)
            Text(info.title, style = MaterialTheme.typography.titleLarge)
            info.author?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(info.mediaType.name)
                    info.durationSecs?.let { append(" • ${it / 60}:${"%02d".format(it % 60)}") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (info.videoFormats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("VIDEO", style = MaterialTheme.typography.labelMedium)
                FormatSelector(
                    formats = info.videoFormats,
                    selectedId = ui.selectedFormatId,
                    onSelect = vm::selectFormat,
                )
            }
            if (info.audioFormats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("AUDIO", style = MaterialTheme.typography.labelMedium)
                FormatSelector(
                    formats = info.audioFormats,
                    selectedId = ui.selectedFormatId,
                    onSelect = vm::selectFormat,
                )
            }
            Spacer(Modifier.height(20.dp))
            FetchButton(
                label = "DOWNLOAD",
                onClick = { vm.downloadSelected(); onDownloadStarted() },
                enabled = vm.selectedFormat() != null,
            )
        }
    }
}
