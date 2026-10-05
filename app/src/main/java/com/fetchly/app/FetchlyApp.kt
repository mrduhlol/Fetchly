package com.fetchly.app

import android.app.Application
import com.fetchly.app.data.download.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FetchlyApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Never let temp processing files accumulate; user media is untouched.
        scope.launch { AppGraph.cleanupTempFiles() }
    }
}
