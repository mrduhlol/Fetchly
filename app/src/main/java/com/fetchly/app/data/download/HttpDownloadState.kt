package com.fetchly.app.data.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** Only bytes flushed to disk may be advertised in the atomically replaced sidecar. */
internal class HttpDownloadState private constructor(
    private val dest: File,
    val sourceUrl: String,
    val resolvedUrl: String,
    val total: Long,
    val etag: String,
    private val offsets: LongArray,
) {
    val count: Int get() = offsets.size
    fun start(index: Int): Long = total * index / count
    fun end(index: Int): Long = total * (index + 1) / count - 1
    fun length(index: Int): Long = end(index) - start(index) + 1
    @Synchronized fun offset(index: Int): Long = offsets[index]
    @Synchronized fun downloaded(): Long = offsets.sum()

    @Synchronized fun checkpoint(index: Int, bytes: Long, output: RandomAccessFile) = storageIo {
        require(bytes in offsets[index]..length(index))
        output.fd.sync()
        offsets[index] = bytes
        save()
    }

    private fun save() {
        val json = JSONObject().apply {
            put("version", 1)
            put("source", sourceUrl)
            put("resolved", resolvedUrl)
            put("total", total)
            put("etag", etag)
            put("offsets", JSONArray(offsets.toList()))
        }
        val state = sidecar(dest)
        val temp = File(state.path + ".tmp")
        temp.outputStream().use {
            it.write(json.toString().toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        Files.move(temp.toPath(), state.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        fun sidecar(dest: File): File = File(dest.path + ".resume.json")

        fun clear(dest: File) = storageIo {
            for (file in listOf(sidecar(dest), File(sidecar(dest).path + ".tmp"))) {
                if (file.exists() && !file.delete()) throw IOException("Could not remove download checkpoint")
            }
        }

        fun open(dest: File, source: String, resolved: String, total: Long, etag: String, count: Int): HttpDownloadState = storageIo {
            val state = sidecar(dest)
            val saved = if (dest.isFile && dest.length() == total && state.length() in 1..16_384) {
                runCatching {
                    val json = JSONObject(state.readText())
                    require(json.getInt("version") == 1 && json.getString("source") == source &&
                        json.getString("resolved") == resolved && json.getLong("total") == total &&
                        json.getString("etag") == etag)
                    val array = json.getJSONArray("offsets")
                    require(array.length() == count)
                    LongArray(count) { index ->
                        array.getLong(index).also {
                            val size = total * (index + 1) / count - total * index / count
                            require(it in 0..size)
                        }
                    }
                }.getOrNull()
            } else null
            if (saved != null) return@storageIo HttpDownloadState(dest, source, resolved, total, etag, saved)
            clear(dest)
            RandomAccessFile(dest, "rw").use {
                it.setLength(0)
                it.setLength(total)
                it.fd.sync()
            }
            HttpDownloadState(dest, source, resolved, total, etag, LongArray(count)).also { it.save() }
        }
    }
}

internal class DownloadStorageException(cause: IOException) : IOException(cause.message, cause)

internal inline fun <T> storageIo(block: () -> T): T = try {
    block()
} catch (exc: DownloadStorageException) {
    throw exc
} catch (exc: IOException) {
    throw DownloadStorageException(exc)
}
