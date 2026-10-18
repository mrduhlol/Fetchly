package com.fetchly.app.data.engine

import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import org.json.JSONObject

sealed interface MapResult {
    data class Ok(val info: MediaInfo) : MapResult
    data class Fail(val rawError: String) : MapResult
}

/**
 * Pure translation from yt-dlp's extract JSON (see fetchly_yt.py) into
 * [MediaInfo]. The pairing rules here are the spec of record:
 *
 * - Video streams need a codec; at one height the muxed stream wins;
 *   otherwise avc1 > vp9/vp09 > av01 > other, then bigger. Videos without a
 *   known height (some HLS variants) are still offered, labelled "Video".
 * - Best audio: mp4a/aac > opus > vorbis > other, then higher abr.
 * - Every video option pairs with the best audio, unless the video is already
 *   muxed or no audio exists at all — then it is labelled "(no audio)".
 * - Container: mp4 when the video is avc1 and the audio is aac/mp4a (or
 *   absent); anything else becomes mkv. Audio options: m4a, or their own ext.
 * - Sizes are summed only when every stream's size is known — never invented.
 * - No video at all: one stream (plain file) or images degrade to a single
 *   FILE option so best-effort links still offer something honest.
 */
object YtDlpMapper {

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    fun map(json: String, sourceUrl: String, platform: Platform): MapResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return MapResult.Fail("The engine returned unreadable data.")
        if (!root.optBoolean("ok", false)) {
            return MapResult.Fail(
                root.str("error") ?: "yt-dlp reported a failure without a message."
            )
        }

        val formats = readFormats(root)
        val videos = formats.filter { it.vcodec != "none" && it.height != null }
        val audios = formats.filter { it.acodec != "none" && it.vcodec == "none" }
        val images = formats.filter {
            it.ext in IMAGE_EXTS && it.vcodec == "none" && it.acodec == "none"
        }
        val bestAudio = audios.sortedWith(AUDIO_RANK).firstOrNull()

        val options = mutableListOf<QualityOption>()
        val bestByHeight = videos.groupBy { it.height!! }
        for ((height, group) in bestByHeight.entries.sortedByDescending { it.key }) {
            val video = group.sortedWith(VIDEO_RANK).first()
            val muxed = video.acodec != "none"
            val audio = if (muxed) null else bestAudio
            val streams = if (muxed) listOf(video) else listOfNotNull(video, audio)
            val hasAudio = muxed || audio != null
            val effectiveAudioCodec = if (muxed) video.acodec else audio?.acodec
            options += QualityOption(
                id = "v$height",
                label = if (hasAudio) "${height}p" else "${height}p (no audio)",
                kind = QualityKind.VIDEO,
                container = videoContainer(video.vcodec, effectiveAudioCodec),
                streams = streams,
                needsMerge = streams.size > 1,
                sizeBytes = sumKnownSizes(streams),
            )
        }

        var degradedType: MediaType? = null
        if (videos.isEmpty()) {
            val fallbackVideo = heightlessVideoOption(formats, bestAudio)
            when {
                fallbackVideo != null -> options += fallbackVideo
                images.isNotEmpty() -> {
                    degradedType = MediaType.IMAGE
                    options += fileOption(images.first(), label = "Image")
                }
                formats.size == 1 -> {
                    val single = formats.first()
                    degradedType = if (single.ext in AUDIO_EXTS) MediaType.AUDIO else MediaType.VIDEO
                    options += fileOption(single, label = "File")
                }
                else -> Unit
            }
        }

        // The standalone audio option rides along with any video option —
        // including the heightless fallback above — but never next to a
        // degraded file/image option (that *is* the stream already).
        val hasDegradedFile = options.any { it.kind != QualityKind.VIDEO }
        if (bestAudio != null && (videos.isNotEmpty() || !hasDegradedFile)) {
            options += audioOption(bestAudio)
        } else if (options.isEmpty()) {
            return MapResult.Fail("yt-dlp returned no usable streams for this link.")
        }

        val mediaType = when {
            options.any { it.kind == QualityKind.VIDEO } -> MediaType.VIDEO
            options.any { it.kind == QualityKind.AUDIO } -> MediaType.AUDIO
            degradedType != null -> requireNotNull(degradedType)
            else -> MediaType.VIDEO
        }

