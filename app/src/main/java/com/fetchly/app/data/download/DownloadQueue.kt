package com.fetchly.app.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.util.Filenames
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one door between the UI and downloading. Every method keeps the history
 * row and the WorkManager job in agreement: the row is the truth the user
 * sees, the unique work (`fetchly_<id>`) is the work that actually runs.
 */
class DownloadQueue(
    private val context: Context,
    private val history: HistoryRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) : DownloadCommands {

    private val mutex = Mutex()

    override suspend fun enqueue(media: MediaInfo, option: QualityOption): String = enqueueWithReceipt(media, option).id

    /** Reuse an active or readable saved copy; one lock covers check, insert and scheduling. */
    override suspend fun enqueueWithReceipt(media: MediaInfo, option: QualityOption): EnqueueReceipt = mutex.withLock {
        val entries = history.observeAll().first()
        for (existing in entries.filter { DownloadQueuePolicy.sameDownload(it, media, option) }) {
            if (existing.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)) {
                pumpLocked()
                return@withLock EnqueueReceipt(existing.id, EnqueueDisposition.ACTIVE)
            }
            if (existing.status == DownloadStatus.COMPLETED && withContext(Dispatchers.IO) {
                    MediaStorePublisher.exists(context, existing.outputUri)
                }) return@withLock EnqueueReceipt(existing.id, EnqueueDisposition.SAVED)
        }
        val id = UUID.randomUUID().toString()
        val now = maxOf(System.currentTimeMillis(), (entries.maxOfOrNull { it.createdAt } ?: 0L) + 1L)
        history.upsert(
            HistoryEntry(
                id = id,
                title = media.title,
                uploader = media.uploader,
                sourceUrl = media.sourceUrl,
                platform = media.platform,
                mediaType = DownloadPlan.mediaType(option.kind, media.mediaType),
                thumbnailUrl = media.thumbnailUrl,
                durationSeconds = media.durationSeconds,
                optionLabel = option.label,
                kind = option.kind,
                container = option.container,
                sizeBytes = option.sizeBytes,
                signature = option.streams,
                status = DownloadStatus.QUEUED,
                errorMessage = null,
                errorRaw = null,
                outputUri = null,
                outputName = null,
                outputPath = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        pumpLocked()
        EnqueueReceipt(id)
    }

    /** Stops a queued or running download; finished rows are left alone. */
    override suspend fun cancel(id: String): Boolean {
        val cancelled = history.cancelIfActive(id, System.currentTimeMillis())
        if (!cancelled) return false
        withContext(Dispatchers.IO) { workManager.cancelUniqueWork(HistoryRepository.workNameOf(id)).result.get() }
        pump()
        return true
    }

    /** FAILED and CANCELLED rows can resume validated direct files; anything else is ignored. */
    override suspend fun retry(id: String): Boolean {
        val entry = history.getById(id) ?: return false
        if (entry.status != DownloadStatus.FAILED && entry.status != DownloadStatus.CANCELLED) {
            if (entry.status == DownloadStatus.COMPLETED) return false
            val terminal = withContext(Dispatchers.IO) {
                val infos = workManager.getWorkInfosForUniqueWork(HistoryRepository.workNameOf(id)).get()
                infos.isNotEmpty() && infos.all { it.state.isFinished }
            }
            if (!terminal) return false
        }
        history.setStatus(id, DownloadStatus.QUEUED, null, null, System.currentTimeMillis())
        pump()
        return true
    }

    /** Removes the row (and, when asked, its published file). */
    override suspend fun delete(id: String, deleteFile: Boolean): Boolean {
        val entry = history.getById(id) ?: return false
        workManager.cancelUniqueWork(HistoryRepository.workNameOf(id))
        if (deleteFile) {
            // MediaStore deletion is a ContentResolver call: never on Main.
            withContext(Dispatchers.IO) {
                entry.outputUri?.let { MediaStorePublisher.delete(context, it) }
            }
        }
        history.delete(id)
        withContext(Dispatchers.IO) {
            TempFiles.deleteFor(TempFiles.dir(context), Filenames.tempPrefix(id))
        }
        return true
    }

    /** Only two WorkManager requests are admitted at once; waiting rows stay durable in Room. */
    suspend fun pump() = mutex.withLock { pumpLocked() }

    private suspend fun pumpLocked() = withContext(Dispatchers.IO) {
        val liveIds = workManager.getWorkInfosByTag(TAG).get().filterNot { it.state.isFinished }
            .mapNotNull { info -> info.tags.firstOrNull { it.startsWith(JOB_TAG_PREFIX) }?.removePrefix(JOB_TAG_PREFIX) }.toSet()
        val next = DownloadQueuePolicy.next(history.observeAll().first(), liveIds)
        for (entry in next) schedule(entry.id)
    }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(jobTag(id))
            .build()
        workManager.enqueueUniqueWork(
            HistoryRepository.workNameOf(id),
            ExistingWorkPolicy.KEEP,
            request,
        ).result.get()
    }

    companion object {
        /** Everything Fetchly schedules carries this tag. */
        const val TAG = "fetchly_downloads"

        /**
         * One tag per download so WorkManager's progress can be joined back
         * to its history row (WorkInfo does not expose the unique work name).
         */
        const val JOB_TAG_PREFIX = "fetchly_job_"

        fun jobTag(id: String): String = "$JOB_TAG_PREFIX$id"
    }
}
