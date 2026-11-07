package com.fetchly.app.data.download

import com.fetchly.app.data.engine.DownloadProgress

/** Python can call back from several fragment/stream threads simultaneously. */
internal class StreamProgress(sizes: List<Long>, private val publish: (DownloadProgress) -> Unit) {
    private val streams = sizes.map { DownloadProgress(0, it, 0.0) }.toMutableList()

    @Synchronized fun update(index: Int, progress: DownloadProgress) {
        streams[index] = progress
        publish(DownloadProgress(
            streams.sumOf { it.downloadedBytes },
            // A partial total would show 100% while another stream is still running.
            if (streams.all { it.totalBytes > 0 }) streams.sumOf { it.totalBytes } else 0,
            streams.sumOf { it.speedBytesPerSecond },
        ))
    }

    @Synchronized fun complete(index: Int, bytes: Long) {
        update(index, DownloadProgress(bytes, bytes, 0.0))
    }
}
