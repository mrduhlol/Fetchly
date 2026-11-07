package com.fetchly.app.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.fetchly.app.ui.components.PlaqueOutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fetchly.app.ui.components.RecoveryActions
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDetailsSheet(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    fileAvailable: Boolean = entry.outputUri != null,
    onOpenSettings: () -> Unit = {},
    onChooseFormat: () -> Unit = {},
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
            if (entry.status == DownloadStatus.COMPLETED && !fileAvailable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "The file is no longer in your media library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.status == DownloadStatus.FAILED) {
                Spacer(Modifier.height(12.dp))
                RecoveryActions(entry.errorRaw ?: entry.errorMessage, entry.sourceUrl,
                    onOpenSettings = onOpenSettings, onChooseFormat = onChooseFormat)
            }
            if (entry.status == DownloadStatus.COMPLETED) {
                Spacer(Modifier.height(10.dp))
                Text(entry.outputName ?: entry.title, style = MaterialTheme.typography.bodyMedium)
                Text(savedFolder(entry), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!fileAvailable) PlaqueOutlinedButton(onClick = onChooseFormat) { Text("Download again") }
            }
            if (entry.status == DownloadStatus.CANCELLED) {
                Spacer(Modifier.height(10.dp))
                Text("Retry continues eligible direct files from saved progress. Other sources may restart.",
                    style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaqueOutlinedButton(onClick = onOpen, enabled = entry.status == DownloadStatus.COMPLETED && fileAvailable) {
                    Text("Open file")
                }
                PlaqueOutlinedButton(onClick = onShare, enabled = entry.status == DownloadStatus.COMPLETED && fileAvailable) {
                    Text("Share")
                }
                if (entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.CANCELLED) {
                    PlaqueOutlinedButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                PlaqueOutlinedButton(
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


private fun savedFolder(entry: HistoryEntry): String = when (entry.mediaType) {
    com.fetchly.app.domain.model.MediaType.VIDEO -> "Saved in Movies/Fetchly"
    com.fetchly.app.domain.model.MediaType.AUDIO -> "Saved in Music/Fetchly"
    com.fetchly.app.domain.model.MediaType.IMAGE -> "Saved in Pictures/Fetchly"
}
