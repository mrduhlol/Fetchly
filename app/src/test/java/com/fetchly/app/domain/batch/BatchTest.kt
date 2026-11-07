package com.fetchly.app.domain.batch

import com.fetchly.app.domain.model.*
import com.fetchly.app.data.download.DownloadQueuePolicy
import com.fetchly.app.ui.downloads.*
import com.fetchly.app.domain.util.Filenames
import org.junit.Assert.*
import org.junit.Test

object BatchFixture {
    val stream = StreamRef("v", "https://example.com/file", "mp4", "h264", "aac", 720, null, null)
    val option = QualityOption("720", "720p", QualityKind.VIDEO, "mp4", listOf(stream), false, null)
    val media = MediaInfo("https://example.com/video", Platform.DIRECT, "Sample", null, null, null, MediaType.VIDEO, listOf(option))
    fun entry(id: String = "1", created: Long = 1) = HistoryEntry(id, media.title, null, media.sourceUrl, media.platform,
        MediaType.VIDEO, null, null, option.label, option.kind, option.container, null, option.streams,
        DownloadStatus.QUEUED, null, null, null, null, null, created, created)
}
class BatchTest {
    @Test fun `parse deduplicates links and refuses private hosts`() {
        val parsed = BatchLinks.parse("Here https://example.com/a https://example.com/a https://127.0.0.1/a")
        assertEquals(listOf("https://example.com/a"), parsed.urls)
        assertEquals(1, parsed.duplicates); assertEquals(1, parsed.rejected)
    }
    @Test fun `oversized batch must be rejected instead of silently truncated`() {
        assertTrue(BatchLinks.parse((1..51).joinToString("\n") { "https://example.com/$it" }).overLimit)
        assertTrue(BatchLinks.parse("a".repeat(BatchLinks.MAX_INPUT + 1)).overLimit)
    }
    @Test fun `quality cap never upgrades or chooses silent video`() {
        val base = BatchFixture.option
        val high = base.copy(id = "1080", streams = listOf(BatchFixture.stream.copy(height = 1080)))
        val silent = base.copy(id = "silent", streams = listOf(BatchFixture.stream.copy(acodec = "none", height = 2160)))
        val media = BatchFixture.media.copy(options = listOf(silent, high, base))
        assertEquals(high, BatchQuality.BEST.choose(media))
        assertEquals(base, BatchQuality.P720.choose(media))
        assertNull(BatchQuality.P720.choose(media.copy(options = listOf(high))))
        assertNull(BatchQuality.AUDIO.choose(media))
    }
    @Test fun `file originals work with every batch default`() {
        val file = BatchFixture.option.copy(kind = QualityKind.FILE)
        BatchQuality.entries.forEach { assertEquals(file, it.choose(BatchFixture.media.copy(options = listOf(file)))) }
    }
    @Test fun `queue admits oldest rows within two slots`() {
        val entries = listOf(BatchFixture.entry("new", 3), BatchFixture.entry("old", 1), BatchFixture.entry("mid", 2))
        assertEquals(listOf("old", "mid"), DownloadQueuePolicy.next(entries, emptySet()).map { it.id })
        assertEquals(listOf("old"), DownloadQueuePolicy.next(entries, setOf("new")).map { it.id })
        assertTrue(DownloadQueuePolicy.next(entries, setOf("new", "old")).isEmpty())
    }
    @Test fun `duplicate matching survives refreshed urls but respects sources and language`() {
        val entry = BatchFixture.entry()
        assertTrue(DownloadQueuePolicy.sameDownload(entry, BatchFixture.media, BatchFixture.option.copy(streams = listOf(BatchFixture.stream.copy(url = "https://example.com/new-token")))))
        assertFalse(DownloadQueuePolicy.sameDownload(entry, BatchFixture.media.copy(sourceUrl = "https://example.com/other"), BatchFixture.option))
        assertFalse(DownloadQueuePolicy.sameDownload(entry, BatchFixture.media, BatchFixture.option.copy(streams = listOf(BatchFixture.stream.copy(language = "hi")))))
    }
    @Test fun `search matches actual file name and combines type filters`() {
        val saved = BatchFixture.entry().copy(outputName = "Holiday clip.mp4", status = DownloadStatus.COMPLETED)
        assertEquals(listOf(saved), DownloadLibrary.filter(listOf(saved), "HOLIDAY clip", LibraryFilter.VIDEO))
        assertTrue(DownloadLibrary.filter(listOf(saved), "holiday", LibraryFilter.AUDIO).isEmpty())
        assertTrue(DownloadLibrary.filter(listOf(saved), "holiday", LibraryFilter.FAILED).isEmpty())
    }
    @Test fun `unicode names fit file systems without splitting emoji or allowing path extensions`() {
        val title = "🎬".repeat(100)
        val stem = Filenames.sanitize(title)
        assertTrue(stem.toByteArray().size <= 180)
        assertFalse(Character.isHighSurrogate(stem.last()))
        val name = Filenames.displayName(title, "🎬".repeat(90), "../../mp4")
        assertTrue(name.toByteArray().size < 255)
        assertFalse(name.contains('/')); assertTrue(name.endsWith(".bin"))
    }
}
