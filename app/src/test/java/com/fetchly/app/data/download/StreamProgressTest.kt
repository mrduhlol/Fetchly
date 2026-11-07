package com.fetchly.app.data.download

import com.fetchly.app.data.engine.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamProgressTest {
    @Test fun combinesStreamsAndRemovesFinishedStreamSpeed() {
        var latest = DownloadProgress(0, 0, 0.0)
        val progress = StreamProgress(listOf(100L, 20L)) { latest = it }
        progress.update(0, DownloadProgress(50, 100, 10.0))
        progress.update(1, DownloadProgress(10, 20, 5.0))
        assertEquals(DownloadProgress(60, 120, 15.0), latest)
        progress.complete(1, 20)
        assertEquals(DownloadProgress(70, 120, 10.0), latest)
    }

    @Test fun unknownStreamSizeDoesNotPublishMisleadingTotal() {
        var latest = DownloadProgress(0, 0, 0.0)
        val progress = StreamProgress(listOf(100L, 0L)) { latest = it }
        progress.update(0, DownloadProgress(100, 100, 10.0))
        assertEquals(0L, latest.totalBytes)
        progress.complete(1, 20)
        assertEquals(DownloadProgress(120, 120, 10.0), latest)
    }
}
