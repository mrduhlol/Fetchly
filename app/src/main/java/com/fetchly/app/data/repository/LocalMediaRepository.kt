package com.fetchly.app.data.repository

import android.content.Context
import com.fetchly.app.data.resolve.DirectMediaResolver
import com.fetchly.app.data.resolve.ResolveOutcome
import com.fetchly.app.domain.model.Platform
import com.fetchly.app.domain.platform.PlatformDetector
import com.fetchly.app.domain.platform.SourceCapabilities
import com.fetchly.app.domain.repository.AnalyzeResult
import com.fetchly.app.domain.repository.MediaRepository
import com.fetchly.app.domain.security.UrlSecurity

/**
 * Local-first analysis. No Fetchly server involved:
 * - Social platforms: honestly reported as unsupported until a legitimate
 *   local or authorized mechanism exists for them.
 * - Direct media files: probed locally (content type, size, name).
 */
class LocalMediaRepository(
    private val context: Context,
    private val direct: DirectMediaResolver = DirectMediaResolver(),
) : MediaRepository {

    private val youtube by lazy { YtDlpMediaRepository(context) }

    override suspend fun analyze(rawUrl: String): AnalyzeResult {
        val valid = UrlSecurity.validate(rawUrl).getOrElse {
            return AnalyzeResult.Failure(it.message ?: "Enter a valid media link.")
        }
        val platform = PlatformDetector.detectWithDirect(valid)
        val capability = SourceCapabilities.forPlatform(platform)

        // Engine-backed platforms resolve fully on-device.
        if (platform == Platform.YOUTUBE && capability.supported) {
            return youtube.analyze(valid)
        }
        if (platform != Platform.DIRECT && platform != Platform.UNKNOWN) {
            return AnalyzeResult.Failure(
                capability.unsupportedReason ?: "This source isn't supported yet."
            )
        }
        return when (val out = direct.resolve(valid)) {
            is ResolveOutcome.Resolved -> AnalyzeResult.Success(out.info)
            is ResolveOutcome.Failed -> AnalyzeResult.Failure(out.message)
        }
    }
}
