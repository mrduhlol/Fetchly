package com.fetchly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.ThemeChoice
import com.fetchly.app.ui.theme.UiScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Top-level so every SettingsStore instance shares one DataStore.
private val Context.settingsData by preferencesDataStore(name = "fetchly_settings")

/** Local appearance, download defaults, and first-launch completion. */
class SettingsStore(context: Context) : WelcomePreferences {

    // Hold the application context: the store outlives any screen that made it.
    private val context = context.applicationContext

    override val welcomeCompleted: Flow<Boolean> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[KEY_WELCOME_COMPLETED] ?: false }

    override suspend fun completeWelcome() {
        context.settingsData.edit { preferences -> preferences[KEY_WELCOME_COMPLETED] = true }
    }

    val theme: Flow<ThemeChoice> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> ThemeChoice.fromName(preferences[KEY_THEME]) }

    val defaultQuality: Flow<DefaultQuality> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> DefaultQuality.fromName(preferences[KEY_DEFAULT_QUALITY]) }

    val uiScale: Flow<UiScale> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> UiScale.fromPercent(preferences[KEY_UI_SCALE]) }

    suspend fun setTheme(choice: ThemeChoice) {
        context.settingsData.edit { preferences -> preferences[KEY_THEME] = choice.name }
    }

    suspend fun setDefaultQuality(choice: DefaultQuality) {
        context.settingsData.edit { preferences -> preferences[KEY_DEFAULT_QUALITY] = choice.name }
    }

    suspend fun setUiScale(choice: UiScale) {
        context.settingsData.edit { preferences -> preferences[KEY_UI_SCALE] = choice.percent }
    }

    private companion object {
        val KEY_WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val KEY_UI_SCALE = intPreferencesKey("ui_scale_percent")
    }
}
