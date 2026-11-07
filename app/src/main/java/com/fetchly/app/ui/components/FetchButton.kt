package com.fetchly.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import com.fetchly.app.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The one big button; shows a spinner and honest "Fetching…" while busy. */
@Composable
fun FetchButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FetchlyButtonColors.container, contentColor = FetchlyButtonColors.content,
            disabledContainerColor = FetchlyButtonColors.container.copy(alpha = 0.85f),
            disabledContentColor = FetchlyButtonColors.content.copy(alpha = 0.6f),
        ),
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text("Fetching…")
        } else {
            Icon(painterResource(R.drawable.ic_stat_download), null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("FETCH MEDIA")
        }
    }
}
