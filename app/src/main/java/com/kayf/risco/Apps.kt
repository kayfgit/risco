package com.kayf.risco

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlin.concurrent.thread

class App(val label: String, val component: ComponentName, val user: UserHandle, val key: String) {
    val folded = Fuzzy.fold(label)
    val letter = Fuzzy.letterIndex(label)
}

/** The installed apps. Reading labels is slow, so it happens off the main thread. */
class Apps(private val context: Context, private val onChange: (List<App>) -> Unit) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var latest = 0
    private val reload = Runnable { load() }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packages: Array<String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packages: Array<String>, user: UserHandle, replacing: Boolean) = changed()
    }

    fun start() {
        launcher.registerCallback(callback, main)
        load()
    }

    fun stop() = launcher.unregisterCallback(callback)

    /** An update fires several callbacks in a row, which only need one reload. */
    fun changed() {
        main.removeCallbacks(reload)
        main.postDelayed(reload, 300)
    }

    fun launch(app: App) = launcher.startMainActivity(app.component, app.user, null, null)

    fun details(app: App) = launcher.startAppDetailsActivity(app.component, app.user, null, null)

    private fun load() {
        val generation = ++latest
        thread(name = "apps") {
            val me = Process.myUserHandle()
            val loaded = launcher.profiles.flatMap { user ->
                launcher.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != context.packageName }
                    .map {
                        val label = it.label.toString().trim() + if (user == me) "" else " [work]"
                        val key = "${it.componentName.flattenToShortString()}@${users.getSerialNumberForUser(user)}"
                        App(label, it.componentName, user, key)
                    }
            }.sortedBy { it.folded }
            main.post { if (generation == latest) onChange(loaded) }
        }
    }
}
