package com.fetchly.app.domain.repository

import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaInfo
import kotlinx.coroutines.flow.Flow

sealed interface AnalyzeResult {
    data class Success(val info: MediaInfo) : AnalyzeResult
    data class Failure(val message: String) : AnalyzeResult
}

interface MediaRepository {
    suspend fun analyze(url: String): AnalyzeResult
}

interface HistoryRepository {
    fun observe(): Flow<List<HistoryEntry>>
    suspend fun add(entry: HistoryEntry): Long
    suspend fun updateStatus(id: Long, status: DownloadStatus, localUri: String?)
    suspend fun updateWorkRequestId(id: Long, uuid: String?)
    suspend fun findCompleted(sourceUrl: String, formatId: String): HistoryEntry?
    suspend fun getById(id: Long): HistoryEntry?
    suspend fun delete(entry: HistoryEntry)
    suspend fun clear()
}
