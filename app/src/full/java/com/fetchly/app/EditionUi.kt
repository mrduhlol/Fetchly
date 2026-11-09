package com.fetchly.app
import androidx.compose.runtime.Composable
import com.fetchly.app.data.AppGraph
object EditionUi {
    @Composable fun Root(graph: AppGraph, requestNotifications: (() -> Unit) -> Unit) = Unit
    @Composable fun Share(graph: AppGraph, text: String, requestNotifications: (() -> Unit) -> Unit, close: () -> Unit) = Unit
}
