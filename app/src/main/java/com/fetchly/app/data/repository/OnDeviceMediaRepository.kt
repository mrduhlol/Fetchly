package com.fetchly.app.data.repository

import com.fetchly.app.data.direct.DirectProbe
import com.fetchly.app.data.direct.DirectResult
import com.fetchly.app.data.engine.LinkExtractor
import com.fetchly.app.data.engine.MapResult
import com.fetchly.app.data.engine.YtDlpMapper
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import com.fetchly.app.domain.platform.PlatformDetector

/**
 * Routing: YouTube and known social hosts go straight to the engine; DIRECT
 * and OTHER links are probed first (a plain file needs no extractor) and fall
 * through to the engine when the probe doesn't claim them.
 *
 * A failed analysis keeps both the honest sentence and the engine's raw
 * message, so history rows and bug reports retain the truth.
 */
class OnDeviceMediaRepository(
    private val probe: DirectProbe,
    private val engine: LinkExtractor,
) : MediaRepository {

    override suspend fun analyze(url: String): ResolveOutcome {
        val platform = PlatformDetector.detect(url)
        if (platform == Platform.DIRECT || platform == Platform.OTHER) {
            val direct = probe.resolve(url)
            if (direct is DirectResult.File) {
                return ResolveOutcome.Success(directInfo(url, platform, direct))
            }
        }
        return analyzeWithEngine(url, platform)
    }

    private suspend fun analyzeWithEngine(url: String, platform: Platform): ResolveOutcome =
        when (val mapped = YtDlpMapper.map(engine.extract(url), url, platform)) {
            is MapResult.Ok -> ResolveOutcome.Success(mapped.info)
            is MapResult.Fail -> ResolveOutcome.Failed(
                message = ResolveErrors.messageFor(mapped.rawError),
                raw = mapped.rawError,
            )
        }

    private fun directInfo(url: String, platform: Platform, file: DirectResult.File): MediaInfo {
        val ext = file.filename
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: extensionForMime(file.mime)
        val stream = StreamRef(
            formatId = "direct",
            url = file.url,
            ext = ext,
            vcodec = "none",
            acodec = "none",
            height = null,
            abr = null,
            sizeBytes = file.sizeBytes,
            protocol = "https",
        )
        return MediaInfo(
            sourceUrl = url,
            platform = platform,
            title = file.filename?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "Direct file",
            uploader = null,
            durationSeconds = null,
            thumbnailUrl = null,
            mediaType = mediaTypeFor(file.mime, ext),
            options = listOf(
                QualityOption(
                    id = "file",
                    label = "File",
                    kind = QualityKind.FILE,
                    container = ext.ifEmpty { "bin" },
                    streams = listOf(stream),
                    needsMerge = false,
                    sizeBytes = file.sizeBytes,
                )
            ),
        )
    }

    private fun mediaTypeFor(mime: String?, ext: String): MediaType = when {
        mime?.startsWith("video/") == true -> MediaType.VIDEO
        mime?.startsWith("audio/") == true -> MediaType.AUDIO
        mime?.startsWith("image/") == true -> MediaType.IMAGE
        ext in IMAGE_EXTS -> MediaType.IMAGE
        ext in AUDIO_EXTS -> MediaType.AUDIO
        else -> MediaType.VIDEO
    }

    private fun extensionForMime(mime: String?): String = when (mime) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "video/quicktime" -> "mov"
        "audio/mp4" -> "m4a"
        "audio/mpeg" -> "mp3"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/flac" -> "flac"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> ""
    }

    private companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp")
        val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    }
}
