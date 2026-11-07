package com.fetchly.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Section label used across the list screens. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, horizontalPadding: Dp = 20.dp) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = horizontalPadding, end = horizontalPadding, top = 24.dp, bottom = 8.dp),
    )
}
