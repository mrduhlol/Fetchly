package com.fetchly.app.ui.result

import com.fetchly.app.data.engine.MapResult
import com.fetchly.app.data.engine.YtDlpMapper
import com.fetchly.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class QualitySafetyTest {
    private fun option(height: Int, audio: String) = QualityOption(
        "v$height", "${height}p", QualityKind.VIDEO, "mp4",
        listOf(StreamRef("v$height", "https://example.com/video", "mp4", "avc1", audio, height, null, null)),
        false, null,
    )
    @Test fun bestPrefersSoundOverSilentHigherResolution() {
        assertEquals("v720", ResultViewModel.preselect(listOf(option(1080, "none"), option(720, "mp4a")), DefaultQuality.BEST))
        assertNull(ResultViewModel.preselect(listOf(option(1080, "none")), DefaultQuality.BEST))
    }
    @Test fun unknownAudioIsNotAdvertisedAsConfirmedSound() {
        assertEquals("Audio availability unconfirmed", option(720, "unknown").audioDescription)
        assertEquals("No sound", option(720, "none").audioDescription)
        assertEquals("Includes audio", option(720, "mp4a").audioDescription)
    }
    @Test fun sourcePreferredAudioTrackIsPairedAndLanguagesRemainSelectable() {
        val json = """{"ok":true,"formats":[
          {"id":"v","url":"https://example.com/v","ext":"mp4","vcodec":"avc1","acodec":"none","height":1080},
          {"id":"dub","url":"https://example.com/dub","ext":"m4a","vcodec":"none","acodec":"mp4a","abr":192,"language":"hi","language_preference":0},
          {"id":"original","url":"https://example.com/original","ext":"m4a","vcodec":"none","acodec":"mp4a","abr":128,"language":"en","language_preference":10}
        ]}"""
        val media = (YtDlpMapper.map(json, "https://youtube.com/watch?v=test", Platform.YOUTUBE) as MapResult.Ok).info
        assertEquals("original", media.options.first { it.kind == QualityKind.VIDEO }.streams.last().formatId)
        assertEquals(setOf("en", "hi"), media.options.filter { it.kind == QualityKind.AUDIO }.map { it.streams.single().language }.toSet())
        assertEquals(media.options.size, media.options.map { it.id }.distinct().size)
    }
    @Test fun approximateSourceSizesAreKeptDistinctFromExactByteCounts() {
        val json = """{"ok":true,"formats":[{"id":"video","url":"https://example.com/video","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":720,"filesize_approx":1000000}]}"""
        val estimated = (YtDlpMapper.map(json, "https://example.com", Platform.OTHER) as MapResult.Ok).info
        assertTrue(estimated.options.first().streams.single().sizeIsEstimate)
        val exact = (YtDlpMapper.map(json.replace("filesize_approx", "filesize"), "https://example.com", Platform.OTHER) as MapResult.Ok).info
        assertFalse(exact.options.first().streams.single().sizeIsEstimate)
    }

}
