package com.fetchly.app.domain.repository

import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaInfo
import kotlinx.coroutines.flow.Flow

sealed interface ResolveResult {
    data class Success(val info: MediaInfo) : ResolveResult
    data class Failure(val message: String) : ResolveResult
}

interface MediaRepository {
    suspend fun analyze(url: String): ResolveResult
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
