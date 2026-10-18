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

    /**
     * QUEUED → RUNNING, atomically. Returns 0 when the row is already
     * CANCELLED (a cancel that raced the worker start must win) or COMPLETED.
     * RUNNING is accepted so a crash-restarted job can continue.
     */
    @Query(
        "UPDATE downloads SET status = 'RUNNING', updatedAt = :updatedAt " +
            "WHERE id = :id AND status IN ('QUEUED', 'RUNNING')"
    )
    suspend fun claim(id: String, updatedAt: Long): Int

    /**
     * Unconditional status write, by row id. Used by queue transitions
     * (retry: FAILED/CANCELLED → QUEUED) where the caller has already made
     * the state decision; worker transitions use the guarded queries above.
     */
    @Query(
        "UPDATE downloads SET status = :status, errorMessage = :message, errorRaw = :raw, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun setStatus(id: String, status: String, message: String?, raw: String?, updatedAt: Long)

    @Query(
        "UPDATE downloads SET status = :status, errorMessage = :message, errorRaw = :raw, " +
            "updatedAt = :updatedAt WHERE id = :id AND status != 'CANCELLED'"
    )
    suspend fun finish(id: String, status: String, message: String?, raw: String?, updatedAt: Long): Int

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', outputUri = :uri, outputName = :name, " +
            "outputPath = :path, sizeBytes = COALESCE(:size, sizeBytes), errorMessage = NULL, " +
            "errorRaw = NULL, updatedAt = :updatedAt WHERE id = :id AND status != 'CANCELLED'"
    )
    suspend fun setCompleted(id: String, uri: String, name: String, path: String, size: Long?, updatedAt: Long): Int

    @Query(
        "UPDATE downloads SET status = 'CANCELLED', errorMessage = NULL, errorRaw = NULL, " +
            "updatedAt = :updatedAt WHERE id = :id AND status IN ('QUEUED', 'RUNNING')"
    )
    suspend fun cancelIfActive(id: String, updatedAt: Long): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
