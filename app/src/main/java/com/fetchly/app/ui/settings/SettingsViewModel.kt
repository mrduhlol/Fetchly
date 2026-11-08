package com.fetchly.app.ui.settings

import com.fetchly.app.data.engine.YtDlpEngine
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.prefs.SettingsStore
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.ThemeChoice
import com.fetchly.app.ui.theme.UiScale
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
    private val engine: YtDlpEngine,
) : ViewModel() {

    data class EngineState(
        val message: String = "Checking installed engine…",
        val busy: Boolean = false,
        val available: Boolean = false,
        val restart: Boolean = false,
        val hasOverride: Boolean = false,
    )
    private val _engineState = MutableStateFlow(EngineState())
    val engineState = _engineState.asStateFlow()
    private val _historyMessage = MutableStateFlow<String?>(null)
    val historyMessage = _historyMessage.asStateFlow()

    init { maintainEngine("status") }

    fun maintainEngine(action: String) {
        if (_engineState.value.busy) return
        _engineState.value = _engineState.value.copy(busy = true, message = when (action) {
            "install" -> "Downloading and verifying the engine update…"
            "check" -> "Checking for an engine update…"
            else -> "Checking installed engine…"
        })
        viewModelScope.launch {
            try {
                val report = JSONObject(engine.maintenance(action))
                _engineState.value = if (report.optBoolean("ok")) EngineState(
                    message = report.optString("message"),
                    available = report.optBoolean("available"),
                    restart = report.optBoolean("restart"),
                    hasOverride = report.optBoolean("override"),
                ) else _engineState.value.copy(busy = false,
                    message = report.optString("message").ifBlank { report.optString("error", "Couldn't update the engine. Try again.") })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _engineState.value = _engineState.value.copy(busy = false, message = "Couldn't update the engine. Check your connection and try again.")
            }
        }
    }

    val theme: StateFlow<ThemeChoice> = settings.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.SYSTEM)

    val defaultQuality: StateFlow<DefaultQuality> = settings.defaultQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultQuality.ASK)

    val uiScale: StateFlow<UiScale> = settings.uiScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiScale.COMPACT)

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { settings.setTheme(choice) }
    }

    fun setDefaultQuality(choice: DefaultQuality) {
        viewModelScope.launch { settings.setDefaultQuality(choice) }
    }

    fun setUiScale(choice: UiScale) {
        viewModelScope.launch { settings.setUiScale(choice) }
    }

    /** Empties the list; files already published stay in the media library. */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                history.clear()
                _historyMessage.value = "Finished history cleared. Active downloads and saved files stay."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _historyMessage.value = "Couldn't clear history. Try again."
            }
        }
    }
}
