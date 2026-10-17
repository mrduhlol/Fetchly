package com.fetchly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * One history row. Mirrors [com.fetchly.app.domain.model.HistoryEntry] with
 * two JSON columns: the structural stream signature (what OptionMatcher
 * compares on retry) and the last known stream URLs (a cache that expires —
 * a failed retry re-resolves instead of trusting stale links).
 *
 * `workName` is the row's unique WorkManager name (`"fetchly_" + id`).
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

    companion object {
        fun build(context: Context): FetchlyDatabase =
            Room.databaseBuilder(context, FetchlyDatabase::class.java, "fetchly.db").build()
    }
}
