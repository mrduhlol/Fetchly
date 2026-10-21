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
import java.util.UUID
import java.util.concurrent.TimeUnit
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

    /** Writes a QUEUED row and schedules the download; returns its id. */
    override suspend fun enqueue(media: MediaInfo, option: QualityOption): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
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
        schedule(id)
        return id
    }

    /** Stops a queued or running download; finished rows are left alone. */
    override suspend fun cancel(id: String): Boolean {
        val cancelled = history.cancelIfActive(id, System.currentTimeMillis())
        if (!cancelled) return false
        workManager.cancelUniqueWork(HistoryRepository.workNameOf(id))
        return true
    }

    /** FAILED and CANCELLED rows can start over; anything else is ignored. */
    override suspend fun retry(id: String): Boolean {
        val entry = history.getById(id) ?: return false
        if (entry.status != DownloadStatus.FAILED && entry.status != DownloadStatus.CANCELLED) return false
        history.setStatus(id, DownloadStatus.QUEUED, null, null, System.currentTimeMillis())
        schedule(id, replace = true)
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
        return true
    }

    private fun schedule(id: String, replace: Boolean = false) {
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
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
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
