package com.fetchly.app.data.download

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

data class DlProgress(val done: Long, val total: Long, val speedBps: Double)

/** Outcome of asking the queue to start a download. */
sealed interface EnqueueOutcome {
    data class Started(val entryId: Long) : EnqueueOutcome
    data class Duplicate(val existing: HistoryEntry) : EnqueueOutcome
    data class NoStorage(val needBytes: Long, val freeBytes: Long) : EnqueueOutcome
}

/** Manual DI graph + download queue control — no Hilt, keeps V1 lean. */
object AppGraph {

    const val TAG_DOWNLOADS = "fetchly_downloads"
    private const val TEMP_PREFIX = "fetchly_"
    private const val TEMP_MAX_AGE_MS = 48L * 60 * 60 * 1000

    private lateinit var appContext: Context
    private val db by lazy {
        Room.databaseBuilder(appContext, FetchlyDatabase::class.java, "fetchly.db")
            .fallbackToDestructiveMigration()
            .build()
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

    /** Stable identity: normalized source URL + backend format id. */
    suspend fun findDuplicate(sourceUrl: String, formatId: String): HistoryEntry? =
        history.findCompleted(sourceUrl, formatId)

    @Suppress("DEPRECATION")
    fun freeStorageBytes(): Long = runCatching {
        Environment.getExternalStorageDirectory().usableSpace
    }.getOrDefault(-1L)

    /**
     * Queue a download. Guards: duplicate completed media and insufficient
     * storage (when size is known). Never creates a duplicate history entry
     * on retry — use [retry] for that.
     */
    suspend fun enqueueDownload(info: MediaInfo, format: MediaFormat): EnqueueOutcome {
        findDuplicate(info.sourceUrl, format.id)?.let { return EnqueueOutcome.Duplicate(it) }

        val need = format.sizeBytes
        if (need != null && need > 0) {
            val free = freeStorageBytes()
            if (free >= 0 && free < need) return EnqueueOutcome.NoStorage(need, free)
        }

        val entryId = history.add(
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
                formatId = format.id,
                downloadUrl = format.downloadUrl,
            )
        )
        enqueueWork(
            entryId = entryId,
            title = info.title,
            quality = format.quality,
            container = format.container,
            mediaType = (if (format.isAudioOnly) MediaType.AUDIO else info.mediaType).name,
            downloadUrl = format.downloadUrl,
        )
        return EnqueueOutcome.Started(entryId)
    }

    /** Retry a failed entry: reuses the stored request, no duplicate entry. */
    suspend fun retry(entryId: Long): Boolean {
        val entry = history.getById(entryId) ?: return false
        if (entry.downloadUrl.isBlank()) return false
        history.updateStatus(entryId, DownloadStatus.QUEUED, entry.localUri)
        enqueueWork(
            entryId = entryId,
            title = entry.title,
            quality = entry.quality,
            container = entry.container,
            mediaType = entry.mediaType.name,
            downloadUrl = entry.downloadUrl,
        )
        return true
    }

    fun cancel(entryId: Long) {
        val entry = runBlocking { history.getById(entryId) } ?: return
        entry.workRequestId?.let {
            runCatching { WorkManager.getInstance(appContext).cancelWorkById(UUID.fromString(it)) }
        }
        runBlocking { history.updateStatus(entryId, DownloadStatus.CANCELLED, entry.localUri) }
        DownloadNotifications.dismiss(appContext, entryId)
    }

    /** Delete the actual media file from MediaStore (explicit user choice). */
    suspend fun deleteDownloadedFile(entry: HistoryEntry): Boolean {
        val uriString = entry.localUri ?: return false
        val deleted = runCatching {
            appContext.contentResolver.delete(android.net.Uri.parse(uriString), null, null) > 0
        }.getOrDefault(false)
        history.delete(entry)
        return deleted
    }

    /** Live WorkManager progress keyed by history id. Throttled at source. */
    fun observeProgress(): Flow<Map<Long, DlProgress>> = callbackFlow {
        val wm = WorkManager.getInstance(appContext)
        val live = wm.getWorkInfosByTagLiveData(TAG_DOWNLOADS)
        val obs = Observer<List<WorkInfo>> { infos ->
            val map = infos
                .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                .mapNotNull { wi ->
                    val hid = wi.progress.getLong(DownloadWorker.KEY_HID, -1L).takeIf { it >= 0 }
                        ?: wi.inputData.getLong(DownloadWorker.KEY_HISTORY_ID, -1L).takeIf { it >= 0 }
                        ?: return@mapNotNull null
                    hid to DlProgress(
                        done = wi.progress.getLong(DownloadWorker.KEY_DONE, 0L),
                        total = wi.progress.getLong(DownloadWorker.KEY_TOTAL, -1L),
                        speedBps = wi.progress.getDouble(DownloadWorker.KEY_SPEED, 0.0),
                    )
                }.toMap()
            trySend(map)
        }
        Handler(Looper.getMainLooper()).post { live.observeForever(obs) }
        awaitClose {
            Handler(Looper.getMainLooper()).post { live.removeObserver(obs) }
        }
    }

    /** Delete stale temp files; never touches user-owned downloads. */
    fun cleanupTempFiles() {
        runCatching {
            val now = System.currentTimeMillis()
            appContext.cacheDir.listFiles { f ->
                f.isFile && f.name.startsWith(TEMP_PREFIX) && now - f.lastModified() > TEMP_MAX_AGE_MS
            }?.forEach { it.delete() }
        }
    }

    private suspend fun enqueueWork(
        entryId: Long,
        title: String,
        quality: String,
        container: String,
        mediaType: String,
        downloadUrl: String,
    ) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(TAG_DOWNLOADS)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_URL, downloadUrl)
                    .putString(DownloadWorker.KEY_TITLE, title)
                    .putString(DownloadWorker.KEY_QUALITY, quality)
                    .putString(DownloadWorker.KEY_CONTAINER, container)
                    .putString(DownloadWorker.KEY_TYPE, mediaType)
                    .putLong(DownloadWorker.KEY_HISTORY_ID, entryId)
                    .build()
            )
            .build()
        history.updateWorkRequestId(entryId, req.id.toString())
        WorkManager.getInstance(appContext).enqueue(req)
    }
}
