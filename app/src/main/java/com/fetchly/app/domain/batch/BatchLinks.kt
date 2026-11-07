package com.fetchly.app.domain.batch

import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.security.UrlValidation

/** Bounded, explicit parsing: rejected links and duplicates are reported, not hidden. */
object BatchLinks {
    const val MAX_LINKS = 50
    const val MAX_INPUT = 128 * 1024
    data class Parsed(val urls: List<String>, val duplicates: Int, val rejected: Int, val overLimit: Boolean)

    fun parse(input: String): Parsed {
        val candidates = UrlSecurity.urlCandidates(input.take(MAX_INPUT))
        val valid = candidates.mapNotNull { (UrlSecurity.validate(it) as? UrlValidation.Valid)?.url }
        val unique = valid.distinct()
        return Parsed(unique.take(MAX_LINKS), valid.size - unique.size, candidates.size - valid.size,
            unique.size > MAX_LINKS || input.length > MAX_INPUT)
    }
}
