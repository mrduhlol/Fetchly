package com.fetchly.app.data.repository

/**
 * Turns the engine's raw error text into one honest, human sentence.
 *
 * This table is the fix for v1.8's failure mode — a generic "couldn't
 * retrieve media information" fallback that hid the real cause. Rules are
 * checked in order, most specific first; the tests pin both the exact
 * strings and the order (DRM before sign-in, age before sign-in).
 */
object ResolveErrors {

    fun messageFor(raw: String?): String {
        val original = (raw ?: "").trim()
        val text = original.lowercase()
        return when {
            "drm" in text ->
                "This media is DRM-protected, so it can't be downloaded."

            hasAll(text, "age", "confirm") || hasAll(text, "age", "restricted") ->
                "This video is age-restricted. Fetchly can't sign in, so it can't download it."

            hasAny(
                text,
                "private video", "login required", "sign in", "log in",
                "cookies", "members-only", "requires authentication",
            ) ->
                "This media isn't publicly available. It needs a sign-in, or it's private."

            hasAny(text, "available in your country", "geo", "region") ->
                "The source says this media isn't available in your region."

            hasAny(text, "unsupported url", "no suitable extractor") ->
                "This source isn't supported yet."

            hasAny(text, "video unavailable", "removed", "does not exist", "404", "410") ->
                "The source says this media doesn't exist or has been removed."

            hasAny(
                text,
                "unable to extract", "player response", "nsig", "signature",
                "precondition", "failed to parse",
            ) ->
                "The source changed how it serves this media and Fetchly's engine couldn't " +
                    "read it. Updating Fetchly usually fixes this."

            hasAny(
                text,
                "timed out", "timeout", "getaddrinfo", "connection", "network",
                "temporary failure", "proxy",
            ) ->
                "Couldn't reach the source. Check your connection and try again."

            else -> fallback(original)
        }
    }

    private fun fallback(raw: String): String {
        val trimmed = raw.replace(WHITESPACE, " ").trim().take(160)
        return if (trimmed.isEmpty()) {
            "Fetchly couldn't read this link."
        } else {
            "Fetchly couldn't read this link. The engine said: \"$trimmed\""
        }
    }

    private fun hasAll(text: String, vararg parts: String): Boolean = parts.all { it in text }

    private fun hasAny(text: String, vararg parts: String): Boolean = parts.any { it in text }

    private val WHITESPACE = Regex("\\s+")
}
