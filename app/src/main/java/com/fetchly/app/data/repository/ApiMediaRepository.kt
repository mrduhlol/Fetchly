package com.fetchly.app.data.repository

import com.fetchly.app.data.remote.AnalyzeRequest
import com.fetchly.app.data.remote.FetchlyApi
import com.fetchly.app.domain.model.MediaFormat
import com.fetchly.app.domain.model.MediaInfo
import com.fetchly.app.domain.model.MediaType
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.repository.AnalyzeResult
import com.fetchly.app.domain.repository.MediaRepository
import com.fetchly.app.domain.security.UrlSecurity
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Backend-backed analysis. The app never scrapes platforms directly;
 * all extraction lives behind the Fetchly API platform adapters.
 */
class ApiMediaRepository(
    private val api: FetchlyApi,
) : MediaRepository {

    override suspend fun analyze(rawUrl: String): AnalyzeResult {
        val valid = UrlSecurity.validate(rawUrl).getOrElse {
            return AnalyzeResult.Failure(it.message ?: "Enter a valid media link.")
        }
        val platform = PlatformDetector.detect(valid)
        if (platform == Platform.UNKNOWN) {
            return AnalyzeResult.Failure("Fetchly doesn't support this source yet.")
        }
        return try {
            val res = api.analyze(AnalyzeRequest(valid))
            if (!res.success || res.formats.isEmpty()) {
                AnalyzeResult.Failure(
                    res.error ?: "This media isn't publicly available or cannot be downloaded."
                )
            } else {
                AnalyzeResult.Success(
                    MediaInfo(
                        sourceUrl = valid,
                        platform = mapPlatform(res.platform, platform),
                        title = res.title.ifBlank { "Untitled media" },
                        author = res.author,
                        thumbnailUrl = res.thumbnail,
                        durationSecs = res.duration,
                        mediaType = mapType(res.mediaType),
                        formats = res.formats.map {
                            MediaFormat(
                                id = it.id,
                                quality = it.quality,
                                container = it.container,
                                sizeBytes = it.size,
                                downloadUrl = it.downloadUrl,
                                isAudioOnly = it.isAudioOnly,
                                bitrateKbps = it.bitrateKbps,
                                width = it.width,
                                height = it.height,
                                hasAudio = it.hasAudio,
                            )
                        },
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            AnalyzeResult.Failure(
                "Can't reach the Fetchly server. Check your connection, " +
                    "or set the API URL in Settings."
            )
        } catch (e: SocketTimeoutException) {
            AnalyzeResult.Failure("The request timed out. Try again on a better connection.")
        } catch (e: Exception) {
            AnalyzeResult.Failure("Fetchly couldn't analyze this link right now.")
        }
    }

    private fun mapPlatform(raw: String, fallback: Platform): Platform =
        runCatching { Platform.valueOf(raw.uppercase()) }.getOrDefault(fallback)

    private fun mapType(raw: String): MediaType =
        runCatching { MediaType.valueOf(raw.uppercase()) }.getOrDefault(MediaType.UNKNOWN)
}
