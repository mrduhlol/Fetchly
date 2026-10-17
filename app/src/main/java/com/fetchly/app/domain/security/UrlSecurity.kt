package com.fetchly.app.domain.security

import java.net.URI

sealed interface UrlValidation {
    data class Valid(val url: String) : UrlValidation
    data class Invalid(val reason: String) : UrlValidation
}

/**
 * What Fetchly accepts as a fetch input: one http(s) URL, nothing else.
 *
 * Local/private hosts are refused so a pasted link can never make the phone
 * hit something on its own network. Invalid reasons are user-facing copy and
 * say exactly what is wrong.
 */
object UrlSecurity {
    const val MAX_URL_LENGTH = 2048

    fun validate(raw: String?): UrlValidation {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return UrlValidation.Invalid("Paste a link first.")
        if (text.length > MAX_URL_LENGTH) {
            return UrlValidation.Invalid("That link is too long to be a media link.")
        }
        if (text.any { it.isWhitespace() }) {
            return UrlValidation.Invalid("That doesn't look like a link — it contains spaces.")
        }
        val uri = runCatching { URI(text) }.getOrNull()
            ?: return UrlValidation.Invalid("That doesn't look like a link.")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlValidation.Invalid(
                if (scheme == null) {
                    "That doesn't look like a link — links need to start with http:// or https://."
                } else {
                    "Only http and https links are supported."
                }
            )
        }
        val host = uri.host?.lowercase().orEmpty()
        if (host.isEmpty()) return UrlValidation.Invalid("That doesn't look like a complete link.")
        if (isPrivateOrLocalHost(host)) {
            return UrlValidation.Invalid("Local network links aren't supported.")
        }
        return UrlValidation.Valid(text)
    }

    /**
     * Share sheets and clipboards carry prose, emoji and punctuation around
     * the link. Pull the first usable http(s) URL out of it.
     */
    fun extractFirstUrl(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        for (match in URL_CANDIDATE.findAll(t)) {
            var candidate = match.value
            while (candidate.isNotEmpty() &&
                (candidate.last() in TRAILING_JUNK || candidate.last().code > 127)
            ) {
                candidate = candidate.dropLast(1)
            }
            if (candidate.isEmpty()) continue
            val result = validate(candidate)
            if (result is UrlValidation.Valid) return result.url
        }
        return null
    }

    fun isPrivateOrLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']').removeSuffix(".")
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        if (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")) return true
        val parts = h.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }

    private val URL_CANDIDATE = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_JUNK = ".,;:!?'\")]}>…"
}
