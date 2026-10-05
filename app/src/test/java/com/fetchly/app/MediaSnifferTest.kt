package com.fetchly.app

import com.fetchly.app.domain.util.MediaSniffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSnifferTest {

    @Test fun rejectsHtmlErrorPage() {
        assertFalse(MediaSniffer.looksLikeMedia("<!DOCTYPE html><html>".toByteArray()))
        assertFalse(MediaSniffer.looksLikeMedia("<html>".toByteArray()))
    }

    @Test fun rejectsJsonError() {
        assertFalse(MediaSniffer.looksLikeMedia("{\"error\":true}".toByteArray()))
        assertFalse(MediaSniffer.looksLikeMedia("[1,2]".toByteArray()))
    }

    @Test fun rejectsShortInput() {
        assertFalse(MediaSniffer.looksLikeMedia(ByteArray(0)))
        assertFalse(MediaSniffer.looksLikeMedia(byteArrayOf(1, 2)))
    }

    @Test fun acceptsJpegAndPng() {
        assertTrue(MediaSniffer.looksLikeMedia(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertTrue(MediaSniffer.looksLikeMedia(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
    }

    @Test fun acceptsMp4AndMp3() {
        val mp4 = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        assertTrue(MediaSniffer.looksLikeMedia(mp4))
        assertTrue(MediaSniffer.looksLikeMedia("ID3\u0004\u0000".toByteArray()))
        assertTrue(MediaSniffer.looksLikeMedia(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0, 0)))
    }

    @Test fun acceptsOggAndWebm() {
        assertTrue(MediaSniffer.looksLikeMedia("OggS\u0000".toByteArray()))
        assertTrue(MediaSniffer.looksLikeMedia(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())))
    }
}
