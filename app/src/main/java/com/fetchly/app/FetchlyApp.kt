package com.fetchly.app

import android.app.Application
import com.fetchly.app.data.AppGraph

/**
 * Fetchly v2.0 — on-device media fetcher.
 *
 * Everything (link resolution, download, merge) happens on this device via
 * the bundled yt-dlp engine and FFmpegKit. There is no backend, no account,
 * and no API key anywhere in this app.
 */
class FetchlyApp : Application() {

    /** One graph for the whole process; the download worker reads it too. */
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.init()
    }
}
