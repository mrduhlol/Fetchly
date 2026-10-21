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
 * - Keep one preferred stream per real output container at each height.
 *   Muxed streams rank first, then avc1 > vp9 > av01; preserve alternatives.
 * - Best audio: mp4a/aac > opus > vorbis > other, then higher abr. Keep one
 *   standalone audio choice per available container.
 * - Pair confirmed video-only/unknown-audio streams with known audio when
 *   available. Unknown embedded audio is not advertised as confirmed silence.
 * - Safe merged output is mp4 for AVC/AAC, otherwise mkv. Compatible muxed
 *   WebM and unknown-codec single streams retain their original container.
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
            options += videoOptions(group, bestAudio, "v$height", "${height}p")
        }

        var degradedType: MediaType? = null
        if (videos.isEmpty()) {
            val fallbackVideos = videoOptions(
                formats.filter { it.vcodec != "none" && it.height == null },
                bestAudio, "video", "Video",
            )
            when {
                fallbackVideos.isNotEmpty() -> options += fallbackVideos
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
            options += audios.sortedWith(AUDIO_RANK)
                .distinctBy { audioContainer(it.ext) }
                .mapIndexed { index, audio ->
                    audioOption(audio).copy(id = if (index == 0) "audio" else "audio-${audioContainer(audio.ext)}")
                }
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

    /** One real output container per quality; no transcoding choices are invented. */
    private fun videoOptions(
        candidates: List<StreamRef>,
        bestAudio: StreamRef?,
        baseId: String,
        label: String,
    ): List<QualityOption> = candidates.sortedWith(VIDEO_RANK)
        .map { video ->
            val muxed = video.acodec != "none" &&
                (video.acodec != "unknown" || bestAudio == null)
            val audio = if (muxed) null else bestAudio
            val streams = if (muxed) listOf(video) else listOfNotNull(video, audio)
            val hasAudio = muxed || audio != null
            val effectiveAudioCodec = if (muxed) video.acodec else audio?.acodec
            val originalContainer = streams.size == 1 && (
                video.vcodec == "unknown" || video.acodec == "unknown" ||
                    (video.ext == "webm" &&
                        (video.vcodec.startsWith("vp9") || video.vcodec.startsWith("vp09") || video.vcodec.startsWith("av01") || video.vcodec.startsWith("vp8")) &&
                        (effectiveAudioCodec == null || effectiveAudioCodec == "none" || effectiveAudioCodec.startsWith("opus") || effectiveAudioCodec.startsWith("vorbis"))))
            QualityOption(
                id = baseId,
                label = if (hasAudio) label else "$label (no audio)",
                kind = QualityKind.VIDEO,
                container = if (originalContainer) video.ext else videoContainer(video.vcodec, effectiveAudioCodec),
                streams = streams,
                needsMerge = streams.size > 1,
                sizeBytes = sumKnownSizes(streams),
            )
        }
        .distinctBy { it.container }
        .mapIndexed { index, option ->
            option.copy(id = if (index == 0) baseId else "$baseId-${option.container}")
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
