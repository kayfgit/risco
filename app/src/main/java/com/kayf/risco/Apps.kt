package com.kayf.risco

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Something home and search can list and open. [label] is what it's shown as, which is the name
 * someone gave it if they renamed it; [original] is its own name. [icon] is only loaded while
 * icons are on.
 */
sealed interface Item {
    val label: String
    val original: String
    val key: String
    val icon: Bitmap?
    val folded: String

    /** What search matches, folded: the label, and the original name too if it was renamed. */
    val names: List<String>
}

class App(
    override val label: String,
    val component: ComponentName,
    val user: UserHandle,
    override val key: String,
    /** Which rail letter it goes under, from the phone's [Alphabet]. */
    val letter: Int,
    override val icon: Bitmap? = null,
    override val original: String = label,
) : Item {
    override val folded = Fuzzy.fold(label)
    override val names = listOf(folded, Fuzzy.fold(original)).distinct()
}

/** A shortcut pinned to home, such as a website from Chrome's "Add to home screen". */
class Shortcut(
    override val label: String,
    val info: ShortcutInfo,
    override val key: String,
    override val icon: Bitmap? = null,
    override val original: String = label,
) : Item {
    override val folded = Fuzzy.fold(label)
    override val names = listOf(folded, Fuzzy.fold(original)).distinct()
}

/**
 * The installed apps, and the shortcuts pinned to home, each under the name [names] gives its key,
 * if any. Reading labels is slow, so it happens off the main thread.
 */
