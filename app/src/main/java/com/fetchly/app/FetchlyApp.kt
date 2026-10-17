package com.fetchly.app

import android.app.Application

/**
 * Fetchly v2.0 — on-device media fetcher.
 *
 * Everything (link resolution, download, merge) happens on this device via
 * the bundled yt-dlp engine and FFmpegKit. There is no backend, no account,
 * and no API key anywhere in this app.
 */
class FetchlyApp : Application()
