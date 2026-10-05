package com.fetchly.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.download.AppGraph
import com.fetchly.app.data.prefs.DefaultQuality
import com.fetchly.app.data.prefs.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    val theme = AppGraph.settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val defaultQuality = AppGraph.settings.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultQuality.ASK)
    val apiBaseUrl = AppGraph.settings.apiBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        AppGraph.settings.setTheme(mode)
    }

    fun setQuality(q: DefaultQuality) = viewModelScope.launch {
        AppGraph.settings.setDefaultQuality(q)
    }

    fun setApiBase(url: String) = viewModelScope.launch {
        AppGraph.settings.setApiBaseUrl(url)
    }

    fun clearHistory() = viewModelScope.launch {
        AppGraph.history.clear()
    }
}
