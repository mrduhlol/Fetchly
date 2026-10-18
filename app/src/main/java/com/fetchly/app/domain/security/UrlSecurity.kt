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
        val host = (uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: authorityHost(uri))
            .orEmpty()
        if (host.isEmpty()) return UrlValidation.Invalid("That doesn't look like a complete link.")
        if (isPrivateOrLocalHost(host)) {
            return UrlValidation.Invalid("Local network links aren't supported.")
        }
        return UrlValidation.Valid(text)
    }

    /**
     * Java's URI refuses to call numeric-short hosts like "127.1" or
     * "0x7f000001" a host, so those come back null. Pull the host part out of
     * the authority by hand so the private-address check still sees it.
     */
    private fun authorityHost(uri: URI): String? {
        val authority = uri.rawAuthority ?: return null
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isEmpty()) return null
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']').removePrefix("[")
        } else {
            hostPort.substringBefore(':')
        }
        return host.ifEmpty { null }
    }

    /**
     * Share sheets and clipboards carry prose, emoji and punctuation around
     * the link. Pull the first usable http(s) URL out of it.
     */
    fun extractFirstUrl(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        for (match in URL_CANDIDATE.findAll(t)) {
            val candidate = trimJunk(match.value)
            if (candidate.isEmpty()) continue
            val result = validate(candidate)
            if (result is UrlValidation.Valid) return result.url
        }
        return null
    }

    /**
     * The first http(s) candidate before validation — lets the UI explain
     * *why* a shared link was refused instead of silently doing nothing.
     */
    fun firstUrlCandidate(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        for (match in URL_CANDIDATE.findAll(t)) {
            val candidate = trimJunk(match.value)
            if (candidate.isNotEmpty()) return candidate
        }
        return null
    }

    private fun trimJunk(raw: String): String {
        var candidate = raw
        while (candidate.isNotEmpty() &&
            (candidate.last() in TRAILING_JUNK || candidate.last().code > 127)
        ) {
            candidate = candidate.dropLast(1)
        }
        return candidate
    }

    /**
     * True for loopback, LAN, link-local and other non-public addresses —
     * including the many textual forms an IPv4 address can take
     * ("127.1", "2130706433", "0x7f000001") and IPv4-mapped IPv6 literals.
     * A pasted link must never make the phone hit something on its own
     * network.
     */
    fun isPrivateOrLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']').removeSuffix(".")
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        if (h.contains(':')) return isPrivateIpv6(h)
        parseIpv4(h)?.let { value -> return isPrivateIpv4(value) }
        return false
    }

    // ---------------------------------------------------- address literals

    private fun isPrivateIpv4(value: Long): Boolean {
        val a = ((value shr 24) and 0xFF).toInt()
        val b = ((value shr 16) and 0xFF).toInt()
        return a == 0 || a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }

    /**
     * inet_aton-style parsing: 1–4 dot-separated parts, each decimal,
     * octal ("0177") or hex ("0x7f"); the last part absorbs the remaining
     * bytes ("127.1" == 127.0.0.1). Returns null when the text isn't a
     * numeric address at all (e.g. an ordinary hostname).
     */
    private fun parseIpv4(host: String): Long? {
        val parts = host.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        var value = 0L
        parts.forEachIndexed { index, part ->
            val isLast = index == parts.size - 1
            val bits = if (isLast) 8 * (4 - index) else 8
            val max = if (bits >= 32) 0xFFFF_FFFFL else (1L shl bits) - 1
            val parsed = parseIpv4Part(part) ?: return null
            if (parsed > max) return null
            value = (value shl bits) or parsed
        }
        return value
    }

    private fun parseIpv4Part(part: String): Long? {
        if (part.isEmpty()) return null
        return when {
            part.startsWith("0x") || part.startsWith("0X") ->
                part.drop(2).takeIf { it.isNotEmpty() }?.toLongOrNull(16)
            part.length > 1 && part.startsWith("0") -> part.drop(1).toLongOrNull(8)
            else -> part.toLongOrNull()
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        val hextets = expandIpv6(host) ?: return false
        val first = hextets[0]
        if ((first and 0xFE00) == 0xFC00) return true // fc00::/7 unique local
        if ((first and 0xFFC0) == 0xFE80) return true // fe80::/10 link local
        if (hextets.all { it == 0 }) return true // ::
        if (hextets.take(7).all { it == 0 } && hextets[7] == 1) return true // ::1
        if (hextets.take(5).all { it == 0 } && hextets[5] == 0xFFFF) { // ::ffff:a.b.c.d
            val mapped = (hextets[6].toLong() shl 16) or hextets[7].toLong()
            return isPrivateIpv4(mapped)
        }
        return false
    }

    /** Full 8-hextet expansion of an IPv6 literal; null when not parseable. */
    private fun expandIpv6(host: String): List<Int>? {
        val h = host.substringBefore('%')
        val doubleColon = h.contains("::")
        val sides = h.split("::", limit = 2)

        fun parseSide(side: String): List<Int>? {
            if (side.isEmpty()) return emptyList()
            val out = mutableListOf<Int>()
            for (token in side.split(':')) {
                if (token.isEmpty()) return null
                if (token.contains('.')) {
                    val quad = parseIpv4(token) ?: return null
                    out += ((quad shr 16) and 0xFFFF).toInt()
                    out += (quad and 0xFFFF).toInt()
                } else {
                    val value = token.toIntOrNull(16) ?: return null
                    if (value !in 0..0xFFFF) return null
                    out += value
                }
            }
            return out
        }

        val left = parseSide(sides[0]) ?: return null
        val right = if (doubleColon) parseSide(sides.getOrElse(1) { "" }) ?: return null else emptyList()
        val missing = 8 - left.size - right.size
        if (!doubleColon && missing != 0) return null
        if (doubleColon && missing < 0) return null
        return left + List(if (doubleColon) missing else 0) { 0 } + right
    }

    private val URL_CANDIDATE = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_JUNK = ".,;:!?'\")]}>…"
}
