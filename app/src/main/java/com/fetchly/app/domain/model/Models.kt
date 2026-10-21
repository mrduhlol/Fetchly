package com.fetchly.app.domain.model

/** Where a link points. DIRECT = a file URL (mp4/mp3/jpg…); OTHER = yt-dlp best-effort. */
enum class Platform { YOUTUBE, INSTAGRAM, TIKTOK, TWITTER, REDDIT, PINTEREST, FACEBOOK, LINKEDIN, OTHER, DIRECT }

enum class MediaType { VIDEO, AUDIO, IMAGE }

/** What kind of download one quality choice performs. */
enum class QualityKind { VIDEO, AUDIO, FILE }

/** One downloadable stream as reported by the engine. */
data class StreamRef(
    val formatId: String,
    val url: String,
    val ext: String,
    val vcodec: String,
    val acodec: String,
    val height: Int?,
    val abr: Int?,
    val sizeBytes: Long?,
    val protocol: String? = null,
)

/**
 * One line in the quality list. `sizeBytes` is the sum of the streams' known
 * sizes, or null when any part is unknown — sizes are never invented.
 */
data class QualityOption(
    val id: String,
    val label: String,
    val kind: QualityKind,
    val container: String,
    val streams: List<StreamRef>,
    val needsMerge: Boolean,
    val sizeBytes: Long?,
)

data class MediaInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val options: List<QualityOption>,
)

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * One row of download history. Keeps what a retry needs: the source URL, the
 * chosen option's kind and stream signature; stored stream URLs are a cache
 * only (they expire — retry re-resolves when they stop working).
 */
data class HistoryEntry(
    val id: String,
    val title: String,
    val uploader: String?,
    val sourceUrl: String,
    val platform: Platform,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val optionLabel: String,
    val kind: QualityKind,
    val container: String,
    val sizeBytes: Long?,
    val signature: List<StreamRef>,
    val status: DownloadStatus,
    val errorMessage: String?,
    val errorRaw: String?,
    val outputUri: String?,
    val outputName: String?,
    val outputPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
