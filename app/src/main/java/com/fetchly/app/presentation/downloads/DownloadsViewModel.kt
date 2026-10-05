package com.fetchly.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.AppGraph
import com.fetchly.app.domain.model.HistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel : ViewModel() {

    val history = AppGraph.history.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entry: HistoryEntry) = viewModelScope.launch {
        AppGraph.history.delete(entry)
    }

    fun clear() = viewModelScope.launch {
        AppGraph.history.clear()
    }
}
