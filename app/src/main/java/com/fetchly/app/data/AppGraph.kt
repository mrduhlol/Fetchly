package com.fetchly.app.data

import android.content.Context
import androidx.room.Room
import com.fetchly.app.data.db.FetchlyDatabase
import com.fetchly.app.data.db.HistoryRepository
import com.fetchly.app.data.direct.DirectMediaResolver
import com.fetchly.app.data.download.DownloadNotifications
import com.fetchly.app.data.download.DownloadQueue
import com.fetchly.app.data.download.TempFiles
import com.fetchly.app.data.engine.YtDlpEngine
import com.fetchly.app.data.prefs.SettingsStore
import com.fetchly.app.data.repository.MediaRepository
import com.fetchly.app.data.repository.OnDeviceMediaRepository
import com.fetchly.app.domain.resolve.ResolveUrlUseCase
import com.fetchly.app.domain.session.IncomingLinks
import com.fetchly.app.domain.session.ResolveSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * The one wiring point of the app: every singleton the UI, the queue and the
 * download worker share is built here. Held by [com.fetchly.app.FetchlyApp]
 * for the whole process.
 *
 * No timeouts are configured for whole responses (only per-read), because a
 * download is allowed to take as long as it takes — `callTimeout` must stay
 * at its default of "no limit".
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    /** Lifetime of the process; only fire-and-forget housekeeping runs here. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val engine = YtDlpEngine(appContext)
    val probe = DirectMediaResolver(http)
    val repository: MediaRepository = OnDeviceMediaRepository(probe, engine)

    /** Fresh installs only — history never migrates from the old app. */
    private val database: FetchlyDatabase = Room.databaseBuilder(
        appContext,
        FetchlyDatabase::class.java,
        "fetchly.db",
    ).build()

    val history = HistoryRepository(database.historyDao())

    val queue by lazy { DownloadQueue(appContext, history) }

    val settings = SettingsStore(appContext)
    val session = ResolveSession()
    val incoming = IncomingLinks()
    val resolver = ResolveUrlUseCase(repository)

    /** Application-start housekeeping: notification channel + scratch sweep. */
    fun init() {
        DownloadNotifications.ensureChannel(appContext)
        val scratch = TempFiles.dir(appContext)
        scope.launch { TempFiles.cleanupStale(scratch) }
    }
}
