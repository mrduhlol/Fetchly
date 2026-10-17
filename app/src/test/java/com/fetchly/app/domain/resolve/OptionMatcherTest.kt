package com.fetchly.app.domain.resolve

import com.fetchly.app.domain.model.QualityKind
import com.fetchly.app.domain.model.QualityOption
import com.fetchly.app.domain.model.StreamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionMatcherTest {

    private fun ref(
        id: String,
        height: Int? = null,
        vcodec: String = "avc1",
        acodec: String = "none",
        ext: String = "mp4",
    ) = StreamRef(
        formatId = id,
        url = "https://cdn.invalid/$id",
        ext = ext,
        vcodec = vcodec,
        acodec = acodec,
        height = height,
        abr = null,
        sizeBytes = null,
    )

    private val video1080 = ref("137", height = 1080)
    private val audioM4a = ref("140", vcodec = "none", acodec = "mp4a.40.2", ext = "m4a")

    private fun videoOption(vararg streams: StreamRef) = QualityOption(
        id = "v1080",
        label = "1080p",
        kind = QualityKind.VIDEO,
        container = "mp4",
        streams = streams.toList(),
        needsMerge = streams.size > 1,
        sizeBytes = null,
    )

    private fun audioOption() = QualityOption(
        id = "audio",
        label = "Audio",
        kind = QualityKind.AUDIO,
        container = "m4a",
        streams = listOf(audioM4a),
        needsMerge = false,
        sizeBytes = null,
    )

    @Test
    fun `primary match ignores format ids and stream order`() {
        val signature = listOf(video1080, audioM4a)
        val options = listOf(
            videoOption(
                audioM4a.copy(formatId = "new-audio", url = "https://cdn.invalid/new-audio"),
                video1080.copy(formatId = "new-video", url = "https://cdn.invalid/new-video"),
            ),
            audioOption(),
        )

        assertEquals("v1080", OptionMatcher.match(signature, options)?.id)
    }

    @Test
    fun `fallback matches on height and extension when codecs changed`() {
        val signature = listOf(video1080, audioM4a)
        val options = listOf(
            videoOption(
                video1080.copy(vcodec = "vp9", url = "https://cdn.invalid/vp9"),
                audioM4a.copy(acodec = "mp4a.40.5", url = "https://cdn.invalid/mp4a5"),
            ),
        )

        assertEquals("v1080", OptionMatcher.match(signature, options)?.id)
    }

    @Test
    fun `different height does not match`() {
        val signature = listOf(video1080)
        val options = listOf(videoOption(video1080.copy(height = 720)))

        assertNull(OptionMatcher.match(signature, options))
    }

    @Test
    fun `audio only signature matches the audio option`() {
        val options = listOf(audioOption())

        assertEquals("audio", OptionMatcher.match(listOf(audioM4a), options)?.id)
    }

    @Test
    fun `muxed signature does not match a split pair`() {
        val muxed = ref("18", height = 360, vcodec = "avc1.42001E", acodec = "mp4a.40.2")
        val options = listOf(
            videoOption(ref("split-v", height = 360), audioM4a),
        )

        assertNull(OptionMatcher.match(listOf(muxed), options))
    }

    @Test
    fun `empty signature never matches`() {
        assertNull(OptionMatcher.match(emptyList(), listOf(audioOption())))
    }
}
