package com.fetchly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("fetchly_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DefaultQuality { BEST, ASK }

class SettingsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val qualityKey = stringPreferencesKey("default_quality")
    private val apiBaseKey = stringPreferencesKey("api_base_url")

    val theme: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val defaultQuality: Flow<DefaultQuality> = context.dataStore.data.map {
        runCatching { DefaultQuality.valueOf(it[qualityKey] ?: "ASK") }.getOrDefault(DefaultQuality.ASK)
    }
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[apiBaseKey].orEmpty() }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDefaultQuality(q: DefaultQuality) {
        context.dataStore.edit { it[qualityKey] = q.name }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[apiBaseKey] = url.trim() }
    }
}
