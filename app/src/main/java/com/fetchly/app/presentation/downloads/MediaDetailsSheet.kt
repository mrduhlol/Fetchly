package com.fetchly.app.presentation.downloads

import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.util.MimeTypes
import com.fetchly.app.domain.util.RelativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compact media details. Never exposes internal server URLs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (displayName, sizeBytes) = remember(entry.localUri) {
        val queried: Pair<String?, Long?> = if (entry.localUri == null) {
            null to null
        } else {
            runCatching {
                context.contentResolver.query(entry.localUri.toUri(), null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        (if (nameIdx >= 0) c.getString(nameIdx) else null) to
                            (if (sizeIdx >= 0) c.getLong(sizeIdx).takeIf { it >= 0 } else null)
                    } else {
                        null to null
                    }
                }
            }.getOrNull() ?: (null to null)
        }
        // Fall back to the size recorded at download time.
        queried.first to (queried.second ?: entry.sizeBytes)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            DetailRow("Title", entry.title)
            DetailRow("Platform", entry.platform.label)
            DetailRow("Media type", entry.mediaType.name.lowercase().replaceFirstChar { it.titlecase() })
            DetailRow("Quality", entry.quality)
            DetailRow("Format", "${entry.container.uppercase()} • ${MimeTypes.resolve(context, entry.localUri, entry.container)}")
            DetailRow("File size", MimeTypes.displaySize(sizeBytes))
            DetailRow(
                "Downloaded",
                "${RelativeTime.timeAgo(System.currentTimeMillis(), entry.createdAt)} • " +
                    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(entry.createdAt)),
            )
            DetailRow("Location", displayName ?: entry.fileName)
            DetailRow(
                "Status",
                entry.status.name.lowercase().replaceFirstChar { it.titlecase() },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
}
