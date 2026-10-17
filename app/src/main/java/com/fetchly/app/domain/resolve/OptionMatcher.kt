package com.fetchly.app.domain.resolve

import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef

/**
 * Finds the freshly resolved option that corresponds to a stored stream
 * signature (used on retry, after re-resolving, when yt-dlp's format ids may
 * have changed).
 *
 * Primary key: the multiset of (height, vcodec, acodec, ext) across the
 * option's streams. Fallback: (height, ext) only. No match → null, and the
 * caller asks the user to pick again.
 */
object OptionMatcher {

    fun match(signature: List<StreamRef>, options: List<QualityOption>): QualityOption? {
        if (signature.isEmpty()) return null

        val primary = signature.map { it.primaryKey() }.sorted()
        options.firstOrNull { option ->
            option.streams.map { it.primaryKey() }.sorted() == primary
        }?.let { return it }

        val fallback = signature.map { it.fallbackKey() }.sorted()
        return options.firstOrNull { option ->
            option.streams.map { it.fallbackKey() }.sorted() == fallback
        }
    }

    private fun StreamRef.primaryKey(): String = "$height|$vcodec|$acodec|$ext"

    private fun StreamRef.fallbackKey(): String = "$height|$ext"
}
