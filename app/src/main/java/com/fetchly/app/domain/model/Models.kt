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
    /** A directly accessible media file (mp4, mp3, jpg, …). Resolved locally. */
    DIRECT("Direct link"),
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
    // Optional — only set when the backend genuinely provides the value.
    val bitrateKbps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hasAudio: Boolean? = null,
) {
    /** Human bitrate like "192 kbps", or null when unknown (never invented). */
    val bitrateLabel: String? get() = bitrateKbps?.let { "$it kbps" }
    /** Human dimensions like "2048 × 1365", or null when unknown. */
    val dimensionsLabel: String? get() =
        if (width != null && height != null && width > 0 && height > 0) "$width × $height" else null
}

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
    val formatId: String = "",
    val downloadUrl: String = "",
    val workRequestId: String? = null,
    val sizeBytes: Long? = null,
)
