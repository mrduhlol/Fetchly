package com.fetchly.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Result : Route
    @Serializable data object Downloads : Route
    @Serializable data object History : Route
    @Serializable data object Settings : Route
    @Serializable data object About : Route
}
