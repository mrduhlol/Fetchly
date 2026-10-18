package com.fetchly.app.data.download

import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.QualityOption

/**
 * What the UI is allowed to tell the download system to do. Implemented by
 * [DownloadQueue]; an interface so screens (and their tests) never need
 * WorkManager or a Context.
 */
interface DownloadCommands {

    /** Starts a download; returns its history id. */
    suspend fun enqueue(media: MediaInfo, option: QualityOption): String

    /** Stops a queued or running download. */
    suspend fun cancel(id: String): Boolean

    /** Restarts a FAILED or CANCELLED download. */
    suspend fun retry(id: String): Boolean

    /** Removes a history row, optionally deleting its published file. */
    suspend fun delete(id: String, deleteFile: Boolean): Boolean
}
