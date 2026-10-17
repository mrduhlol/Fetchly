package com.fetchly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** Newest first — the order the Downloads screen shows. */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        "UPDATE downloads SET status = :status, errorMessage = :message, errorRaw = :raw, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun setStatus(
        id: String,
        status: String,
        message: String?,
        raw: String?,
        updatedAt: Long,
    )

    /** Completing clears any previous failure text; [size] never overwrites with null. */
    @Query(
        "UPDATE downloads SET status = 'COMPLETED', outputUri = :uri, outputName = :name, " +
            "outputPath = :path, sizeBytes = COALESCE(:size, sizeBytes), updatedAt = :updatedAt, " +
            "errorMessage = NULL, errorRaw = NULL WHERE id = :id",
    )
    suspend fun setCompleted(
        id: String,
        uri: String?,
        name: String?,
        path: String?,
        size: Long?,
        updatedAt: Long,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
