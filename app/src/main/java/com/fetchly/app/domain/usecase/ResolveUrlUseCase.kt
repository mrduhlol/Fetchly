package com.fetchly.app.domain.usecase

import com.fetchly.app.domain.repository.ResolveResult
import com.fetchly.app.domain.repository.MediaRepository

class ResolveUrlUseCase(private val repo: MediaRepository) {
    suspend operator fun invoke(url: String): ResolveResult = repo.analyze(url)
}
