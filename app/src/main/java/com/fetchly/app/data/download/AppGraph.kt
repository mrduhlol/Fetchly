package com.fetchly.app.data.download

import android.content.Context
import androidx.room.Room
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fetchly.app.data.local.FetchlyDatabase
import com.fetchly.app.data.prefs.SettingsStore
import com.fetchly.app.data.remote.ApiClient
import com.fetchly.app.data.repository.ApiMediaRepository
import com.fetchly.app.data.repository.RoomHistoryRepository
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Manual DI graph — no Hilt, keeps V1 lean. */
object AppGraph {

    private lateinit var appContext: Context
    private val db by lazy {
        Room.databaseBuilder(appContext, FetchlyDatabase::class.java, "fetchly.db").build()
    }
    val history by lazy { RoomHistoryRepository(db.historyDao()) }
    val settings by lazy { SettingsStore(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun mediaRepository(): ApiMediaRepository {
        val override = runBlocking { settings.apiBaseUrl.first() }
        val base = override.ifBlank { ApiClient.baseUrl() }
        return ApiMediaRepository(ApiClient.create(base))
    }

    fun enqueueDownload(info: MediaInfo, format: MediaFormat): Long {
        val entryId = runBlocking {
            history.add(
                HistoryEntry(
                    title = info.title,
                    sourceUrl = info.sourceUrl,
                    platform = info.platform,
                    mediaType = if (format.isAudioOnly) MediaType.AUDIO else info.mediaType,
                    quality = format.quality,
                    container = format.container,
                    fileName = MediaStoreSaver.buildFileName(info.title, format.quality, format.container),
                    localUri = null,
                    thumbnailUrl = info.thumbnailUrl,
                    status = DownloadStatus.QUEUED,
                )
            )
        }
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_URL, format.downloadUrl)
                    .putString(DownloadWorker.KEY_TITLE, info.title)
                    .putString(DownloadWorker.KEY_QUALITY, format.quality)
                    .putString(DownloadWorker.KEY_CONTAINER, format.container)
                    .putString(DownloadWorker.KEY_TYPE, info.mediaType.name)
                    .putLong(DownloadWorker.KEY_HISTORY_ID, entryId)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueue(req)
        return entryId
    }
}
