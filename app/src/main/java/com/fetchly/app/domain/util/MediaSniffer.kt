package com.fetchly.app.domain.util

/** Magic-byte sniffing so HTML/JSON error pages are never saved as media. */
object MediaSniffer {

    /**
     * Returns true when [header] (first bytes of a download) looks like a
     * real media container. Rejects HTML (`<...`) and JSON (`{...`/`[...]`)
     * error responses outright.
     */
    fun looksLikeMedia(header: ByteArray): Boolean {
        if (header.size < 4) return false
        // Error pages, not media.
        if (header[0] == '<'.code.toByte()) return false
        if (header[0] == '{'.code.toByte() || header[0] == '['.code.toByte()) return false

        // Images.
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return true // JPEG
        if (header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()
        ) return true // PNG
        if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte()
        ) return true // GIF
        if (header.size >= 12 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        ) return true // WebP

        // Video / audio containers.
        if (header.size >= 8 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        ) return true // MP4 / MOV / M4A
        if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) return true // WebM / MKV
        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) return true // MP3 with ID3
        if (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) return true // MP3 frame
        if (header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()
        ) return true // OGG
        if (header.size >= 12 && header[0] == 'R'.code.toByte() && header[8] == 'W'.code.toByte() &&
            header[9] == 'A'.code.toByte() && header[10] == 'V'.code.toByte() &&
            header[11] == 'E'.code.toByte()
        ) return true // WAV
        if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
        ) return true // FLAC

        return false
    }
}
