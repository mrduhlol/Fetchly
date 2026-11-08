package com.fetchly.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.fetchly.app.domain.model.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Apply launcher changes only when no Fetchly activity is visible. */
internal class LauncherAppearance(private val app: FetchlyApp) : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())
    private var visibleActivities = 0
    private var choice = ThemeChoice.SYSTEM
    private val applyPending = Runnable {
        if (visibleActivities == 0) applyIcon()
    }

    fun register() {
        app.registerActivityLifecycleCallbacks(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            app.graph.settings.theme.collect { choice = it }
        }
    }

    private fun applyIcon() {
        val light = when (choice) {
            ThemeChoice.LIGHT -> true
            ThemeChoice.DARK -> false
            ThemeChoice.SYSTEM -> app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        }
        val manager = app.packageManager
        val selected = ComponentName(app, "${MainActivity::class.java.name}${if (light) "Light" else "Dark"}")
        val other = ComponentName(app, "${MainActivity::class.java.name}${if (light) "Dark" else "Light"}")
        // Enable first so there is always a launcher entry. Never kill the process or downloads.
        if (manager.getComponentEnabledSetting(selected) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            manager.setComponentEnabledSetting(selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        if (manager.getComponentEnabledSetting(other) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            manager.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        visibleActivities++
        handler.removeCallbacks(applyPending)
    }
    override fun onActivityStopped(activity: Activity) {
        visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        if (visibleActivities == 0 && !activity.isChangingConfigurations) handler.postDelayed(applyPending, 700)
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
