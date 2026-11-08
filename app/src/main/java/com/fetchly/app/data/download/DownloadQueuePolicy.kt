package com.fetchly.app.data.download

import com.fetchly.app.domain.model.*
import java.net.URI

object DownloadQueuePolicy {
    const val MAX_PARALLEL = 2

    fun next(entries: List<HistoryEntry>, liveIds: Set<String>): List<HistoryEntry> = entries
        .filter { it.status == DownloadStatus.QUEUED && it.id !in liveIds }
        .sortedWith(compareBy<HistoryEntry> { it.createdAt }.thenBy { it.id })
        .take((MAX_PARALLEL - liveIds.size).coerceAtLeast(0))

    fun sameDownload(entry: HistoryEntry, media: MediaInfo, option: QualityOption): Boolean =
        sourceKey(entry.sourceUrl) == sourceKey(media.sourceUrl) && entry.kind == option.kind &&
            entry.container.equals(option.container, true) &&
            (option.kind != QualityKind.AUDIO || option.container != "mp3" || entry.optionLabel == option.label) &&
            signature(entry.signature, option.kind) == signature(option.streams, option.kind)

    private fun signature(streams: List<StreamRef>, kind: QualityKind) = streams.map {
        listOf(it.height, it.vcodec, it.acodec, it.ext, it.language,
            if (kind == QualityKind.AUDIO) it.abr else null).joinToString("|")
    }.sorted()

    private fun sourceKey(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        val youtubeId = when {
            host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
            host == "youtube.com" || host.endsWith(".youtube.com") -> {
                if (uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/")) uri.path.split('/').getOrNull(2)
                else uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            }
            else -> null
        }
        if (youtubeId?.matches(Regex("[A-Za-z0-9_-]{11}")) == true) "youtube:$youtubeId"
        else uri.normalize().toASCIIString()
    }.getOrDefault(url.trim())
}
