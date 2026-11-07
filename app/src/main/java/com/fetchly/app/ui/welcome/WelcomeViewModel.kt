package com.fetchly.app.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.data.prefs.WelcomePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WelcomeUiState(
    // null keeps Home hidden until the saved first-launch state has actually loaded.
    val completed: Boolean? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

class WelcomeViewModel(private val preferences: WelcomePreferences) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.welcomeCompleted.collect { completed ->
                _state.update { it.copy(completed = completed) }
            }
        }
    }

    fun getStarted() {
        if (_state.value.saving || _state.value.completed != false) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                preferences.completeWelcome()
                _state.update { it.copy(completed = true, saving = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, error = "Couldn't save your welcome preference. Try again.") }
            }
        }
    }
}
