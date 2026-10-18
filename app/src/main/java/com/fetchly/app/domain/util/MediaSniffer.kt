package com.fetchly.app.domain.util

import java.io.File

/**
 * Magic-byte checks that catch the classic download lie: a server answering
 * HTTP 200 with an HTML page (or a sign-in wall) saved as `video.mp4`.
 *
 * Only containers Fetchly itself can produce are checked. An unknown
 * container is accepted — there is nothing to compare it against, and
 * pretending to verify would be worse than admitting we can't.
 */
object MediaSniffer {

    private const val HEADER_BYTES = 64

    /**
     * The container the file's first bytes actually look like, or null when
     * the file is missing, too short, or not a format Fetchly recognises.
     * `ftyp` brands report "mp4" (the ISO base media family: mp4/m4a/mov).
     */
    fun containerOf(file: File): String? {
        if (!file.isFile) return null
        val head = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(head) }
        if (read < 12) return null
        val text = String(head, 0, read, Charsets.ISO_8859_1)
        return when {
            text.regionMatches(4, "ftyp", 0, 4) -> "mp4"
            text.regionMatches(0, "OggS", 0, 4) -> "ogg"
            text.regionMatches(0, "fLaC", 0, 4) -> "flac"
            text.regionMatches(0, "GIF8", 0, 4) -> "gif"
            text.regionMatches(0, "ID3", 0, 3) -> "mp3"
            text.regionMatches(0, "RIFF", 0, 4) && text.regionMatches(8, "WAVE", 0, 4) -> "wav"
            text.regionMatches(0, "RIFF", 0, 4) && text.regionMatches(8, "WEBP", 0, 4) -> "webp"
            isEbml(head) -> if (text.contains("webm")) "webm" else "mkv"
            isJpeg(head) -> "jpg"
            isPng(head) -> "png"
            isMp3Frame(head) -> "mp3"
            isAacFrame(head) -> "aac"
            else -> null
        }
    }

    /**
     * Does the file's magic bytes fit [container]? Unknown containers are
     * accepted (nothing to verify against); a known container whose magic is
     * wrong — or missing — is rejected.
     */
    fun matches(file: File, container: String): Boolean {
        val accepted = FAMILIES[container.lowercase()] ?: return true
        val actual = containerOf(file) ?: return false
        return actual in accepted
    }

    private fun isEbml(head: ByteArray): Boolean =
        head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()

    private fun isJpeg(head: ByteArray): Boolean =
        head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()

    private fun isPng(head: ByteArray): Boolean =
        head[0] == 0x89.toByte() && head[1] == 0x50.toByte() && head[2] == 0x4E.toByte() &&
            head[3] == 0x47.toByte() && head[4] == 0x0D.toByte() && head[5] == 0x0A.toByte() &&
            head[6] == 0x1A.toByte() && head[7] == 0x0A.toByte()

    /** MPEG audio frame sync: 11 sync bits with a layer other than "reserved". */
    private fun isMp3Frame(head: ByteArray): Boolean {
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0
    }

    /** ADTS AAC frame sync: 12 sync bits with layer bits "00". */
    private fun isAacFrame(head: ByteArray): Boolean {
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return b0 == 0xFF && (b1 and 0xF6) == 0xF0
    }

    /** container spelling → the sniffed containers that satisfy it. */
    private val FAMILIES: Map<String, Set<String>> = mapOf(
        "mp4" to setOf("mp4"), "m4v" to setOf("mp4"), "mov" to setOf("mp4"),
        "m4a" to setOf("mp4"),
        "mkv" to setOf("mkv", "webm"), "webm" to setOf("webm", "mkv"),
        "mp3" to setOf("mp3"), "aac" to setOf("aac"),
        "ogg" to setOf("ogg"), "opus" to setOf("ogg"), "oga" to setOf("ogg"),
        "wav" to setOf("wav"), "flac" to setOf("flac"),
        "jpg" to setOf("jpg"), "jpeg" to setOf("jpg"),
        "png" to setOf("png"), "gif" to setOf("gif"), "webp" to setOf("webp"),
    )
}
