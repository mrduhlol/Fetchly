package com.fetchly.app.data.download

import com.fetchly.app.data.engine.DownloadProgress

object DownloadStages {
    fun isTransfer(stage: String?): Boolean = stage?.let {
        it.startsWith("Downloading", true) || it.startsWith("Resuming", true)
    } == true

    fun progressFor(stage: String?, progress: DownloadProgress): DownloadProgress =
        if (isTransfer(stage)) progress else DownloadProgress(0, 0, 0.0)
}
