package com.fetchly.app

import android.app.Application
import com.fetchly.app.data.download.AppGraph

class FetchlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
