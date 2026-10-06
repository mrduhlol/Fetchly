package com.fetchly.app.data.engine

import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import org.json.JSONObject

/** Pure mapping from the extractor's JSON to app models. Fully unit-tested. */
object YtDlpMapper {

    data class Mapped(val info: MediaInfo?, val error: String?)

    fun fromJson(sourceUrl: String, platform: Platform, json: String): Mapped {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return Mapped(null, "unparseable")
        if (!root.optBoolean("ok", false)) {
            return Mapped(null, root.optString("error", "extract_failed"))
        }
        val title = root.optString("title", "").ifBlank { "Untitled media" }
        val uploader = root.optString("uploader", "").ifBlank { null }
        val duration = root.optLong("duration", 0L).takeIf { it > 0 }
        val thumbnail = root.optString("thumbnail", "").ifBlank { null }

        val raw = root.optJSONArray("formats") ?: return Mapped(null, "no_formats")
        data class Row(
            val id: String,
            val note: String,
            val height: Int,
            val ext: String,
            val size: Long,
            val url: String,
            val vcodec: String,
            val acodec: String,
            val abr: Double,
        )
        val rows = (0 until raw.length()).mapNotNull { i ->
            val f = raw.optJSONObject(i) ?: return@mapNotNull null
            val url = f.optString("url", "")
            val ext = f.optString("ext", "").lowercase()
            if (url.isBlank() || ext.isBlank() || ext == "mhtml") return@mapNotNull null
            Row(
                id = f.optString("id", ""),
                note = f.optString("quality", ""),
                height = f.optInt("height", 0),
                ext = ext,
                size = f.optLong("filesize", 0L),
                url = url,
                vcodec = f.optString("vcodec", "none"),
                acodec = f.optString("acodec", "none"),
                abr = f.optDouble("abr", 0.0),
            )
        }.filter { it.id.isNotBlank() }
        if (rows.isEmpty()) return Mapped(null, "no_formats")

        val videos = rows.filter { it.vcodec != "none" && it.height > 0 }
            .groupBy { it.height to it.ext }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.size } }
            .sortedByDescending { it.height }
        val audios = rows.filter { it.vcodec == "none" && it.acodec != "none" }
            .sortedByDescending { it.abr }
            .take(6)

        val formats = (videos + audios).map { r ->
            val audioOnly = r.vcodec == "none"
            MediaFormat(
                id = "ytdlp-${r.id}",
                quality = when {
                    !audioOnly && r.height > 0 -> "${r.height}p"
                    audioOnly && r.abr > 0 -> "${r.abr.toInt()} kbps"
                    r.note.isNotBlank() -> r.note
                    audioOnly -> "Audio"
                    else -> "Best available"
                },
                container = r.ext,
                sizeBytes = r.size.takeIf { it > 0 },
                downloadUrl = r.url,
                isAudioOnly = audioOnly,
                bitrateKbps = if (audioOnly) r.abr.toInt().takeIf { it > 0 } else null,
                hasAudio = r.acodec != "none",
            )
        }
        if (formats.isEmpty()) return Mapped(null, "no_formats")

        val mediaType = if (videos.isNotEmpty()) MediaType.VIDEO else MediaType.AUDIO
        return Mapped(
            MediaInfo(
                sourceUrl = sourceUrl,
                platform = platform,
                title = title,
                author = uploader,
                thumbnailUrl = thumbnail,
                durationSecs = duration,
                mediaType = mediaType,
                formats = formats,
            ),
            null,
        )
    }
}
