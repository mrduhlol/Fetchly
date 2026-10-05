package com.fetchly.app.domain.usecase

import com.fetchly.app.domain.repository.AnalyzeResult
import com.fetchly.app.domain.repository.MediaRepository

class AnalyzeUrlUseCase(private val repo: MediaRepository) {
    suspend operator fun invoke(url: String): AnalyzeResult = repo.analyze(url)
}
