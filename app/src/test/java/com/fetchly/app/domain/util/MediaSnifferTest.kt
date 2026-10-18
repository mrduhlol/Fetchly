package com.fetchly.app.domain.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaSnifferTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `ftyp brands are the whole iso media family`() {
        val mp4 = file("one.mp4", ascii("\u0000\u0000\u0000\u0018ftypmp42"))
        assertEquals("mp4", MediaSniffer.containerOf(mp4))
        assertTrue(MediaSniffer.matches(mp4, "mp4"))
        assertTrue(MediaSniffer.matches(mp4, "m4a"))
        assertTrue(MediaSniffer.matches(mp4, "MOV"))
        assertFalse(MediaSniffer.matches(mp4, "mkv"))
    }

    /**
     * The regression test for v1.8's exact failure class: a server answering
     * 200 with an HTML sign-in wall saved under a .mp4 name.
     */
    @Test
    fun `an html sign-in page saved as mp4 is rejected`() {
        val lie = file(
            "video.mp4",
            ascii("<!DOCTYPE html><html>Sign in to confirm you're not a bot</html>"),
        )
        assertNull(MediaSniffer.containerOf(lie))
        assertFalse(MediaSniffer.matches(lie, "mp4"))
        assertFalse(MediaSniffer.matches(lie, "m4a"))
    }

    @Test
    fun `unknown containers are accepted with nothing to check against`() {
        val lie = file("clip.flv", ascii("<!DOCTYPE html><html></html>"))
        assertTrue(MediaSniffer.matches(lie, "flv"))
    }

    @Test
    fun `files too short to hold magic bytes are rejected`() {
        val tiny = file("tiny.mp4", ascii("ftypmp42"))
        assertNull(MediaSniffer.containerOf(tiny))
        assertFalse(MediaSniffer.matches(tiny, "mp4"))
    }

    @Test
    fun `missing files fail every known container`() {
        val missing = File(folder.root, "never-downloaded.mp4")
        assertNull(MediaSniffer.containerOf(missing))
        assertFalse(MediaSniffer.matches(missing, "mp4"))
    }

    @Test
    fun `ogg family covers opus and oga`() {
        val ogg = file("track.opus", ascii("OggS\u0000\u0002\u0000\u0000"))
        assertEquals("ogg", MediaSniffer.containerOf(ogg))
        assertTrue(MediaSniffer.matches(ogg, "opus"))
        assertTrue(MediaSniffer.matches(ogg, "oga"))
        assertFalse(MediaSniffer.matches(ogg, "mp3"))
    }

    @Test
    fun `ebml doctype separates webm from mkv`() {
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val webm = file("v.webm", ebml + ascii("....webm...."))
        val mkv = file("v.mkv", ebml + ascii("....matroska...."))
        assertEquals("webm", MediaSniffer.containerOf(webm))
        assertEquals("mkv", MediaSniffer.containerOf(mkv))
        // The mkv/webm families accept each other; both are the same EBML cage.
        assertTrue(MediaSniffer.matches(webm, "mkv"))
        assertTrue(MediaSniffer.matches(mkv, "webm"))
        assertFalse(MediaSniffer.matches(mkv, "mp4"))
    }

    @Test
    fun `mp3 arrives as an id3 tag or a raw frame sync`() {
        val tagged = file("a.mp3", ascii("ID3\u0003\u0000\u0000\u0000"))
        val raw = file("b.mp3", byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64))
        assertEquals("mp3", MediaSniffer.containerOf(tagged))
        assertEquals("mp3", MediaSniffer.containerOf(raw))
    }

    @Test
    fun `adts sync identifies aac`() {
        val aac = file("a.aac", byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()))
        assertEquals("aac", MediaSniffer.containerOf(aac))
    }

    @Test
    fun `image magic bytes are recognised`() {
        val png = file("i.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val jpg = file("i.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        val gif = file("i.gif", ascii("GIF89a...."))
        val webp = file("i.webp", ascii("RIFF\u0000\u0000\u0000\u0000WEBP"))
        assertEquals("png", MediaSniffer.containerOf(png))
        assertEquals("jpg", MediaSniffer.containerOf(jpg))
        assertEquals("gif", MediaSniffer.containerOf(gif))
        assertEquals("webp", MediaSniffer.containerOf(webp))
        assertTrue(MediaSniffer.matches(jpg, "jpeg"))
        assertTrue(MediaSniffer.matches(png, "PNG"))
    }

    @Test
    fun `wav and flac are recognised`() {
        val wav = file("a.wav", ascii("RIFF\u0000\u0000\u0000\u0000WAVE"))
        val flac = file("a.flac", ascii("fLaC...."))
        assertEquals("wav", MediaSniffer.containerOf(wav))
        assertEquals("flac", MediaSniffer.containerOf(flac))
        assertFalse(MediaSniffer.matches(wav, "flac"))
    }

    private fun file(name: String, bytes: ByteArray): File =
        folder.newFile(name).apply { writeBytes(padded(bytes)) }

    /** Sniffing only looks at the head; pad so short magics still reach 12 bytes. */
    private fun padded(bytes: ByteArray): ByteArray = bytes.copyOf(maxOf(64, bytes.size))

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)
}