class Apps(
    private val context: Context,
    private val names: (key: String) -> String?,
    private val onChange: (List<App>, List<Shortcut>) -> Unit,
) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var latest = 0
    private var started = false
    private val reload = Runnable { load() }
    private val metrics = context.resources.displayMetrics

    /** Marks a work profile's apps, as " [work]" in English. */
    private val workSuffix = context.getString(R.string.work_suffix)

    /**
     * The rail's letters: the phone's languages unless `:alphabet` picked one. A language change
     * restarts home, so the phone's languages are read once.
     */
    var alphabet = phoneAlphabet(context.resources.configuration.locales)
        private set

    /** Which alphabet `:alphabet` picked. Changing it files every app again. */
    var choice = AlphabetChoice.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            alphabet = phoneAlphabet(context.resources.configuration.locales, value)
            if (started) load()
        }

    /** The size icons are drawn at: a little taller than an 18sp name, growing with the font. */
    val iconSize = (22 * context.resources.configuration.fontScale * metrics.density).toInt()

    /** Loading icons costs time and memory, so it happens only while they're shown. */
    var icons = false
        set(value) {
            if (field == value) return
            field = value
            if (started) load()
        }

    /** Each app's name and icon from the last start, so only new or updated apps are read again. */
    private val cacheFile = AtomicFile(File(context.filesDir, "apps.cache"))

    /** Packages Android says changed, read again on the next load even if their files didn't move. */
    private val stale = mutableSetOf<String>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packages: Array<String>, user: UserHandle, replacing: Boolean) =
            changed(*packages)
        override fun onPackagesUnavailable(packages: Array<String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) =
            changed()
    }

    fun start() {
        launcher.registerCallback(callback, main)
        started = true
        load()
    }

    fun stop() = launcher.unregisterCallback(callback)

    /** An update fires several callbacks in a row, which only need one reload. */
    fun changed(vararg packages: String) {
        stale += packages
        main.removeCallbacks(reload)
        main.postDelayed(reload, 300)
    }

    /** Reads everything again now, such as after a rename. */
    fun reload() {
        main.removeCallbacks(reload)
        load()
    }

    fun launch(app: App) = launcher.startMainActivity(app.component, app.user, null, null)

    fun details(app: App) = launcher.startAppDetailsActivity(app.component, app.user, null, null)

    fun launch(shortcut: ShortcutInfo) = launcher.startShortcut(shortcut, null, null)

    /**
     * What holding [app] offers, such as "New message". Android only shows shortcuts to the
     * default launcher, so there are none while risco is opened as an ordinary app.
     */
    fun shortcutsFor(app: App): List<ShortcutInfo> {
        val query = ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST)
        val all = shortcuts(query, app.user).filter { it.isEnabled }
        return menuShortcuts(all, { it.isDeclaredInManifest }, { it.rank })
    }

    /** Takes [shortcut] off home. Android keeps a launcher's pins, so this tells it which to keep. */
    fun unpin(shortcut: Shortcut) {
        val info = shortcut.info
        val query = ShortcutQuery().setPackage(info.`package`).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
        val keep = shortcuts(query, info.userHandle).map { it.id }.filter { it != info.id }
        runCatching { launcher.pinShortcuts(info.`package`, keep, info.userHandle) }
            .onFailure { Log.w(TAG, "can't unpin ${shortcut.key}", it) }
    }

    /**
     * Asking for shortcuts throws when risco isn't the default launcher, and while a work profile
     * is off, so either just means none.
     */
    private fun shortcuts(query: ShortcutQuery, user: UserHandle): List<ShortcutInfo> = try {
        if (launcher.hasShortcutHostPermission()) launcher.getShortcuts(query, user).orEmpty() else emptyList()
    } catch (e: RuntimeException) {
        Log.w(TAG, "can't read shortcuts", e)
        emptyList()
    }

    private fun shortcutLabel(info: ShortcutInfo): String =
        (info.shortLabel?.takeIf { it.isNotBlank() } ?: info.longLabel ?: "").toString().trim()

    private fun load() {
        val generation = ++latest
        val withIcons = icons
        val changedPackages = stale.toSet()
        stale.clear()
        // One load at a time, so two never write the cache at once.
        thread(name = "apps") { synchronized(cacheFile) { read(generation, withIcons, changedPackages) } }
    }

    private fun read(generation: Int, withIcons: Boolean, changedPackages: Set<String>) {
        val header = AppCache.header(context.resources.configuration.locales.toLanguageTags(), iconSize, iconDpi)
        val cache = try {
            cacheFile.openRead().use { AppCache.read(it, header) }
        } catch (e: IOException) {
            AppCache.read(null, header)
        }
        val saved = HashMap<String, AppCache.Entry>()
        var readAgain = 0
        val me = Process.myUserHandle()
        val loaded = launcher.profiles.flatMap { user ->
            launcher.getActivityList(null, user)
                .filter { it.applicationInfo.packageName != context.packageName }
                .map {
                    val work = if (user == me) "" else workSuffix
                    val key = "${it.componentName.flattenToShortString()}@${users.getSerialNumberForUser(user)}"
                    val source = it.applicationInfo.sourceDir.orEmpty()
                    val changed = it.applicationInfo.packageName in changedPackages
                    val cached = if (changed) null else cache.fresh(key, source, withIcons)
                    val entry = cached ?: run {
                        readAgain++
                        AppCache.Entry(source, it.label.toString().trim(), if (withIcons) png(icon(it)) else null)
                    }
                    saved[key] = entry
                    val original = entry.label
                    val label = displayLabel(original, names(key)) + work
                    val icon = if (withIcons) entry.icon?.let(::bitmap) else null
                    App(label, it.componentName, user, key, alphabet.letterOf(label), icon, original + work)
                }
        }.sortedBy { it.folded }
        if (readAgain > 0 || saved.keys != cache.entries.keys) save(AppCache(header, saved))
        val pinned = launcher.profiles.flatMap { user ->
            val serial = users.getSerialNumberForUser(user)
            shortcuts(ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED), user)
                .filter { it.isEnabled }
                .map { info ->
                    val work = if (user == me) "" else workSuffix
                    val original = shortcutLabel(info)
                    val key = shortcutKey(info.`package`, info.id, serial)
                    val icon = if (withIcons) shortcutIcon(info) else null
                    Shortcut(displayLabel(original, names(key)) + work, info, key, icon, original + work)
                }
        }
        main.post { if (generation == latest) onChange(loaded, pinned) }
    }

    /** Saving can fail, such as with the storage full; the next start then just reads every app. */
    private fun save(cache: AppCache) {
        val out = try {
            cacheFile.startWrite()
        } catch (e: IOException) {
            Log.w(TAG, "can't save the app cache", e)
            return
        }
        try {
            cache.write(out)
            cacheFile.finishWrite(out)
        } catch (e: IOException) {
            Log.w(TAG, "can't save the app cache", e)
            cacheFile.failWrite(out)
        }
    }

    private fun png(icon: Bitmap?): ByteArray? = icon?.let {
        ByteArrayOutputStream().also { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }.toByteArray()
    }

    private fun bitmap(png: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(png, 0, png.size)

    /** Small icons only need a low density; the screen's own decodes each one far bigger than it's drawn. */
    private val iconDpi = iconDensity(iconSize, metrics.densityDpi)

    /** In grey, so the icons sit with the white names instead of competing with them. */
    private fun icon(info: LauncherActivityInfo): Bitmap? = grey(info.getIcon(iconDpi))

    private fun shortcutIcon(info: ShortcutInfo): Bitmap? = grey(launcher.getShortcutIconDrawable(info, iconDpi))

    private fun grey(icon: Drawable?): Bitmap? = runCatching {
        val drawable = icon!!.mutate()
        drawable.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()
}

private const val TAG = "risco"

/** Android's standard densities, from mdpi to xxxhdpi. */
private val STANDARD_DPIS = listOf(160, 240, 320, 480, 640)

/**
 * The density to ask for an icon at, so it's drawn at [size] pixels without decoding more than
 * that: the smallest standard density whose 48dp launcher icon covers [size], and never more than
 * the screen's [screenDpi]. At the screen's own density, a 62px icon on a 450dpi phone was decoded
 * from layers about 430px across, around 1.5 MB each, for every app at once.
 */
fun iconDensity(size: Int, screenDpi: Int): Int {
    val enough = STANDARD_DPIS.firstOrNull { 48 * it / 160 >= size } ?: screenDpi
    return minOf(enough, screenDpi)
}
