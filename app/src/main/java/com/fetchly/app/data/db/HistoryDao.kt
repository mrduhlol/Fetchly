package com.fetchly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        "UPDATE downloads SET status = :status, errorMessage = :message, errorRaw = :raw, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun setStatus(id: String, status: String, message: String?, raw: String?, updatedAt: Long)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', outputUri = :uri, outputName = :name, " +
            "outputPath = :path, sizeBytes = COALESCE(:size, sizeBytes), errorMessage = NULL, " +
            "errorRaw = NULL, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun setCompleted(id: String, uri: String, name: String, path: String, size: Long?, updatedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
