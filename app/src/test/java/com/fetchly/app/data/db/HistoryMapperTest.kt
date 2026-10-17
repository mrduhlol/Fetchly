package com.fetchly.app.data.db

import com.fetchly.app.domain.model.DownloadStatus
import com.fetchly.app.domain.model.HistoryEntry
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import com.fetchly.app.domain.resolve.OptionMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMapperTest {

    private val video = StreamRef(
        formatId = "137",
        url = "https://cdn.example.invalid/137",
        ext = "mp4",
        vcodec = "avc1.640028",
        acodec = "none",
        height = 1080,
        abr = null,
        sizeBytes = 80_911_999,
        protocol = "https",
    )

    private val audio = StreamRef(
        formatId = "140",
        url = "https://cdn.example.invalid/140",
        ext = "m4a",
        vcodec = "none",
        acodec = "mp4a.40.2",
        height = null,
        abr = 129,
        sizeBytes = null,
        protocol = null,
    )

    private val entry = HistoryEntry(
        id = "abc-123",
        title = "Some title",
        uploader = "Some uploader",
        sourceUrl = "https://www.youtube.com/watch?v=test123",
        platform = Platform.YOUTUBE,
        mediaType = MediaType.VIDEO,
        thumbnailUrl = "https://i.example.invalid/t.jpg",
        durationSeconds = 213,
        optionLabel = "1080p",
        kind = QualityKind.VIDEO,
        container = "mp4",
        sizeBytes = 84_361_446,
        signature = listOf(video, audio),
        status = DownloadStatus.QUEUED,
        errorMessage = null,
        errorRaw = null,
        outputUri = null,
        outputName = null,
        outputPath = null,
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000,
    )

    @Test
    fun `entry to entity and back is lossless`() {
        assertEquals(entry, entry.toEntity().toDomain())
    }

    @Test
    fun `entity carries the work name the queue uses`() {
        assertEquals("fetchly_abc-123", entry.toEntity().workName)
    }

    @Test
    fun `unknown enum names fall back instead of crashing`() {
        val entity = entry.toEntity().copy(
            platform = "MYSPACE",
            mediaType = "HOLOGRAM",
            kind = "MIRAGE",
            status = "EXPLODED",
        )

        val decoded = entity.toDomain()

        assertEquals(Platform.OTHER, decoded.platform)
        assertEquals(MediaType.VIDEO, decoded.mediaType)
        assertEquals(QualityKind.VIDEO, decoded.kind)
        assertEquals(DownloadStatus.FAILED, decoded.status)
    }

    @Test
    fun `corrupt stream json yields an empty signature, not a crash`() {
        val entity = entry.toEntity()
            .copy(signatureJson = "{broken", streamUrlsJson = "[broken")

        assertTrue(entity.toDomain().signature.isEmpty())
    }

    @Test
    fun `stored signature still matches a freshly resolved option`() {
        val stored = entry.toEntity().toDomain().signature

        val freshOption = QualityOption(
            id = "v1080",
            label = "1080p",
            kind = QualityKind.VIDEO,
            container = "mp4",
            streams = listOf(
                video.copy(formatId = "9999", url = "https://cdn.example.invalid/fresh/9999"),
                audio.copy(formatId = "8888", url = "https://cdn.example.invalid/fresh/8888"),
            ),
            needsMerge = true,
            sizeBytes = null,
        )

        assertEquals("v1080", OptionMatcher.match(stored, listOf(freshOption))?.id)
    }
}
