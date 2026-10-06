package com.fetchly.app.data.resolve

import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.util.MimeTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed interface ResolveOutcome {
    data class Resolved(val info: MediaInfo) : ResolveOutcome
    data class Failed(val message: String) : ResolveOutcome
}

/**
 * Local-first media resolution for directly accessible files.
 * Probes the real content type with HEAD (GET-without-body fallback) —
 * never guesses from the extension alone, never invents qualities.
 */
class DirectMediaResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {

    fun resolve(rawUrl: String): ResolveOutcome {
        val url = UrlSecurity.validate(rawUrl).getOrElse {
            return ResolveOutcome.Failed(it.message ?: "Enter a valid media link.")
        }
        return try {
            val head = Request.Builder().url(url).head()
                .header("User-Agent", "Fetchly/1.7").build()
            client.newCall(head).execute().use { res ->
                if (res.code == 404 || res.code == 410) {
                    return ResolveOutcome.Failed("This media is currently unavailable.")
                }
                val type = res.body?.contentType()
                if (res.isSuccessful && type != null && isMedia(type.toString())) {
                    return buildInfo(url, type.toString(), res.body!!.contentLength(), res)
                }
                // Some servers reject HEAD: retry as GET, read headers only.
                if (res.code == 405 || res.code == 501 || !res.isSuccessful) {
                    return tryGet(url)
                }
                ResolveOutcome.Failed("This source isn't supported yet.")
            }
        } catch (e: UnknownHostException) {
            ResolveOutcome.Failed("Couldn't connect to the source. Check your connection and try again.")
        } catch (e: SocketTimeoutException) {
            ResolveOutcome.Failed("The source took too long to respond. Try again.")
        } catch (e: Exception) {
            ResolveOutcome.Failed("Fetchly couldn't retrieve media information from this link.")
        }
    }

    private fun tryGet(url: String): ResolveOutcome {
        return try {
            val get = Request.Builder().url(url).header("User-Agent", "Fetchly/1.7").build()
            client.newCall(get).execute().use { res ->
                // Body is closed un-read: headers are all we need.
                if (res.code == 404 || res.code == 410) {
                    return ResolveOutcome.Failed("This media is currently unavailable.")
                }
                val type = res.body?.contentType()
                if (res.isSuccessful && type != null && isMedia(type.toString())) {
                    buildInfo(url, type.toString(), res.body!!.contentLength(), res)
                } else {
                    ResolveOutcome.Failed("This source isn't supported yet.")
                }
            }
        } catch (e: UnknownHostException) {
            ResolveOutcome.Failed("Couldn't connect to the source. Check your connection and try again.")
        } catch (e: SocketTimeoutException) {
            ResolveOutcome.Failed("The source took too long to respond. Try again.")
        } catch (e: Exception) {
            ResolveOutcome.Failed("Fetchly couldn't retrieve media information from this link.")
        }
    }

    private fun buildInfo(
        url: String,
        mime: String,
        length: Long,
        res: okhttp3.Response,
    ): ResolveOutcome {
        val mediaType = mediaTypeForMime(mime) ?: return ResolveOutcome.Failed(
            "This source isn't supported yet."
        )
        val container = containerForMime(mime, url)
        val title = titleFromUrl(url, res.header("Content-Disposition"))
        val format = MediaFormat(
            id = "direct-original",
            quality = "Original",
            container = container,
            sizeBytes = length.takeIf { it > 0 },
            downloadUrl = url,
            isAudioOnly = mediaType == MediaType.AUDIO,
        )
        return ResolveOutcome.Resolved(
            MediaInfo(
                sourceUrl = url,
                platform = Platform.DIRECT,
                title = title,
                author = null,
                thumbnailUrl = if (mediaType == MediaType.IMAGE) url else null,
                durationSecs = null,
                mediaType = mediaType,
                formats = listOf(format),
            )
        )
    }

    companion object {
        fun isMedia(mime: String): Boolean {
            val t = mime.substringBefore(';').trim().lowercase()
            return t.startsWith("video/") || t.startsWith("audio/") || t.startsWith("image/")
        }

        fun mediaTypeForMime(mime: String): MediaType? {
            val t = mime.substringBefore(';').trim().lowercase()
            return when {
                t.startsWith("video/") -> MediaType.VIDEO
                t.startsWith("audio/") -> MediaType.AUDIO
                t.startsWith("image/") -> MediaType.IMAGE
                else -> null
            }
        }

        fun containerForMime(mime: String, url: String): String {
            val t = mime.substringBefore(';').trim().lowercase()
            val mapped = when (t) {
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "video/x-matroska" -> "mkv"
                "audio/mpeg" -> "mp3"
                "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/ogg" -> "ogg"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/flac" -> "flac"
                "image/jpeg" -> "jpeg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> t.substringAfter('/', "")
            }
            if (mapped.isNotBlank()) return MimeTypes.extensionFor(mapped)
            // Last resort: trust the URL path, never invent.
            return extensionFromPath(url).ifBlank { "mp4" }
        }

        fun extensionFromPath(url: String): String {
            val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
            val raw = path.substringAfterLast('.', "").substringBefore('?').lowercase()
            if (raw.isBlank() || '/' in raw) return ""
            return MimeTypes.extensionFor(raw)
        }

        /** File name from Content-Disposition or the URL path, without extension. */
        fun titleFromUrl(url: String, contentDisposition: String?): String {
            contentDisposition?.let { cd ->
                Regex("filename\\*?=([^;]+)").find(cd)?.let { m ->
                    var name = m.groupValues[1].trim().trim('"')
                    name = name.substringAfter("''", name)
                    runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }?.let {
                            return it.substringBeforeLast('.', it).ifBlank { it }
                        }
                }
            }
            val last = runCatching { java.net.URI(url).path.orEmpty() }
                .getOrDefault("").substringAfterLast('/').substringBefore('?')
            if (last.isBlank()) return hostOf(url)
            val decoded = runCatching { java.net.URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)
            return decoded.substringBeforeLast('.', decoded).ifBlank { hostOf(url) }
        }

        private fun hostOf(url: String): String =
            runCatching { java.net.URI(url).host ?: "media" }.getOrDefault("media")
    }
}
