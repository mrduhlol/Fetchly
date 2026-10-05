package com.fetchly.app.domain.security

/** Treat every pasted URL as untrusted. Validation + sanitization live here. */
object UrlSecurity {

    private const val MAX_URL_LENGTH = 2048
    const val MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB guard

    fun validate(raw: String): Result<String> {
        val url = raw.trim()
        if (url.isEmpty()) return Result.failure(UrlError.Empty)
        if (url.length > MAX_URL_LENGTH) return Result.failure(UrlError.TooLong)
        if (url.contains(' ')) return Result.failure(UrlError.Invalid)
        val withScheme = if (url.contains("://")) url else "https://$url"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull()
            ?: return Result.failure(UrlError.Invalid)
        val scheme = uri.scheme?.lowercase() ?: return Result.failure(UrlError.Invalid)
        if (scheme != "http" && scheme != "https") return Result.failure(UrlError.BadScheme)
        val host = uri.host ?: return Result.failure(UrlError.Invalid)
        if (!host.contains('.')) return Result.failure(UrlError.Invalid)
        return Result.success(withScheme)
    }

    /** Strip characters filesystems/MediaStore dislike; never trust remote filenames. */
    fun sanitizeFileName(raw: String, fallback: String = "fetchly_media"): String {
        var name = raw.trim().take(120)
        name = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        name = name.replace(Regex("\\s+"), " ").trim().trim('.', '_')
        if (name.isEmpty()) return fallback
        return name
    }

    sealed class UrlError(message: String) : IllegalArgumentException(message) {
        data object Empty : UrlError("Enter a valid media link.")
        data object TooLong : UrlError("That link is too long to process.")
        data object Invalid : UrlError("Enter a valid media link.")
        data object BadScheme : UrlError("Only http(s) links are supported.")
    }
}
