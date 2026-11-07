package com.fetchly.app.domain.util

import com.fetchly.app.domain.model.QualityOption
import java.util.Locale

/**
 * Names Fetchly writes into the media library.
 *
 * Titles are strangers' metadata, so [sanitize] is strict: the result is
 * never empty and contains nothing MediaStore or a filesystem could refuse.
 * [displayName] adds the chosen quality so two downloads of one media don't
 * collide ("Video [1080p].mp4" vs "Video [720p].mp4"), and [tempPrefix]
 * gives every download a stable stem for its scratch files.
 */
object Filenames {

    const val MAX_STEM_LENGTH = 80
    const val FALLBACK_STEM = "Fetchly media"

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")

    /** A safe, non-empty title stem: no separators, no control characters. */
    fun sanitize(title: String): String {
        val cleaned = WHITESPACE
            .replace(ILLEGAL.replace(title, " "), " ")
            .trim()
            .trim('.')
        val stem = clip(cleaned, MAX_STEM_LENGTH, 180).trim().trim('.')
        return stem.ifEmpty { FALLBACK_STEM }
    }

    /** "Title [1080p].mp4" — the name MediaStore shows and the user opens. */
    fun displayName(title: String, option: QualityOption): String =
        displayName(title, option.label, option.container)

    fun displayName(title: String, label: String, container: String): String {
        val cleanLabel = clip(sanitize(label), 40, 60)
        val ext = container.trim().lowercase(Locale.US).trimStart('.').takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
        return "${sanitize(title)} [$cleanLabel].$ext"
    }

    private fun clip(value: String, units: Int, bytes: Int): String {
        val result = StringBuilder()
        var usedBytes = 0
        var index = 0
        while (index < value.length) {
            val code = value.codePointAt(index)
            val part = String(Character.toChars(code))
            val size = part.toByteArray(Charsets.UTF_8).size
            if (result.length + part.length > units || usedBytes + size > bytes) break
            if (code !in 0xD800..0xDFFF) { result.append(part); usedBytes += size }
            index += Character.charCount(code)
        }
        return result.toString()
    }

    /** Scratch-file stem for one download; UUIDs pass through unchanged. */
    fun tempPrefix(downloadId: String): String =
        "fetchly_" + downloadId
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(48)
}
