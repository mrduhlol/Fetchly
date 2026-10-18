package com.fetchly.app.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.util.MimeTypes
import com.fetchly.app.domain.util.RelativeTime

/**
 * Details for one history entry: what it is, what went wrong (if anything),
 * and the four things a user can do with it. Opening and sharing happen in
 * the host screen, which owns the Context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                detailsLine(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.errorMessage?.takeIf { entry.status != DownloadStatus.COMPLETED }?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (entry.status == DownloadStatus.COMPLETED && entry.outputUri == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "The file is no longer in your media library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpen, enabled = entry.outputUri != null) {
                    Text("Open")
                }
                OutlinedButton(onClick = onShare, enabled = entry.outputUri != null) {
                    Text("Share")
                }
                if (entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.CANCELLED) {
                    OutlinedButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun detailsLine(entry: HistoryEntry): String = listOf(
    PlatformDetector.label(entry.platform),
    entry.optionLabel,
    RelativeTime.format(entry.updatedAt),
).joinToString(" · ")

/** Shared helpers for acting on an entry's published file. */
internal object EntryFiles {

    fun mimeOf(entry: HistoryEntry): String =
        MimeTypes.forContainer(entry.container, audio = entry.kind == QualityKind.AUDIO)

    fun open(context: Context, entry: HistoryEntry) {
        val uri = entry.outputUri?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(entry))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
    }

    fun share(context: Context, entry: HistoryEntry) {
        val uri = entry.outputUri?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeOf(entry))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, entry.title)) }
    }
}
