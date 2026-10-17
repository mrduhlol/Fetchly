package com.fetchly.app.domain.model

/** How Fetchly picks its colors. */
enum class ThemeChoice {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeChoice =
            ThemeChoice.entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** What a fresh fetch preselects on the result screen. */
enum class DefaultQuality {
    ASK, BEST;

    companion object {
        fun fromName(name: String?): DefaultQuality =
            DefaultQuality.entries.firstOrNull { it.name == name } ?: ASK
    }
}
