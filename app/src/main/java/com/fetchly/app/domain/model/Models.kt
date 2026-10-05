package com.fetchly.app.domain.model

/** Supported platforms. UNKNOWN means "not currently supported". */
enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    TWITTER("X / Twitter"),
    REDDIT("Reddit"),
    FACEBOOK("Facebook"),
    PINTEREST("Pinterest"),
    UNKNOWN("Unknown"),
}

enum class MediaType { VIDEO, AUDIO, IMAGE, UNKNOWN }

enum class FetchState { IDLE, ANALYZING, READY, DOWNLOADING, COMPLETED, ERROR }

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

data class MediaFormat(
    val id: String,
    val quality: String,
    val container: String,
    val sizeBytes: Long?,
    val downloadUrl: String,
    val isAudioOnly: Boolean = false,
)

data class MediaInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationSecs: Long?,
    val mediaType: MediaType,
    val formats: List<MediaFormat>,
) {
    val videoFormats: List<MediaFormat> get() = formats.filter { !it.isAudioOnly }
    val audioFormats: List<MediaFormat> get() = formats.filter { it.isAudioOnly }
}

data class HistoryEntry(
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val platform: Platform,
    val mediaType: MediaType,
    val quality: String,
    val container: String,
    val fileName: String,
    val localUri: String?,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    val createdAt: Long = System.currentTimeMillis(),
)
