package com.fetchly.app

import com.fetchly.app.data.remote.AnalyzeRequest
import com.fetchly.app.data.remote.AnalyzeResponse
import com.fetchly.app.data.remote.FetchlyApi
import com.fetchly.app.data.remote.FormatDto
import com.fetchly.app.data.repository.ApiMediaRepository
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.repository.AnalyzeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMappingTest {

    private fun repo(response: AnalyzeResponse) = ApiMediaRepository(
        object : FetchlyApi {
            override suspend fun analyze(body: AnalyzeRequest): AnalyzeResponse = response
        }
    )

    @Test fun mapsFullMetadata() = runBlocking {
        val r = repo(
            AnalyzeResponse(
                success = true,
                platform = "youtube",
                title = "Amazing Video",
                author = "creator",
                thumbnail = "https://img/x.jpg",
                mediaType = "video",
                duration = 272,
                formats = listOf(
                    FormatDto("f1", "1080p", "mp4", 80_000_000, "https://dl/f1"),
                    FormatDto(
                        "a1", "192 kbps", "mp3", 5_200_000, "https://dl/a1",
                        isAudioOnly = true, bitrateKbps = 192, hasAudio = true,
                    ),
                    FormatDto(
                        "i1", "Original", "jpeg", 2_400_000, "https://dl/i1",
                        width = 2048, height = 1365,
                    ),
                ),
            )
        ).analyze("https://www.youtube.com/watch?v=abc")

        assertTrue(r is AnalyzeResult.Success)
        val info = (r as AnalyzeResult.Success).info
        assertEquals(Platform.YOUTUBE, info.platform)
        assertEquals("Amazing Video", info.title)
        assertEquals("creator", info.author)
        assertEquals(272L, info.durationSecs)
        assertEquals(MediaType.VIDEO, info.mediaType)
        assertEquals(2, info.videoFormats.size)
        assertEquals(1, info.audioFormats.size)
        val audio = info.audioFormats.first()
        assertEquals("192 kbps", audio.bitrateLabel)
        val image = info.videoFormats.first { it.id == "i1" }
        assertEquals("2048 × 1365", image.dimensionsLabel)
    }

    @Test fun missingMetadataStaysNull() = runBlocking {
        val r = repo(
            AnalyzeResponse(
                success = true,
                platform = "instagram",
                title = "Clip",
                formats = listOf(FormatDto("f1", "Best", "mp4", null, "https://dl/f1")),
            )
        ).analyze("https://www.instagram.com/p/abc/")
        val info = (r as AnalyzeResult.Success).info
        val f = info.formats.first()
        assertEquals(null, f.bitrateLabel)
        assertEquals(null, f.dimensionsLabel)
        assertEquals("Unknown size", com.fetchly.app.domain.util.MimeTypes.displaySize(f.sizeBytes))
    }

    @Test fun unsupportedSourceShortCircuits() = runBlocking {
        val r = repo(AnalyzeResponse(success = false)).analyze("https://example.com/x")
        assertTrue(r is AnalyzeResult.Failure)
        assertEquals(
            "Fetchly doesn't support this source yet.",
            (r as AnalyzeResult.Failure).message,
        )
    }
}
