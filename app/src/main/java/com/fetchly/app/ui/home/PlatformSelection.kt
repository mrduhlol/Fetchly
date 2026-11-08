package com.fetchly.app.ui.home

import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind

enum class FetchMode(val label: String, val kind: QualityKind?) {
    VIDEO("Video", QualityKind.VIDEO), AUDIO("Audio", QualityKind.AUDIO),
    INSTAGRAM_AUDIO("Reels / post audio", QualityKind.AUDIO),
    REELS("Reels", QualityKind.VIDEO), POST("Post · single / bulk", null),
    VIDEO_AUDIO("Video’s audio", QualityKind.AUDIO),
}

enum class SupportedApp(val platform: Platform, val label: String, val modes: List<FetchMode>) {
    YOUTUBE(Platform.YOUTUBE, "YouTube", listOf(FetchMode.VIDEO, FetchMode.AUDIO)),
    INSTAGRAM(Platform.INSTAGRAM, "Instagram", listOf(FetchMode.INSTAGRAM_AUDIO, FetchMode.REELS, FetchMode.POST)),
    TIKTOK(Platform.TIKTOK, "TikTok", listOf(FetchMode.VIDEO, FetchMode.AUDIO)),
    X(Platform.TWITTER, "X", listOf(FetchMode.VIDEO, FetchMode.VIDEO_AUDIO)),
    REDDIT(Platform.REDDIT, "Reddit", listOf(FetchMode.VIDEO, FetchMode.AUDIO)),
    FACEBOOK(Platform.FACEBOOK, "Facebook", listOf(FetchMode.VIDEO, FetchMode.AUDIO)),
    PINTEREST(Platform.PINTEREST, "Pinterest", listOf(FetchMode.VIDEO)),
    LINKEDIN(Platform.LINKEDIN, "LinkedIn", listOf(FetchMode.VIDEO, FetchMode.AUDIO)),
    YOUTUBE_MUSIC(Platform.YOUTUBE, "YouTube Music", listOf(FetchMode.AUDIO)),
}
