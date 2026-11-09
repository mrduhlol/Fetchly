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

        return options.firstOrNull { matches(signature, it, primary = true) }
            ?: options.firstOrNull { matches(signature, it, primary = false) }
    }

    private fun matches(signature: List<StreamRef>, option: QualityOption, primary: Boolean): Boolean {
        if (signature.size != option.streams.size) return false
        val remaining = option.streams.toMutableList()
        // Match explicit languages first; older rows without a language are wildcards.
        for (expected in signature.sortedByDescending { it.language != null }) {
            val index = remaining.indexOfFirst { fresh ->
                val sameShape = if (primary) expected.primaryKey() == fresh.primaryKey()
                    else expected.fallbackKey() == fresh.fallbackKey()
                val sameLanguage = expected.language == null || expected.language.equals(fresh.language, ignoreCase = true)
                val sameAudioQuality = option.kind != com.fetchly.app.domain.model.QualityKind.AUDIO || expected.abr == null ||
                    (fresh.abr != null && kotlin.math.abs(expected.abr - fresh.abr) <= maxOf(1.0, expected.abr * 0.05))
                val expectedItem = expected.formatId.takeIf { it.startsWith("x-entry-") }?.substringBefore("::")
                val freshItem = fresh.formatId.takeIf { it.startsWith("x-entry-") }?.substringBefore("::")
                sameShape && sameLanguage && sameAudioQuality && expectedItem == freshItem
            }
            if (index < 0) return false
            remaining.removeAt(index)
        }
        return true
    }

    private fun StreamRef.primaryKey(): String = "$height|$vcodec|$acodec|$ext"

    private fun StreamRef.fallbackKey(): String = "$height|$ext"
}
