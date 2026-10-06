package com.fetchly.app.domain.platform

import com.fetchly.app.domain.model.Platform

/**
 * Honest capability matrix. A capability is only true after it actually
 * works — social platforms stay unsupported until a legitimate local or
 * authorized mechanism exists for them. Only DIRECT works today.
 */
data class SourceCapability(
    val platform: Platform,
    val supported: Boolean,
    val supportsVideo: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsImage: Boolean = false,
    val supportsQualitySelection: Boolean = false,
    val supportsThumbnail: Boolean = false,
    val supportsMetadata: Boolean = false,
    val unsupportedReason: String? = null,
)

object SourceCapabilities {

    private const val NEEDS_MECHANISM =
        "This source isn't supported yet. Only direct media links work for now."

    fun all(): List<SourceCapability> = listOf(
        SourceCapability(
            platform = Platform.DIRECT,
            supported = true,
            supportsVideo = true,
            supportsAudio = true,
            supportsImage = true,
            supportsQualitySelection = false,
            supportsThumbnail = true,
            supportsMetadata = true,
        ),
        SourceCapability(Platform.YOUTUBE, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.INSTAGRAM, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.TIKTOK, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.TWITTER, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.REDDIT, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.FACEBOOK, false, unsupportedReason = NEEDS_MECHANISM),
        SourceCapability(Platform.PINTEREST, false, unsupportedReason = NEEDS_MECHANISM),
    )

    fun forPlatform(platform: Platform): SourceCapability =
        all().firstOrNull { it.platform == platform }
            ?: SourceCapability(platform, false, unsupportedReason = NEEDS_MECHANISM)
}
