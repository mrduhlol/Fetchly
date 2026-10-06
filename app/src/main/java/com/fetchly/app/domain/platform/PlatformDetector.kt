package com.fetchly.app.domain.platform

import com.fetchly.app.domain.model.Platform

/**
 * Pure client-side URL -> platform detection. No network, no scraping.
 * Returns UNKNOWN for anything not on the supported list.
 */
object PlatformDetector {

    fun detect(rawUrl: String): Platform {
        val host = runCatching {
            val normalized = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
            java.net.URI(normalized.trim()).host?.lowercase()?.removePrefix("www.") ?: return Platform.UNKNOWN
        }.getOrElse { return Platform.UNKNOWN }

        return when {
            host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") -> Platform.YOUTUBE
            host == "instagram.com" || host.endsWith(".instagram.com") -> Platform.INSTAGRAM
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            host == "twitter.com" || host == "x.com" ||
                host.endsWith(".twitter.com") || host.endsWith(".x.com") -> Platform.TWITTER
            host == "reddit.com" || host.endsWith(".reddit.com") -> Platform.REDDIT
            host == "facebook.com" || host == "fb.watch" || host.endsWith(".facebook.com") -> Platform.FACEBOOK
            host == "pinterest.com" || host.endsWith(".pinterest.com") -> Platform.PINTEREST
            else -> Platform.UNKNOWN
        }
    }

    fun isSupported(rawUrl: String): Boolean = detect(rawUrl) != Platform.UNKNOWN

    private val DIRECT_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mkv",
        "mp3", "m4a", "ogg", "oga", "wav", "flac",
        "jpg", "jpeg", "png", "webp", "gif",
    )

    /**
     * Extension hint for direct media files. The resolver still verifies the
     * real content type over the network — this only guides the UI message.
     */
    fun detectWithDirect(rawUrl: String): Platform {
        val path = runCatching {
            val normalized = if (rawUrl.contains("://")) rawUrl.trim() else "https://${rawUrl.trim()}"
            java.net.URI(normalized).path?.lowercase().orEmpty()
        }.getOrElse { return detect(rawUrl) }
        val ext = path.substringAfterLast('.', "").substringBefore('?')
        if (ext in DIRECT_EXTENSIONS) return Platform.DIRECT
        return detect(rawUrl)
    }
}
