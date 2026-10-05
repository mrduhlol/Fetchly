package com.fetchly.app.data.repository

import com.fetchly.app.data.local.HistoryDao
import com.fetchly.app.data.local.HistoryEntity
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {

    override fun observe(): Flow<List<HistoryEntry>> =
        dao.observe().map { list -> list.map { it.toDomain() } }

    override suspend fun add(entry: HistoryEntry): Long = dao.insert(entry.toEntity())

    override suspend fun updateStatus(id: Long, status: DownloadStatus, localUri: String?) {
        dao.updateStatus(id, status.name, localUri)
    }

    override suspend fun delete(entry: HistoryEntry) {
        dao.delete(entry.toEntity())
    }

    override suspend fun clear() = dao.clear()

    private fun HistoryEntity.toDomain() = HistoryEntry(
        id = id, title = title, sourceUrl = sourceUrl,
        platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.UNKNOWN),
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.UNKNOWN),
        quality = quality, container = container, fileName = fileName,
        localUri = localUri, thumbnailUrl = thumbnailUrl,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
        createdAt = createdAt,
    )

    private fun HistoryEntry.toEntity() = HistoryEntity(
        id = id, title = title, sourceUrl = sourceUrl,
        platform = platform.name, mediaType = mediaType.name,
        quality = quality, container = container, fileName = fileName,
        localUri = localUri, thumbnailUrl = thumbnailUrl,
        status = status.name, createdAt = createdAt,
    )
}
