package com.fetchly.app.data.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/**
 * One row of download history, ready for SQLite. Mirrors
 * [com.fetchly.app.domain.model.HistoryEntry] with the stream signature and
 * its last-known URLs flattened to JSON (see [Signatures]) and the
 * WorkManager unique name stored next to the id.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val uploader: String?,
    val sourceUrl: String,
    val platform: String,
    val mediaType: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val optionLabel: String,
    val kind: String,
    val container: String,
    val sizeBytes: Long?,
    val signatureJson: String,
    val streamUrlsJson: String,
    val workName: String,
    val status: String,
    val errorMessage: String?,
    val errorRaw: String?,
    val outputUri: String?,
    val outputName: String?,
    val outputPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Fresh installs only — history never migrates from the old app. */
@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class FetchlyDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