        return MapResult.Ok(
            MediaInfo(
                sourceUrl = sourceUrl,
                platform = platform,
                title = root.str("title") ?: "Untitled",
                uploader = root.str("uploader"),
                durationSeconds = root.intOrNull("duration")?.takeIf { it > 0 },
                thumbnailUrl = root.str("thumbnail"),
                mediaType = mediaType,
                options = options,
            )
        )
    }

    // ------------------------------------------------------------ options

    private fun audioOption(audio: StreamRef) = QualityOption(
        id = "audio",
        label = "Audio",
        kind = QualityKind.AUDIO,
        container = audioContainer(audio.ext),
        streams = listOf(audio),
        needsMerge = false,
        sizeBytes = sumKnownSizes(listOf(audio)),
    )

    /**
     * The best video format that carries no height (e.g. an HLS variant
     * whose master play-list omits RESOLUTION): still a real stream, so it
     * is offered rather than silently dropped. null when there is none.
     */
    private fun heightlessVideoOption(
        formats: List<StreamRef>,
        bestAudio: StreamRef?,
    ): QualityOption? {
        val candidates = formats.filter { it.vcodec != "none" && it.height == null }
        val video = candidates.sortedWith(VIDEO_RANK).firstOrNull() ?: return null
        val muxed = video.acodec != "none"
        val audio = if (muxed) null else bestAudio
        val streams = if (muxed) listOf(video) else listOfNotNull(video, audio)
        val hasAudio = muxed || audio != null
        val effectiveAudioCodec = if (muxed) video.acodec else audio?.acodec
        return QualityOption(
            id = "video",
            label = if (hasAudio) "Video" else "Video (no audio)",
            kind = QualityKind.VIDEO,
            container = videoContainer(video.vcodec, effectiveAudioCodec),
            streams = streams,
            needsMerge = streams.size > 1,
            sizeBytes = sumKnownSizes(streams),
        )
    }

    private fun fileOption(stream: StreamRef, label: String) = QualityOption(
        id = "file",
        label = label,
        kind = QualityKind.FILE,
        container = stream.ext,
        streams = listOf(stream),
        needsMerge = false,
        sizeBytes = sumKnownSizes(listOf(stream)),
    )

    private fun videoContainer(videoCodec: String, audioCodec: String?): String {
        val videoIsMp4 = videoCodec.startsWith("avc")
        val audioIsMp4 = audioCodec == null ||
            audioCodec.startsWith("mp4a") || audioCodec.startsWith("aac")
        return if (videoIsMp4 && audioIsMp4) "mp4" else "mkv"
    }

    private fun audioContainer(ext: String): String = when (ext) {
        "m4a", "mp4" -> "m4a"
        else -> ext
    }

    private fun sumKnownSizes(streams: List<StreamRef>): Long? {
        if (streams.isEmpty()) return null
        var total = 0L
        for (stream in streams) {
            val size = stream.sizeBytes ?: return null
            total += size
        }
        return total
    }

    private val VIDEO_RANK = compareByDescending<StreamRef> { it.acodec != "none" }
        .thenBy { videoCodecRank(it.vcodec) }
        .thenByDescending { it.sizeBytes ?: 0L }

    private val AUDIO_RANK = compareBy<StreamRef> { audioCodecRank(it.acodec) }
        .thenByDescending { it.abr ?: 0 }
        .thenByDescending { it.sizeBytes ?: 0L }

    private fun videoCodecRank(codec: String): Int = when {
        codec.startsWith("avc") -> 0
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
        codec.startsWith("av01") -> 2
        else -> 3
    }

    private fun audioCodecRank(codec: String): Int = when {
        codec.startsWith("mp4a") || codec.startsWith("aac") -> 0
        codec.startsWith("opus") -> 1
        codec.startsWith("vorbis") -> 2
        else -> 3
    }

    // ------------------------------------------------------------ parsing

    private fun readFormats(root: JSONObject): List<StreamRef> {
        val array = root.optJSONArray("formats") ?: return emptyList()
        val out = ArrayList<StreamRef>(array.length())
        for (i in 0 until array.length()) {
            val f = array.optJSONObject(i) ?: continue
            val url = f.str("url") ?: continue
            val ext = f.str("ext")?.lowercase() ?: continue
            if (ext == "mhtml") continue // storyboards are not media
            out += StreamRef(
                formatId = f.str("id").orEmpty(),
                url = url,
                ext = ext,
                vcodec = f.str("vcodec")?.lowercase() ?: "none",
                acodec = f.str("acodec")?.lowercase() ?: "none",
                height = f.intOrNull("height"),
                abr = f.doubleOrNull("abr")?.toInt(),
                sizeBytes = f.longOrNull("filesize") ?: f.longOrNull("filesize_approx"),
                protocol = f.str("protocol"),
            )
        }
        return out
    }

    private fun JSONObject.str(key: String): String? {
        val value = opt(key) ?: return null
        if (value === JSONObject.NULL) return null
        val text = value.toString().trim()
        return text.ifEmpty { null }
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        (opt(key) as? Number)?.toInt()

    private fun JSONObject.doubleOrNull(key: String): Double? =
        (opt(key) as? Number)?.toDouble()

    private fun JSONObject.longOrNull(key: String): Long? =
        (opt(key) as? Number)?.toLong()
}
