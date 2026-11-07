package com.fetchly.app.domain.recovery

enum class FailureKind { NETWORK, RATE_LIMIT, BOT_CHECK, SIGN_IN, REGION, REMOVED, DRM, ENGINE, STORAGE, MERGE, UNKNOWN }

/** Recovery is based on the cause, not on whether a message happens to contain 403. */
object FailureRecovery {
    fun kind(raw: String?): FailureKind {
        val text = raw.orEmpty().lowercase()
        fun any(vararg words: String) = words.any { it in text }
        return when {
            any("no space", "enospc", "not enough space", "disk full", "errno 28") -> FailureKind.STORAGE
            any("drm-protected", "drm protected", "drm") -> FailureKind.DRM
            any("429", "too many requests", "rate limit", "limiting requests") -> FailureKind.RATE_LIMIT
            any("not a bot", "captcha", "bot check", "cloudflare challenge") -> FailureKind.BOT_CHECK
            any("sign in", "sign-in", "log in", "login required", "cookies", "private video", "members-only", "authentication", "age-restricted", "isn't publicly available") -> FailureKind.SIGN_IN
            any("available in your country", "region", "geo-restricted", "georestricted") -> FailureKind.REGION
            any("javascript runtime", "challenge solving", "unable to extract", "player response", "nsig", "signature", "precondition", "failed to parse", "source changed", "engine couldn't") -> FailureKind.ENGINE
            any("video unavailable", "removed", "does not exist", "404") -> FailureKind.REMOVED
            any("ffmpeg", "merge failed", "mux", "couldn't be assembled") -> FailureKind.MERGE
            any("timed out", "timeout", "getaddrinfo", "connection", "network", "temporary failure", "proxy", "unreachable", "reset by peer", "incomplete http", "unexpected end") -> FailureKind.NETWORK
            else -> FailureKind.UNKNOWN
        }
    }

    fun nextStep(raw: String?): String = when (kind(raw)) {
        FailureKind.NETWORK -> "Reconnect, then Retry. Eligible direct files continue from saved progress."
        FailureKind.RATE_LIMIT -> "Wait before retrying. Repeated attempts can extend the source's limit."
        FailureKind.BOT_CHECK -> "Open the source to check availability. Browser verification does not sign Fetchly in."
        FailureKind.SIGN_IN -> "Open the source using an account with access. Fetchly downloads public media only."
        FailureKind.REGION -> "Check availability on the source. Fetchly cannot change regional restrictions."
        FailureKind.REMOVED -> "Open the source to confirm the post still exists, or try another link."
        FailureKind.DRM -> "Use the source's official offline feature if available."
        FailureKind.ENGINE -> "Check for an engine update in Settings, reopen Fetchly after installing it, then fetch the link again. Some sources also require capabilities this build does not provide."
        FailureKind.STORAGE -> "Free up space, then Retry. Downloading, merging and saving can require temporary copies."
        FailureKind.MERGE -> "Try another available format or a lower quality. The streams arrived but could not be combined."
        FailureKind.UNKNOWN -> "Retry once, or fetch the source again to choose a different format."
    }
}
