package com.fetchly.app.domain.util

import android.content.Context
import android.net.Uri

/** MIME handling: never expose raw paths, always match the actual file. */
object MimeTypes {

    fun fromContainer(container: String): String = when (container.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    /** Real MIME from the content provider, falling back to the container mapping. */
    fun resolve(context: Context, uriString: String?, container: String): String {
        if (uriString != null) {
            runCatching {
                context.contentResolver.getType(Uri.parse(uriString))
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return fromContainer(container)
    }

    /** File extension matching the actual container (never trust raw suffixes). */
    fun extensionFor(container: String): String = when (container.lowercase()) {
        "jpeg" -> "jpg"
        "m4v" -> "mp4"
        "oga" -> "ogg"
        else -> container.lowercase().take(5).ifBlank { "mp4" }
    }

    fun displaySize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "Unknown size"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return if (mb < 10) "%.1f MB".format(mb) else "%.0f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
