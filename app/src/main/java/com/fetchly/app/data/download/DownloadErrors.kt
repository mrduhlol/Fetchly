package com.fetchly.app.data.download

/**
 * Turns a download or merge failure's raw text into one honest sentence.
 *
 * Same rules style as [com.fetchly.app.data.repository.ResolveErrors]: first
 * match wins, most specific first. The expired-link row is only surfaced
 * after the worker already re-resolved the link once, so "even a fresh one
 * was refused" is the truth whenever the user reads it.
 */
object DownloadErrors {

    fun textFor(raw: String?): String {
        val original = (raw ?: "").trim()
        val text = original.lowercase()
        return when {
            hasAny(text, "no space", "enospc", "not enough space", "disk full", "errno 28") ->
                "Not enough free storage for this download."

            hasAny(text, "ffmpeg", "merge", "mux") ->
                "The media was downloaded but couldn't be assembled (merge failed)."

            isExpiredLink(original) ->
                "The download link expired and even a fresh one was refused. Try again later."

            isNetworkProblem(original) ->
                "The download lost its connection. Check your network and try again."

            else -> fallback(original)
        }
    }

    /** 403/410-style refusals: worth one re-resolve before telling the user. */
    fun isExpiredLink(raw: String?): Boolean {
        val text = (raw ?: "").trim().lowercase()
        return hasAny(text, "403", "410", "forbidden")
    }

    fun isNetworkProblem(raw: String?): Boolean {
        val text = (raw ?: "").trim().lowercase()
        return hasAny(
            text,
            "timed out", "timeout", "temporary failure", "getaddrinfo",
            "name or service not known", "connection", "network", "proxy",
            "unreachable", "reset by peer", "eof occurred",
        )
    }

    private fun fallback(raw: String): String {
        val cleaned = WHITESPACE.replace(raw, " ").trim().take(160)
        return if (cleaned.isEmpty()) {
            "The download failed before it started."
        } else {
            "The download failed. The engine said: \"$cleaned\""
        }
    }

    private fun hasAny(text: String, vararg parts: String): Boolean = parts.any { it in text }

    private val WHITESPACE = Regex("\\s+")
}
