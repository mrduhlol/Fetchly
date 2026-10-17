package com.fetchly.app.domain.util

object MimeTypes {
    fun forContainer(container: String, audio: Boolean = false): String = when (container.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        "mov" -> "video/quicktime"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    /** Is this a mime type Fetchly is willing to download as a direct file? */
    fun isMediaMime(mime: String?): Boolean {
        val m = mime?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return m.startsWith("video/") || m.startsWith("audio/") || m.startsWith("image/") ||
            m == "application/octet-stream"
    }

    fun extensionFor(container: String): String = container.lowercase()
}
