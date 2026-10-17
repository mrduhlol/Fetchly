package com.fetchly.app.data.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** What the repository needs from the engine: extraction as raw JSON. */
interface LinkExtractor {
    suspend fun extract(url: String): String
}

sealed interface EngineDownload {
    data class Ok(val file: File) : EngineDownload
    data object Cancelled : EngineDownload
    data class Failed(val message: String) : EngineDownload
}

/**
 * Kotlin facade over the Chaquopy-packaged `fetchly_yt` module.
 *
 * Every call runs on [io]; Python starts lazily, exactly once. The engine
 * never throws at the caller: failures come back as JSON `ok:false`
 * (extract) or [EngineDownload.Failed] (download), always keeping the raw
 * yt-dlp message so the app can name the real cause.
 */
class YtDlpEngine(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LinkExtractor {

    /** e.g. "2026.08.19", or "missing" when the wheel didn't ship. */
    suspend fun engineVersion(): String = withContext(io) {
        runCatching { module().callAttr("version").toString() }.getOrDefault("missing")
    }

    override suspend fun extract(url: String): String = withContext(io) {
        runCatching {
            module().callAttr("extract", url).toString()
        }.getOrElse { exc ->
            failureJson(exc.message ?: "The engine crashed while resolving this link.")
        }
    }

    suspend fun download(
        pageUrl: String,
        formatId: String,
        destDir: File,
        prefix: String,
        hook: DownloadHook,
    ): EngineDownload = withContext(io) {
        destDir.mkdirs()
        val raw = runCatching {
            module().callAttr(
                "download",
                pageUrl,
                formatId,
                destDir.absolutePath,
                prefix,
                hook,
            ).toString()
        }.getOrElse { exc ->
            return@withContext EngineDownload.Failed(
                exc.message ?: "The engine crashed while downloading."
            )
        }
        parseDownloadResult(raw)
    }

    @Synchronized
    private fun module(): PyObject {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance().getModule(MODULE)
    }

    private fun parseDownloadResult(raw: String): EngineDownload {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return EngineDownload.Failed("The engine returned unreadable data.")
        if (json.optBoolean("ok", false)) {
            val path = json.optString("path").trim()
            val file = path.takeIf { it.isNotEmpty() }?.let(::File)
            return when {
                file == null -> EngineDownload.Failed("The download finished but reported no file.")
                !file.exists() -> EngineDownload.Failed("The download finished but the file is missing.")
                else -> EngineDownload.Ok(file)
            }
        }
        if (json.optBoolean("cancelled", false)) return EngineDownload.Cancelled
        return EngineDownload.Failed(
            json.optString("error").takeIf { it.isNotBlank() }
                ?: "The engine reported a failure without a message."
        )
    }

    private fun failureJson(message: String): String =
        """{"ok":false,"error":${JSONObject.quote(message)}}"""

    companion object {
        private const val MODULE = "fetchly_yt"
    }
}
