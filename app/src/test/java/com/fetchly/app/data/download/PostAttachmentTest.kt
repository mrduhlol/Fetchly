package com.fetchly.app.data.download

import com.fetchly.app.data.engine.MapResult
import com.fetchly.app.data.engine.YtDlpMapper
import com.fetchly.app.domain.batch.BatchFixture
import com.fetchly.app.domain.model.*
import com.fetchly.app.domain.resolve.OptionMatcher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostAttachmentTest {
    private fun format(id: String, photo: Boolean) = JSONObject().put("format_id", id)
        .put("url", "https://cdn.example.com/$id.${if (photo) "jpg" else "mp4"}")
        .put("ext", if (photo) "jpg" else "mp4").put("vcodec", if (photo) "none" else "h264")
        .put("acodec", if (photo) "none" else "aac").apply { if (!photo) put("height", 720) }
    private fun post(photo: Boolean, count: Int): MediaInfo {
        val root = JSONObject().put("ok", true).put("title", "Post")
            .put("formats", JSONArray().put(format("first", photo)))
            .put("entries", JSONArray().apply { repeat(count) { index ->
                put(JSONObject().put("title", "Clip").put("formats", JSONArray().put(format("item$index", photo))))
            } })
        return (YtDlpMapper.map(root.toString(), "https://x.com/user/status/123", Platform.TWITTER) as MapResult.Ok).info
    }
    @Test fun `photo attachments get independent original URLs and preview images`() {
        val info = post(true, 5)
        assertEquals(5, info.items.size)
        assertEquals(5, info.items.map { it.sourceUrl }.distinct().size)
        assertTrue(info.items.all { it.platform == Platform.DIRECT && it.mediaType == MediaType.IMAGE && it.thumbnailUrl == it.sourceUrl })
    }
    @Test fun `same-shape video attachments are not treated as duplicate downloads`() {
        val info = post(false, 2)
        val first = info.items[0].options.first { it.kind == QualityKind.VIDEO }
        val second = info.items[1].options.first { it.kind == QualityKind.VIDEO }
        val stored = BatchFixture.entry().copy(sourceUrl = info.sourceUrl, signature = first.streams,
            kind = first.kind, container = first.container)
        assertTrue(DownloadQueuePolicy.sameDownload(stored, info.items[0], first))
        assertFalse(DownloadQueuePolicy.sameDownload(stored, info.items[1], second))
        assertEquals(second, OptionMatcher.match(second.streams, listOf(first, second)))
        assertNull(OptionMatcher.match(second.streams, listOf(first)))
    }
    @Test fun `Lite has one queue slot while full keeps two`() {
        val entries = listOf(BatchFixture.entry("first", 1), BatchFixture.entry("second", 2))
        assertEquals(listOf("first"), DownloadQueuePolicy.next(entries, emptySet(), 1).map { it.id })
        assertTrue(DownloadQueuePolicy.next(entries, setOf("live"), 1).isEmpty())
        assertEquals(2, DownloadQueuePolicy.next(entries, emptySet()).size)
    }
}
