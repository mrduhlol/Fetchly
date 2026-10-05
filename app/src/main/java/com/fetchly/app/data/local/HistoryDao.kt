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
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET status = :status, localUri = :localUri WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, localUri: String?)

    @Delete
    suspend fun delete(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}
