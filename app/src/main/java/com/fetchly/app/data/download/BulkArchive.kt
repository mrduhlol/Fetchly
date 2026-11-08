package com.fetchly.app.data.download

import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Stream already-compressed media into an archive with predictable safe entry names. */
internal object BulkArchive {
    fun write(files: List<File>, output: File, checkCancelled: () -> Unit = {}) {
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                files.forEachIndexed { index, file ->
                    checkCancelled()
                    val ext = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
                    zip.putNextEntry(ZipEntry("media-${(index + 1).toString().padStart(2, '0')}.$ext"))
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            checkCancelled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }
}
