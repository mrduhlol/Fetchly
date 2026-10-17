package com.fetchly.app.domain.resolve

import com.fetchly.app.data.repository.MediaRepository
import com.fetchly.app.data.repository.ResolveOutcome
import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.security.UrlValidation

/**
 * The UI's single entry point for "what is this link?". Validation is local
 * and cheap; only valid links reach the repository (and the network).
 */
class ResolveUrlUseCase(private val repo: MediaRepository) {

    suspend operator fun invoke(raw: String): ResolveOutcome =
        when (val validation = UrlSecurity.validate(raw)) {
            is UrlValidation.Invalid -> ResolveOutcome.Failed(validation.reason, raw = null)
            is UrlValidation.Valid -> repo.analyze(validation.url)
        }
}
