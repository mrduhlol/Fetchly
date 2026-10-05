package com.fetchly.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val platform: String,
    val mediaType: String,
    val quality: String,
    val container: String,
    val fileName: String,
    val localUri: String?,
    val thumbnailUrl: String?,
    val status: String,
    val createdAt: Long,
    // V1.2: stable identity for duplicate detection + retry, and the
    // WorkManager request id for cancel.
    val formatId: String = "",
    val downloadUrl: String = "",
    val workRequestId: String? = null,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET status = :status, localUri = :localUri WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, localUri: String?)

    @Query("UPDATE history SET workRequestId = :uuid WHERE id = :id")
    suspend fun updateWorkRequestId(id: Long, uuid: String?)

    @Query("SELECT * FROM history WHERE sourceUrl = :url AND formatId = :formatId AND status = 'COMPLETED' LIMIT 1")
    suspend fun findCompleted(url: String, formatId: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HistoryEntity?

    @Delete
    suspend fun delete(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}
