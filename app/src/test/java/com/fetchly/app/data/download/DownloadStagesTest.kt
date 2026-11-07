package com.fetchly.app.data.download

import com.fetchly.app.data.engine.DownloadProgress
import org.junit.Assert.*
import org.junit.Test

class DownloadStagesTest {
    @Test fun transferCountsNeverMasqueradeAsMergingOrSavingProgress() {
        val transfer = DownloadProgress(100, 100, 200.0)
        for (stage in listOf("Merging…", "Checking file…", "Saving…", "Retrying in 2 seconds…")) {
            assertFalse(DownloadStages.isTransfer(stage))
            assertEquals(DownloadProgress(0, 0, 0.0), DownloadStages.progressFor(stage, transfer))
        }
        assertEquals(transfer, DownloadStages.progressFor("Downloading video…", transfer))
    }
}
