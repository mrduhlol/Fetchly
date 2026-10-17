package com.fetchly.app.data.direct

import com.fetchly.app.domain.util.MimeTypes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

sealed interface DirectResult {
    data class File(
        val url: String,
        val mime: String?,
        val sizeBytes: Long?,
        val filename: String?,
    ) : DirectResult

    data object NotDirect : DirectResult
}

interface DirectProbe {
    suspend fun resolve(url: String): DirectResult
}

/**
 * One cheap question: is this URL a plain media file that can be saved as-is,
 * or does it deserve the full yt-dlp treatment?
 *
 * HEAD first (no bytes downloaded); when the server refuses HEAD or answers
 * without a content type, a single ranged GET asks for the first byte and
 * reads the total size from Content-Range. Anything that is not a media mime
 * (web pages, JSON APIs, …) is NotDirect, which sends the link to the engine.
 */
class DirectMediaResolver(
    http: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DirectProbe {

    private val client = http.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(url: String): DirectResult = withContext(io) {
        val head = runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute()
        }.getOrNull()
        val headVerdict = head?.use { classify(url, it) }
        if (headVerdict != null) return@withContext headVerdict

        val ranged = runCatching {
            client.newCall(
                Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
            ).execute()
        }.getOrNull()
        ranged?.use { classify(url, it) } ?: DirectResult.NotDirect
    }

    /** null = HEAD was unusable, so a ranged GET is worth trying. */
    private fun classify(url: String, response: Response): DirectResult? {
        if (response.code !in 200..299) return null
        val rawType = response.header("Content-Type")?.trim() ?: return null
        val mime = rawType.substringBefore(';').trim().lowercase()
        if (mime.isEmpty()) return null
        if (!MimeTypes.isMediaMime(mime)) return DirectResult.NotDirect
        return DirectResult.File(
            url = url,
            mime = mime,
            sizeBytes = totalSize(response),
            filename = dispositionFilename(response) ?: pathFilename(url),
        )
    }

    private fun totalSize(response: Response): Long? {
        val contentRange = response.header("Content-Range")
        if (contentRange != null) {
            val total = contentRange.substringAfterLast('/', "").trim()
            if (total.isNotEmpty() && total != "*") return total.toLongOrNull()
        }
        return response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun dispositionFilename(response: Response): String? {
        val value = response.header("Content-Disposition") ?: return null
        val encoded = FILENAME_STAR.find(value)?.groupValues?.get(1)?.trim()?.trim('"')
        if (!encoded.isNullOrBlank()) {
            val name = encoded.substringAfter("''", encoded)
            return runCatching { URLDecoder.decode(name, "UTF-8") }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
        val plain = FILENAME.find(value)?.groupValues?.get(1)?.trim()?.trim('"')
        return plain?.takeIf { it.isNotBlank() }
    }

    private fun pathFilename(url: String): String? =
        runCatching { URI(url).path }.getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val TIMEOUT_SECONDS = 15L
        private val FILENAME_STAR = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        private val FILENAME = Regex("""filename(?!\*)\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
    }
}
