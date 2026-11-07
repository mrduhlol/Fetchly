package com.fetchly.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Primary actions keep the flat ivory treatment in both palettes. */
object FetchlyButtonColors {
    val container: Color @Composable get() = com.fetchly.app.ui.theme.ActionIvory
    val content: Color @Composable get() = com.fetchly.app.ui.theme.OnActionIvory
}
