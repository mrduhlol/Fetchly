package com.fetchly.app.data.download

import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityOption

/**
 * What the UI is allowed to tell the download system to do. Implemented by
 * [DownloadQueue]; an interface so screens (and their tests) never need
 * WorkManager or a Context.
 */
enum class EnqueueDisposition(val message: String) {
    QUEUED("Added to downloads."), ACTIVE("This file is already in your download queue."), SAVED("This file is already saved. Open it from your history.");
}
data class EnqueueReceipt(val id: String, val disposition: EnqueueDisposition = EnqueueDisposition.QUEUED)

interface DownloadCommands {

    /** Starts a download; returns its history id. */
    suspend fun enqueue(media: MediaInfo, option: QualityOption): String

    suspend fun enqueueWithReceipt(media: MediaInfo, option: QualityOption): EnqueueReceipt = EnqueueReceipt(enqueue(media, option))

    /** Stops a queued or running download. */
    suspend fun cancel(id: String): Boolean

    /** Restarts a FAILED or CANCELLED download. */
    suspend fun retry(id: String): Boolean

    /** Removes a history row, optionally deleting its published file. */
    suspend fun delete(id: String, deleteFile: Boolean): Boolean
}
