package com.fetchly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fetchly.app.domain.model.DefaultQuality
import com.fetchly.app.domain.model.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Top-level so every SettingsStore instance shares one DataStore.
private val Context.settingsData by preferencesDataStore(name = "fetchly_settings")

/** User preferences: appearance and default quality. Nothing else. */
class SettingsStore(context: Context) {

    // Hold the application context: the store outlives any screen that made it.
    private val context = context.applicationContext

    val theme: Flow<ThemeChoice> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> ThemeChoice.fromName(preferences[KEY_THEME]) }

    val defaultQuality: Flow<DefaultQuality> = context.settingsData.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> DefaultQuality.fromName(preferences[KEY_DEFAULT_QUALITY]) }

    suspend fun setTheme(choice: ThemeChoice) {
        context.settingsData.edit { preferences -> preferences[KEY_THEME] = choice.name }
    }

    suspend fun setDefaultQuality(choice: DefaultQuality) {
        context.settingsData.edit { preferences -> preferences[KEY_DEFAULT_QUALITY] = choice.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    }
}
