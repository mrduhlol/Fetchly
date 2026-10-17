package com.fetchly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Appearance setting; SYSTEM follows the phone. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** What a fresh quality list preselects: nothing (ASK) or the top option (BEST). */
enum class DefaultQuality { ASK, BEST }

private val Context.settingsDataStore by preferencesDataStore(name = "fetchly_settings")

/**
 * The whole settings surface: theme and default quality. Deliberately no
 * API/endpoint/URL setting — Fetchly has no backend and nothing to point at.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val theme: Flow<ThemeChoice> = store.data.map { prefs ->
        prefs[THEME]?.asEnum(ThemeChoice.SYSTEM) ?: ThemeChoice.SYSTEM
    }

    val defaultQuality: Flow<DefaultQuality> = store.data.map { prefs ->
        prefs[DEFAULT_QUALITY]?.asEnum(DefaultQuality.ASK) ?: DefaultQuality.ASK
    }

    suspend fun setTheme(choice: ThemeChoice) {
        store.edit { it[THEME] = choice.name }
    }

    suspend fun setDefaultQuality(choice: DefaultQuality) {
        store.edit { it[DEFAULT_QUALITY] = choice.name }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    }
}

private inline fun <reified T : Enum<T>> String.asEnum(fallback: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: fallback
