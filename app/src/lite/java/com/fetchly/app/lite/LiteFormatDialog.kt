package com.fetchly.app.lite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.util.FormatSize

@Composable
fun LiteFormatDialog(state: LiteResolveState, vm: LiteResolveViewModel, download: () -> Unit,
    close: () -> Unit, floating: Boolean = false) {
    if (floating) LiteFormatContent(state, vm, download, close)
    else Dialog(onDismissRequest = { if (!state.adding) close() },
        properties = DialogProperties(dismissOnBackPress = !state.adding, dismissOnClickOutside = !state.adding)) {
        LiteFormatContent(state, vm, download, close)
    }
}

@Composable
private fun LiteFormatContent(state: LiteResolveState, vm: LiteResolveViewModel, download: () -> Unit, close: () -> Unit) {
    Surface(color = Parchment, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
        Column(Modifier.widthIn(max = 440.dp).heightIn(max = 720.dp).verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose your download", style = MaterialTheme.typography.headlineSmall)
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.stage)
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.items.size > 1) {
                Text("Select the items you want (${state.selected.size}/${state.items.size})")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.items) { index, media ->
                        Column(Modifier.width(112.dp)) {
                            Box(Modifier.size(112.dp).clickable(enabled = !state.adding) { vm.focus(index) }) {
                                AsyncImage(media.thumbnailUrl, "Preview item ${index + 1}",
                                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                Checkbox(index in state.selected, { vm.toggle(index) }, enabled = !state.adding,
                                    modifier = Modifier.align(Alignment.TopEnd))
                            }
                            TextButton(onClick = { vm.focus(index) }, enabled = !state.adding) {
                                Text(if (index == state.focused) "Item ${index + 1} · Editing" else "Item ${index + 1}")
                            }
                        }
                    }
                }
            }
            val media = state.items.getOrNull(state.focused)
            val selected = state.option(state.focused)
            if (media != null) {
                AsyncImage(media.thumbnailUrl, "Media thumbnail", Modifier.fillMaxWidth().heightIn(max = 180.dp)
                    .aspectRatio(if ((media.width ?: 0) > 0 && (media.height ?: 0) > 0)
                        (media.width!!.toFloat() / media.height!!).coerceIn(.7f, 2f) else 16f / 9f), contentScale = ContentScale.Fit)
                Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                media.uploader?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val kinds = media.options.map { it.kind }.distinct()
                if (kinds.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { kind ->
                        FilterChip(selected?.kind == kind, onClick = {
                            media.options.firstOrNull { it.kind == kind && !it.silentVideo }?.let { vm.select(it.id) }
                                ?: media.options.firstOrNull { it.kind == kind }?.let { vm.select(it.id) }
                        }, enabled = !state.adding, label = { Text(when (kind) {
                            QualityKind.AUDIO -> "MP3"; QualityKind.VIDEO -> "Video"; QualityKind.FILE -> "Original"
                        }) })
                    }
                }
                Column(Modifier.selectableGroup()) {
                    media.options.filter { it.kind == (selected?.kind ?: media.options.firstOrNull()?.kind) }.forEach { option ->
                        Row(Modifier.fillMaxWidth().selectable(selected?.id == option.id,
                            enabled = !state.adding, role = Role.RadioButton, onClick = { vm.select(option.id) })
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected?.id == option.id, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(option.label)
                                Text("${option.container.uppercase()} · ${option.audioDescription}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(FormatSize.format(option.sizeBytes) ?: "Size unknown", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (state.silent) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.silentConfirmed, vm::confirmSilent, enabled = !state.adding)
                Text("Download selected video without sound", style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.error != null && state.items.isEmpty()) TextButton(onClick = { vm.resolve() }, enabled = !state.loading) { Text("Retry") }
            Button(onClick = download, enabled = state.canDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (state.adding) "Adding…" else if (state.items.size > 1) "Download selected (${state.selected.size})" else "Download")
            }
            TextButton(onClick = close, enabled = !state.adding, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}
