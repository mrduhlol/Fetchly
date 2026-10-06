package com.fetchly.app.data.repository

import android.content.Context
import com.fetchly.app.data.engine.YtDlpEngine
import com.fetchly.app.data.engine.YtDlpMapper
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.repository.AnalyzeResult
import com.fetchly.app.domain.repository.MediaRepository
import com.fetchly.app.domain.security.UrlSecurity
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Analysis through the on-device yt-dlp engine. Used only for platforms
 * the capability matrix marks supported.
 */
class YtDlpMediaRepository(
    private val context: Context,
) : MediaRepository {

    override suspend fun analyze(rawUrl: String): AnalyzeResult {
        val valid = UrlSecurity.validate(rawUrl).getOrElse {
            return AnalyzeResult.Failure(it.message ?: "Enter a valid media link.")
        }
        return try {
            val json = YtDlpEngine.extractJson(context, valid)
            val mapped = YtDlpMapper.fromJson(valid, Platform.YOUTUBE, json)
            val info = mapped.info
            if (info != null) {
                AnalyzeResult.Success(info)
            } else {
                AnalyzeResult.Failure(classify(mapped.error.orEmpty()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            AnalyzeResult.Failure("Couldn't connect to the source. Check your connection and try again.")
        } catch (e: SocketTimeoutException) {
            AnalyzeResult.Failure("The source took too long to respond. Try again.")
        } catch (e: Exception) {
            AnalyzeResult.Failure("Fetchly couldn't retrieve media information from this link.")
        }
    }

    private fun classify(error: String): String = when {
        error.contains("Private video", ignoreCase = true) ||
            error.contains("Login required", ignoreCase = true) ->
            "This media isn't publicly available or cannot be downloaded."
        error.contains("Unsupported URL", ignoreCase = true) ->
            "This source isn't supported yet."
        error.contains("Unable to extract", ignoreCase = true) ||
            error.contains("Requested content is not available", ignoreCase = true) ->
            "This media is currently unavailable."
        else -> "Fetchly couldn't retrieve media information from this link."
    }
}
