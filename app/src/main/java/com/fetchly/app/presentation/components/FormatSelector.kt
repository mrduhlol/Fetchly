package com.fetchly.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fetchly.app.domain.model.MediaFormat

@Composable
fun FormatSelector(
    formats: List<MediaFormat>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        formats.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = f.id == selectedId,
                        role = Role.RadioButton,
                        onClick = { onSelect(f.id) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = f.id == selectedId, onClick = { onSelect(f.id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.quality, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${f.container.uppercase()}${f.sizeBytes?.let { " • ${it / 1024 / 1024} MB" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
