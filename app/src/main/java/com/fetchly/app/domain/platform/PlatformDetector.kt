package com.fetchly.app.domain.platform

import com.fetchly.app.domain.model.Platform
import java.net.URI

enum class Capability { SUPPORTED, BEST_EFFORT }

/**
 * Host-based platform detection. Only YouTube and plain direct-file links are
 * promised to work well; everything else is best-effort through yt-dlp, and
 * failures stay honest rather than pretending the platform is unsupported.
 */
object PlatformDetector {
    private val MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mkv", "mov",
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac",
        "jpg", "jpeg", "png", "gif", "webp",
    )

    fun detect(url: String): Platform {
        val host = hostOf(url)
        if (host.isEmpty()) return Platform.OTHER
        return when {
            host == "youtu.be" || matches(host, "youtube.com") -> Platform.YOUTUBE
            matches(host, "instagram.com") -> Platform.INSTAGRAM
            matches(host, "tiktok.com") -> Platform.TIKTOK
            matches(host, "x.com") || matches(host, "twitter.com") -> Platform.TWITTER
            matches(host, "reddit.com") || host == "redd.it" -> Platform.REDDIT
            matches(host, "facebook.com") || matches(host, "fb.com") || host == "fb.watch" -> Platform.FACEBOOK
            matches(host, "pinterest.com") || host == "pin.it" -> Platform.PINTEREST
            hasMediaExtension(url) -> Platform.DIRECT
            else -> Platform.OTHER
        }
    }

    fun capability(platform: Platform): Capability = when (platform) {
        Platform.YOUTUBE, Platform.DIRECT -> Capability.SUPPORTED
        else -> Capability.BEST_EFFORT
    }

    /** host is the domain itself or one of its subdomains (www., m., music., …). */
    private fun matches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull().orEmpty()

    private fun hasMediaExtension(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext in MEDIA_EXTENSIONS
    }
}
