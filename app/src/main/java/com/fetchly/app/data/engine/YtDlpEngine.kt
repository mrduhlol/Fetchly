package com.fetchly.app.data.engine

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge to the embedded yt-dlp engine. Python starts once, lazily, off the
 * main thread — extraction itself runs on Dispatchers.IO.
 */
object YtDlpEngine {

    @Volatile private var started = false
    private val lock = Any()

    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(lock) {
            if (started) return
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            started = true
        }
    }

    /** Returns the extractor's raw JSON string (ok flag inside). */
    suspend fun extractJson(context: Context, url: String): String =
        withContext(Dispatchers.IO) {
            ensureStarted(context)
            val module = Python.getInstance().getModule("fetchly_yt")
            module.callAttr("extract", url).toString()
        }
}
