package com.fetchly.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.prefs.SettingsStore
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.ThemeChoice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings' brain: appearance and download defaults from the preference
 * store, plus the one history action. There is deliberately nothing else a
 * user can configure — Fetchly runs entirely on this device.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val history: HistoryRepository,
) : ViewModel() {

    val theme: StateFlow<ThemeChoice> = settings.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.SYSTEM)

    val defaultQuality: StateFlow<DefaultQuality> = settings.defaultQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultQuality.ASK)

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { settings.setTheme(choice) }
    }

    fun setDefaultQuality(choice: DefaultQuality) {
        viewModelScope.launch { settings.setDefaultQuality(choice) }
    }

    /** Empties the list; files already published stay in the media library. */
    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }
}
