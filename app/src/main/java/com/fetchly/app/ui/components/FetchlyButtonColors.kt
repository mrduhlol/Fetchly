package com.fetchly.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Primary actions keep the gold plaque treatment in both palettes. */
object FetchlyButtonColors {
    val container: Color @Composable get() = com.fetchly.app.ui.theme.ActionGold
    val content: Color @Composable get() = com.fetchly.app.ui.theme.OnActionGold
}
