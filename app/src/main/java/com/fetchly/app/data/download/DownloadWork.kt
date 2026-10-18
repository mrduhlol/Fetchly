package com.fetchly.app.data.download

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One download job as WorkManager currently sees it. */
data class DownloadWork(
    val downloadId: String,
    val running: Boolean,
    val stage: String?,
    val bytes: Long,
    val total: Long,
    val speedBytesPerSecond: Long,
)

/** The slice of WorkManager the Downloads screen needs; fakeable in tests. */
interface DownloadWorks {
    fun observe(): Flow<List<DownloadWork>>
}

/**
 * Reads live progress for every Fetchly job. WorkInfo doesn't expose the
 * unique work name, so each request carries [DownloadQueue.jobTag] with its
 * download id and this reader joins on it.
 */
class WorkManagerDownloadWorks(
    private val workManager: WorkManager,
) : DownloadWorks {

    override fun observe(): Flow<List<DownloadWork>> =
        workManager.getWorkInfosByTagFlow(DownloadQueue.TAG)
            .map { infos -> infos.mapNotNull(::toDownloadWork) }

    private fun toDownloadWork(info: WorkInfo): DownloadWork? {
        val downloadId = info.tags
            .firstOrNull { it.startsWith(DownloadQueue.JOB_TAG_PREFIX) }
            ?.removePrefix(DownloadQueue.JOB_TAG_PREFIX)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val progress = info.progress
        return DownloadWork(
            downloadId = downloadId,
            running = info.state == WorkInfo.State.RUNNING,
            stage = progress.getString(DownloadWorker.KEY_STAGE),
            bytes = progress.getLong(DownloadWorker.KEY_BYTES, 0L),
            total = progress.getLong(DownloadWorker.KEY_TOTAL, 0L),
            speedBytesPerSecond = progress.getLong(DownloadWorker.KEY_SPEED, 0L),
        )
    }
}
