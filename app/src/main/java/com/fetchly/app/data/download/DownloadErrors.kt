package com.fetchly.app.data.download

import com.fetchly.app.domain.recovery.FailureKind
import com.fetchly.app.domain.recovery.FailureRecovery
import com.fetchly.app.data.repository.ResolveErrors

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

            FailureRecovery.kind(original) in setOf(FailureKind.BOT_CHECK, FailureKind.SIGN_IN,
                FailureKind.REGION, FailureKind.RATE_LIMIT, FailureKind.DRM, FailureKind.ENGINE,
                FailureKind.REMOVED) -> ResolveErrors.messageFor(original)

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
        return FailureRecovery.kind(raw) !in setOf(FailureKind.BOT_CHECK, FailureKind.SIGN_IN,
            FailureKind.REGION, FailureKind.RATE_LIMIT, FailureKind.DRM, FailureKind.ENGINE) &&
            hasAny(text, "403", "410", "forbidden")
    }

    fun isNetworkProblem(raw: String?): Boolean {
        val text = (raw ?: "").trim().lowercase()
        if (FailureRecovery.kind(raw) in setOf(FailureKind.BOT_CHECK, FailureKind.SIGN_IN,
                FailureKind.REGION, FailureKind.DRM, FailureKind.ENGINE, FailureKind.RATE_LIMIT)) return false
        return hasAny(
            text,
            "timed out", "timeout", "temporary failure", "getaddrinfo",
            "name or service not known", "connection", "network", "proxy",
            "unreachable", "reset by peer", "eof occurred", "incomplete http", "unexpected end",
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
