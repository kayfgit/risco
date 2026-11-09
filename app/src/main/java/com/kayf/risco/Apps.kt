package com.kayf.risco

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlin.concurrent.thread

/** [icon] is only loaded while icons are on. */
class App(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val key: String,
    val icon: Bitmap? = null,
) {
    val folded = Fuzzy.fold(label)
    val letter = Fuzzy.letterIndex(label)
}

/** The installed apps. Reading labels is slow, so it happens off the main thread. */
class Apps(private val context: Context, private val onChange: (List<App>) -> Unit) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var latest = 0
    private var started = false
    private val reload = Runnable { load() }
    private val metrics = context.resources.displayMetrics

    /** The size icons are drawn at: a little taller than an 18sp name, growing with the font. */
    val iconSize = (22 * context.resources.configuration.fontScale * metrics.density).toInt()

    /** Loading icons costs time and memory, so it happens only while they're shown. */
    var icons = false
        set(value) {
            if (field == value) return
            field = value
            if (started) load()
        }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packages: Array<String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packages: Array<String>, user: UserHandle, replacing: Boolean) = changed()
    }

    fun start() {
        launcher.registerCallback(callback, main)
        started = true
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
        val withIcons = icons
        thread(name = "apps") {
            val me = Process.myUserHandle()
            val loaded = launcher.profiles.flatMap { user ->
                launcher.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != context.packageName }
                    .map {
                        val label = it.label.toString().trim() + if (user == me) "" else " [work]"
                        val key = "${it.componentName.flattenToShortString()}@${users.getSerialNumberForUser(user)}"
                        App(label, it.componentName, user, key, if (withIcons) icon(it) else null)
                    }
            }.sortedBy { it.folded }
            main.post { if (generation == latest) onChange(loaded) }
        }
    }

    /** In grey, so the icons sit with the white names instead of competing with them. */
    private fun icon(info: LauncherActivityInfo): Bitmap? = runCatching {
        val drawable = info.getIcon(metrics.densityDpi).mutate()
        drawable.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()
}
