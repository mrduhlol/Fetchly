package com.fetchly.app.data.prefs

import kotlinx.coroutines.flow.Flow

/** Completion is durable before the main app is entered. */
interface WelcomePreferences {
    val welcomeCompleted: Flow<Boolean>
    suspend fun completeWelcome()
}
