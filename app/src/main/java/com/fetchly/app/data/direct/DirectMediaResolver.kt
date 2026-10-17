package com.fetchly.app.data.direct

import com.fetchly.app.domain.util.MimeTypes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

/** "Is this URL a media file?" — faked in repository tests. */
interface DirectProbe {
    suspend fun resolve(url: String): DirectResult
}

/**
 * Asks a URL (HEAD first, ranged GET as fallback) whether it serves a media
 * file directly. HTML/JSON/unknown types — and any network failure — come
 * back as [DirectResult.NotDirect] so the caller can hand the link to yt-dlp
 * instead of pretending it failed.
 */
class DirectMediaResolver(
    http: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DirectProbe {

    private val client = http.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(url: String): DirectResult = withContext(io) {
        val head = fetch(Request.Builder().url(url).head().build())
            ?: return@withContext DirectResult.NotDirect
        if (head.answers()) return@withContext classify(url, head)

        val ranged = fetch(
            Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .get()
                .build()
        )
        if (ranged != null && ranged.answers()) classify(url, ranged) else DirectResult.NotDirect
    }

    private fun classify(url: String, response: ProbeResponse): DirectResult {
        val mime = response.contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (!MimeTypes.isMediaMime(mime)) return DirectResult.NotDirect
        return DirectResult.File(
            url = url,
            mime = mime,
            sizeBytes = totalSize(response),
            filename = filename(url, response.disposition),
        )
    }

    private fun totalSize(response: ProbeResponse): Long? {
        response.contentRange?.let { range ->
            val total = range.substringAfterLast('/', "").trim()
            if (total.isNotEmpty() && total != "*") return total.toLongOrNull()
        }
        return response.contentLength?.takeIf { it > 0 }
    }

    private fun filename(url: String, disposition: String?): String? {
        disposition?.let { header ->
            FILENAME_STAR.find(header)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { return decode(it.trim().trim('"')) }
            FILENAME_PLAIN.find(header)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.trim().trim('"') }
        }
        return runCatching { URI(url).path }.getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun fetch(request: Request): ProbeResponse? = runCatching {
        client.newCall(request).execute().use { response ->
            ProbeResponse(
                code = response.code,
                contentType = response.header("Content-Type"),
                contentLength = response.header("Content-Length")?.toLongOrNull(),
                contentRange = response.header("Content-Range"),
                disposition = response.header("Content-Disposition"),
            )
        }
    }.getOrNull()

    private data class ProbeResponse(
        val code: Int,
        val contentType: String?,
        val contentLength: Long?,
        val contentRange: String?,
        val disposition: String?,
    ) {
        /** True when this response alone can be classified (no retry needed). */
        fun answers(): Boolean = code in 200..299 && !contentType.isNullOrBlank()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        val FILENAME_STAR = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
        val FILENAME_PLAIN = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
    }
}
