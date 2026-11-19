package com.kayf.risco

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.Log
import android.util.SizeF
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private class Hit(val text: String, val item: Item?, val icon: Bitmap? = null, val run: () -> Unit)

    private lateinit var store: Store
    private lateinit var apps: Apps
    private lateinit var root: HomeRoot
    private lateinit var home: LinearLayout
    private lateinit var pins: LinearLayout
    private lateinit var hint: TextView
    private lateinit var widgets: LinearLayout
    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var search: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var results: LinearLayout
    private lateinit var input: EditText
    private lateinit var rail: RailView
    private lateinit var anchor: View
    private lateinit var editor: WidgetEditor

    private var all: List<App> = emptyList()
    private var visible: List<App> = emptyList()
    private var shortcuts: List<Shortcut> = emptyList()
    private var hits: List<Hit> = emptyList()
    private var searching = false
    private var keyboardWasUp = false
    private var barsTop = 0
    private var barsBottom = 0

    /** What home does on a swipe or a hold, for TalkBack and Switch Access, which can do neither there. */
    private val homeActions = listOf(
        HomeAction(R.id.action_search, "Search apps", ::openSearch),
        HomeAction(R.id.action_notifications, "Notifications", ::expandShade),
        HomeAction(R.id.action_menu, "Wallpaper and widgets") { showHomeMenu(root.width / 2f, root.height / 2f) },
    )

    /** A widget id between asking to add it and it landing on home. */
    private var pendingWidget = AppWidgetManager.INVALID_APPWIDGET_ID

    /** What `:widget` offers, read once per search since labels are slow to load. */
    private var widgetChoices: List<WidgetChoice>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        pins = findViewById(R.id.pins)
        hint = findViewById(R.id.hint)
        widgets = findViewById(R.id.widgets)
        search = findViewById(R.id.search)
        scroller = findViewById(R.id.scroller)
        results = findViewById(R.id.results)
        input = findViewById(R.id.input)
        rail = findViewById(R.id.rail)
        anchor = findViewById(R.id.anchor)
        editor = findViewById(R.id.editor)

        store = Store(this)
        apps = Apps(this, store::name) { loaded, pinned ->
            all = loaded
            shortcuts = pinned
            store.pinNew(pinned.map { it.key })
            refresh()
        }
        apps.icons = store.icons
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = WidgetHost(this)

        root.onSwipeUp = ::openSearch
        root.onSwipeDown = ::expandShade
        root.onHold = ::showHomeMenu
        rail.onOpen = ::open
        rail.onLetter = ::showLetter
        rail.actions = homeActions
        wireEditor()
        rail.side = store.railSide
        rail.onSide = { store.railSide = it }
        renderWidgets()

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val withKeyboard = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            val keyboardUp = insets.isVisible(WindowInsets.Type.ime())
            if (keyboardUp) store.keyboardHeight = withKeyboard.bottom
            barsTop = bars.top
            barsBottom = bars.bottom
            home.setPadding(home.paddingLeft, bars.top, home.paddingRight, bars.bottom)
            placeSearch(searchBottom(keyboardUp, withKeyboard.bottom, bars.bottom, store.keyboardHeight))
            rail.setInsets(bars.top, bars.bottom)

            // Back hides the keyboard before the app ever hears about it, so that is the signal.
            if (keyboardWasUp && !keyboardUp) closeSearch()
            keyboardWasUp = keyboardUp
            WindowInsets.CONSUMED
        }
        // With a callback set, the listener above only hears where the keyboard ends up, never the
        // frames in between, so the results are placed once and stay put while it slides in.
        root.setWindowInsetsAnimationCallback(
            object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(insets: WindowInsets, running: List<WindowInsetsAnimation>) = insets
            },
        )

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) = renderHits()
        })
        input.setOnEditorActionListener { _, _, event ->
            // A hardware Enter arrives here twice, once for the press and once for the release.
            if (event == null || event.action == KeyEvent.ACTION_DOWN) hits.firstOrNull()?.run?.invoke()
            true
        }

        apps.start()
    }

    override fun onDestroy() {
        apps.stop()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
        closeOverlays()
    }

    /** Pressing home while already home lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeOverlays()
    }

    @Deprecated("A launcher never finishes, and this API still covers every supported version.")
    override fun onBackPressed() {
        closeOverlays()
    }

    private fun closeOverlays() {
        rail.dismiss()
        editor.finish()
        closeSearch()
    }

    private fun refresh() {
        visible = all.filter { it.key !in store.hidden }
        rail.setApps(visible, apps.alphabet.letters)
        renderPins()
        if (searching) renderHits()
    }

    /** What search finds: the apps, and the shortcuts pinned to home. */
    private val searchable get() = visible + shortcuts

    private fun renderPins() {
        val byKey = searchable.associateBy { it.key }
        pins.removeAllViews()
        for (key in store.pins) {
            val item = byKey[key] ?: continue
            val view = row(item.label, on = false, icon = item.icon)
            view.setOnClickListener { open(item) }
            view.setOnLongClickListener {
                showMenu(item, view.x, root.height / 2f)
                true
            }
            view.offer(homeActions)
            pins.addView(view)
        }
        hint.visibility = if (store.welcomed) View.GONE else View.VISIBLE
    }

    private fun open(item: Item) {
        store.launched(item.key)
        if (!store.welcomed) {
            store.welcomed = true
            hint.visibility = View.GONE
        }
        // The app can vanish between the list being drawn and the finger lifting, and a shortcut
        // can stop working while its work profile is off.
        try {
            when (item) {
                is App -> apps.launch(item)
                is Shortcut -> apps.launch(item.info)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "can't open ${item.key}", e)
            Toast.makeText(this, "Can't open ${item.label}", Toast.LENGTH_SHORT).show()
            apps.changed()
        }
    }

    /** One of an app's own shortcuts, from its hold menu. */
    private fun open(shortcut: ShortcutInfo) {
        try {
            apps.launch(shortcut)
        } catch (e: RuntimeException) {
            Log.w(TAG, "can't open shortcut ${shortcut.id}", e)
            Toast.makeText(this, "Can't open ${shortcut.shortLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSearch() = openSearch("")

    private fun openSearch(text: String) {
        searching = true
        root.swipes = false
        home.visibility = View.INVISIBLE
        rail.visibility = View.GONE
        placeSearch(searchBottom(false, barsBottom, barsBottom, store.keyboardHeight))
        search.visibility = View.VISIBLE
        input.setText(text)
        input.setSelection(input.length())
        input.requestFocus()
        // Unlike InputMethodManager, this still works when the field only just became visible.
        window.insetsController?.show(WindowInsets.Type.ime())
        renderHits()
    }

    private fun placeSearch(bottom: Int) {
        if (search.paddingBottom == bottom && search.paddingTop == barsTop) return
        search.setPadding(search.paddingLeft, barsTop, search.paddingRight, bottom)
        if (searching) scroller.post { scroller.scrollTo(0, results.height) }
    }

    private fun closeSearch() {
        if (!searching) return
        searching = false
        root.swipes = true
        search.visibility = View.GONE
        home.visibility = View.VISIBLE
        rail.visibility = View.VISIBLE
        input.clearFocus()
        window.insetsController?.hide(WindowInsets.Type.ime())
        widgetChoices = null
    }

    private fun renderHits() {
        if (!searching) return
        hits = hitsFor(input.text.toString())
        results.removeAllViews()
        // Best match last, so it sits right above the keyboard where the thumb already is.
        hits.asReversed().forEachIndexed { i, hit ->
            val view = row(hit.text, on = i == hits.lastIndex, icon = hit.icon)
            view.setOnClickListener { hit.run() }
            hit.item?.let { item ->
                view.setOnLongClickListener {
                    showMenu(item, root.width / 2f, root.height / 2f)
                    true
                }
            }
            results.addView(view)
        }
        scroller.post { scroller.scrollTo(0, results.height) }
    }

    // Commands are typed, not translated, so building ":pin " in code is fine.
    @SuppressLint("SetTextI18n")
    private fun hitsFor(text: String): List<Hit> = when (val parsed = parseInput(text)) {
        is Input.Search -> ranked(searchable, parsed.query).map { item ->
            Hit(item.label, item, item.icon) {
                open(item)
                closeSearch()
            }
        }
        is Input.Partial -> parsed.matches.map { command ->
            Hit(":${command.word}  ${command.help}", null) {
                if (command.arg == Arg.NONE) {
                    run(command, null)
                } else {
                    input.setText(":${command.word} ")
                    input.setSelection(input.length())
                }
            }
        }
        is Input.Full -> when (parsed.command.arg) {
            Arg.NONE -> listOf(
                Hit(":${parsed.command.word}  ${parsed.command.help}", null) {
                    run(parsed.command, null)
                },
            )
            Arg.CHOICE -> parsed.command.choicesFor(parsed.argument).map { choice ->
                Hit(":${parsed.command.word} $choice", null) { run(parsed.command, null, choice) }
            }
            Arg.WIDGET -> Fuzzy.rank(choices(), parsed.argument, { listOf(it.folded) }, { 0 }, MAX_HITS).map { choice ->
                Hit(":${parsed.command.word} ${choice.label}", null) {
                    closeSearch()
                    addWidget(choice.info)
                }
            }
            else -> ranked(pool(parsed.command.arg), parsed.argument).map { item ->
                Hit(":${parsed.command.word} ${item.label}", item) { run(parsed.command, item) }
            }
        }
    }

    private fun pool(arg: Arg): List<Item> = when (arg) {
        Arg.ITEM -> searchable
        Arg.PINNED -> searchable.filter { it.key in store.pins }
        Arg.HIDDEN -> all.filter { it.key in store.hidden }
        else -> visible
    }

    private fun <T : Item> ranked(pool: List<T>, query: String) =
        Fuzzy.rank(pool, query, { it.names }, { store.launches(it.key) }, MAX_HITS)

    /**
     * Every profile's widgets, work ones marked like work apps. A widget registered twice under one
     * name is listed once: Samsung ships most of its widgets that way, an old and an "integrated" one.
     */
    private fun choices(): List<WidgetChoice> = widgetChoices ?: run {
        val me = Process.myUserHandle()
        getSystemService(LauncherApps::class.java).profiles.flatMap { user ->
            widgetManager.getInstalledProvidersForProfile(user).map { info ->
                val app = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0))
                }.getOrDefault("")
                val label = widgetLabel(app.toString(), info.loadLabel(packageManager))
                WidgetChoice(info, label + if (user == me) "" else " [work]")
            }
        }.distinctBy { it.info.provider.packageName to it.label }
            .sortedBy { it.folded }
            .also { widgetChoices = it }
    }

    /** [item] is set for commands that take an app; `:unpin` and `:rename` also take a shortcut. */
    private fun run(command: Command, item: Item?, choice: String? = null) {
        val app = item as? App
        when (command) {
            Command.PIN -> store.pin(app!!.key)
            Command.UNPIN -> unpin(item!!)
            // After search closes, so the dialog's keyboard isn't hidden along with search's.
            Command.RENAME -> root.post { rename(item!!) }
            Command.HIDE -> store.hide(app!!.key)
            Command.UNHIDE -> store.unhide(app!!.key)
            Command.INFO -> apps.details(app!!)
            Command.RM -> uninstall(app!!)
            Command.SET -> startActivity(Intent(Settings.ACTION_SETTINGS))
            Command.DEFAULT -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Command.ICONS -> setIcons(choice == "on")
            Command.MENU -> root.post { showHomeMenu(root.width / 2f, root.height / 2f) }
            Command.WALLPAPER -> openWallpaper()
            Command.WIDGET -> {}
            Command.Q -> {}
        }
        closeSearch()
        refresh()
    }

    /**
     * Asks for a new name, starting from the current one. Saving it blank, or as the item's own
     * name, goes back to its own name, and so does Reset.
     */
    private fun rename(item: Item) {
        val custom = store.name(item.key)
        val own = item.original.removeSuffix(" [work]")
        val field = EditText(this).apply {
            setText(custom ?: own)
            hint = own
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            selectAll()
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
        }
        fun save(name: String?) {
            store.rename(item.key, name?.trim()?.takeIf { it.isNotEmpty() && it != own })
            apps.reload()
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(box)
            .setPositiveButton("Save") { _, _ -> save(field.text.toString()) }
            .setNegativeButton("Cancel", null)
        if (custom != null) builder.setNeutralButton("Reset") { _, _ -> save(null) }
        val dialog = builder.create()
        field.setOnEditorActionListener { _, _, _ ->
            save(field.text.toString())
            dialog.dismiss()
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    private fun unpin(item: Item) {
        if (item is Shortcut) apps.unpin(item)
        store.unpin(item.key)
    }

    private fun uninstall(app: App) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
    }

    private fun setIcons(on: Boolean) {
        store.icons = on
        apps.icons = on
    }

    private fun openWallpaper() {
        try {
            startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no wallpaper picker", e)
            Toast.makeText(this, "No wallpaper app found", Toast.LENGTH_SHORT).show()
        }
    }

    /** What holding empty space on home opens, like a stock launcher's, and `:menu`. */
    private fun showHomeMenu(x: Float, y: Float) {
        anchor.x = x
        anchor.y = y
        val menu = PopupMenu(this, anchor)
        menu.menu.add("Wallpaper").setOnMenuItemClickListener {
            openWallpaper()
            true
        }
        menu.menu.add("Widgets").setOnMenuItemClickListener {
            openSearch(":${Command.WIDGET.word} ")
            true
        }
        menu.menu.add(if (store.icons) "Hide icons" else "Show icons").setOnMenuItemClickListener {
            setIcons(!store.icons)
            true
        }
        menu.menu.add("Commands").setOnMenuItemClickListener {
            openSearch(":")
            true
        }
        menu.show()
    }

    /** Binds a widget, which can take asking permission and then the widget's own setup screen. */
    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureWidget(id)
            return
        }
        val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        try {
            startActivityForResult(ask, REQUEST_BIND)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "can't ask to bind a widget", e)
            dropPendingWidget()
        }
    }

    private fun configureWidget(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        // A widget can say its setup screen is only for later. Android 11 has no way to say it.
        val optional = info != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info?.configure == null || optional) {
            placeWidget(id)
            return
        }
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
        } catch (e: RuntimeException) {
            // Some setup screens aren't exported or are gone; the widget usually works without.
            Log.w(TAG, "can't set up widget $id", e)
            placeWidget(id)
        }
    }

    @Deprecated("Activity's own result API is the one without AndroidX, and covers every supported version.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (pendingWidget == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when {
            resultCode != RESULT_OK -> dropPendingWidget()
            requestCode == REQUEST_BIND -> configureWidget(pendingWidget)
            requestCode == REQUEST_CONFIGURE -> placeWidget(pendingWidget)
        }
    }

    private fun dropPendingWidget() {
        widgetHost.deleteAppWidgetId(pendingWidget)
        pendingWidget = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun placeWidget(id: Int) {
        pendingWidget = AppWidgetManager.INVALID_APPWIDGET_ID
        store.addWidget(id)
        renderWidgets()
    }

    private fun removeWidget(id: Int) {
        widgetHost.deleteAppWidgetId(id)
        store.removeWidget(id)
        renderWidgets()
    }

    /** Draws home's widgets, dropping any whose app has been uninstalled. */
    private fun renderWidgets() {
        editor.finish()
        val gone = store.widgets.filter { widgetManager.getAppWidgetInfo(it) == null }
        for (id in gone) {
            widgetHost.deleteAppWidgetId(id)
            store.removeWidget(id)
        }
        widgets.removeAllViews()
        for (id in store.widgets) {
            val info = widgetManager.getAppWidgetInfo(id) ?: continue
            val view = widgetHost.createView(this, id, info) as WidgetView
            val height = store.widgetHeight(id).takeIf { it > 0 }?.let(::dp) ?: ownHeight(info)
            val custom = store.widgetWidth(id).takeIf { it > 0 }?.let(::dp)?.coerceAtMost(columnWidth)
            sizeWidget(view, custom ?: columnWidth, height)
            view.onHold = { editWidget(view, info) }
            view.onDrag = editor::drag
            describeWidget(view, info, custom ?: columnWidth, height)
            val params = LinearLayout.LayoutParams(custom ?: LinearLayout.LayoutParams.MATCH_PARENT, height)
            params.gravity = Gravity.CENTER_HORIZONTAL
            widgets.addView(view, params)
        }
    }

    /** The width widgets get unless resized: home's column, between the rails. */
    private val columnWidth get() = resources.displayMetrics.widthPixels - home.paddingLeft - home.paddingRight

    /** The widgets on home, top to bottom, as views. */
    private fun widgetViews() = (0 until widgets.childCount).map { widgets.getChildAt(it) }

    /** Holding a widget: an outline with corner handles, to drag it around and resize it. */
    private fun editWidget(view: WidgetView, info: AppWidgetProviderInfo) {
        root.swipes = false
        editor.start(view, limits(info), ::widgetViews)
    }

    private fun wireEditor() {
        editor.onResize = { view, width, height ->
            view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = width
                this.height = height
            }
        }
        editor.onResized = { view ->
            val params = view.layoutParams
            val width = if (params.width > 0) params.width else view.width
            val height = if (params.height > 0) params.height else view.height
            store.setWidgetHeight(view.appWidgetId, px2dp(height))
            // The full column is kept as "full", so it follows the screen if that changes.
            store.setWidgetWidth(view.appWidgetId, if (width >= columnWidth) 0 else px2dp(width))
            sizeWidget(view, width, height)
        }
        editor.onSwap = { view, by ->
            // Moves the neighbour rather than the dragged widget, so the stroke stays with it.
            val at = widgets.indexOfChild(view)
            val other = widgets.getChildAt(at + by)
            widgets.removeView(other)
            widgets.addView(other, at)
        }
        editor.onMoved = { store.orderWidgets(widgetViews().map { (it as WidgetView).appWidgetId }) }
        editor.onRemove = { view -> removeWidget(view.appWidgetId) }
        editor.onFinish = { root.swipes = !searching }
    }

    /** The sizes [info]'s widget allows, in px, never wider than home's column. */
    private fun limits(info: AppWidgetProviderInfo): Limits {
        val s = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val ownMaxWidth = if (s) info.maxResizeWidth else 0
        return Limits(
            minWidth = listOf(info.minResizeWidth, info.minWidth).filter { it > 0 }.minOrNull() ?: dp(WIDGET_ROW),
            maxWidth = if (ownMaxWidth > 0) minOf(ownMaxWidth, columnWidth) else columnWidth,
            minHeight = smallest(info),
            maxHeight = largest(info),
            horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
    }

    /**
     * TalkBack and Switch Access can't drag, so moving, resizing and removing a widget are in
     * their actions menu on it, a step at a time, each only when it would change something.
     */
    private fun describeWidget(view: WidgetView, info: AppWidgetProviderInfo, width: Int, height: Int) {
        val id = view.appWidgetId
        val limits = limits(info)
        val step = dp(WIDGET_ROW / 2)
        val actions = mutableListOf<HomeAction>()
        fun add(action: Int, label: String, run: () -> Unit) = actions.add(
            HomeAction(action, label) {
                run()
                renderWidgets()
            },
        )
        val at = store.widgets.indexOf(id)
        if (at > 0) add(R.id.action_widget_up, "Move up") { store.moveWidget(id, -1) }
        if (at < store.widgets.lastIndex) add(R.id.action_widget_down, "Move down") { store.moveWidget(id, 1) }
        if (limits.vertical) {
            val taller = resized(height, step, limits.minHeight, limits.maxHeight)
            val shorter = resized(height, -step, limits.minHeight, limits.maxHeight)
            if (taller > height) add(R.id.action_widget_taller, "Taller") { store.setWidgetHeight(id, px2dp(taller)) }
            if (shorter <
                height
            ) {
                add(R.id.action_widget_shorter, "Shorter") { store.setWidgetHeight(id, px2dp(shorter)) }
            }
        }
        if (limits.horizontal) {
            val wider = resized(width, step, limits.minWidth, limits.maxWidth)
            val narrower = resized(width, -step, limits.minWidth, limits.maxWidth)
            fun keep(w: Int) = store.setWidgetWidth(id, if (w >= columnWidth) 0 else px2dp(w))
            if (wider > width) add(R.id.action_widget_wider, "Wider") { keep(wider) }
            if (narrower < width) add(R.id.action_widget_narrower, "Narrower") { keep(narrower) }
        }
        actions.add(HomeAction(R.id.action_widget_remove, "Remove widget") { removeWidget(id) })
        view.offer(actions)
        view.isScreenReaderFocusable = true
        view.contentDescription = "${info.loadLabel(packageManager)} widget"
    }

    /** The height a widget asks for, in px, before anyone resizes it. */
    private fun ownHeight(info: AppWidgetProviderInfo): Int {
        val targetRows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0
        return widgetHeight(info.minHeight, info.minResizeHeight, targetRows, dp(WIDGET_ROW), dp(WIDGET_ROW * 2))
    }

    /** The smallest a widget says it can be, in px, or a row if it says nothing. */
    private fun smallest(info: AppWidgetProviderInfo): Int =
        listOf(info.minResizeHeight, info.minHeight).filter { it > 0 }.minOrNull() ?: dp(WIDGET_ROW)

    /** The largest a widget says it can be, in px, and never more than most of the screen. */
    private fun largest(info: AppWidgetProviderInfo): Int {
        val screen = (resources.displayMetrics.heightPixels * MAX_WIDGET_SHARE).toInt()
        val own = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.maxResizeHeight else 0
        return if (own > 0) minOf(own, screen) else screen
    }

    private fun px2dp(px: Int) = (px / resources.displayMetrics.density).roundToInt()

    /** Tells the widget the space it has, in dp, so it can lay itself out for it. */
    private fun sizeWidget(view: WidgetView, width: Int, height: Int) {
        val density = resources.displayMetrics.density
        val w = width / density
        val h = height / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
        }
    }

    /** A rail letter's apps as a menu, for TalkBack and Switch Access, which can't make the stroke. */
    private fun showLetter(letterApps: List<App>, y: Float) {
        anchor.x = if (rail.side == Side.LEFT) 0f else root.width.toFloat()
        anchor.y = y
        val menu = PopupMenu(this, anchor)
        for (app in letterApps) {
            menu.menu.add(app.label).setOnMenuItemClickListener {
                open(app)
                true
            }
        }
        menu.show()
    }

    /**
     * What holding an app or a pinned shortcut opens. An app's own shortcuts come first, above a
     * line where the phone draws one (Samsung's menus don't). Labels are plain words, not the
     * commands they run, which are for typing.
     */
    private fun showMenu(item: Item, x: Float, y: Float) {
        anchor.x = x
        anchor.y = y
        val menu = PopupMenu(this, anchor)
        menu.menu.setGroupDividerEnabled(true)
        fun add(command: Command, label: String) = menu.menu.add(ACTIONS, Menu.NONE, Menu.NONE, label)
            .setOnMenuItemClickListener {
                run(command, item)
                true
            }
        when (item) {
            is Shortcut -> {
                add(Command.RENAME, "Rename")
                add(Command.UNPIN, "Remove from home")
            }
            is App -> {
                for (shortcut in apps.shortcutsFor(item)) {
                    val label = shortcut.longLabel?.takeIf { it.isNotBlank() } ?: shortcut.shortLabel
                    menu.menu.add(SHORTCUTS, Menu.NONE, Menu.NONE, label).setOnMenuItemClickListener {
                        closeSearch()
                        open(shortcut)
                        true
                    }
                }
                if (item.key in store.pins) add(Command.UNPIN, "Remove from home") else add(Command.PIN, "Add to home")
                add(Command.RENAME, "Rename")
                add(Command.HIDE, "Hide")
                add(Command.INFO, "App info")
                add(Command.RM, "Uninstall")
            }
        }
        menu.show()
    }

    /** There is no public API for this, so failing quietly is the most it can do. */
    @SuppressLint("WrongConstant")
    private fun expandShade() {
        runCatching {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(getSystemService("statusbar"))
        }.onFailure { Log.w(TAG, "can't open notifications", it) }
    }

    private fun row(text: String, on: Boolean, icon: Bitmap? = null) = TextView(this).apply {
        this.text = if (icon == null) text else withIcon(icon, text)
        typeface = Typeface.MONOSPACE
        textSize = 18f
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        minHeight = dp(52)
        // Centred, so neither thumb has further to reach.
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        if (on) {
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        } else {
            setTextColor(Color.WHITE)
            setShadowLayer(dp(3).toFloat(), 0f, 0f, Color.BLACK)
        }
    }

    /** The icon as the first character, so it stays beside the centred name instead of at the edge. */
    private fun withIcon(icon: Bitmap, text: String) = SpannableString("  $text").apply {
        val drawable = BitmapDrawable(resources, icon).apply { setBounds(0, 0, icon.width, icon.height) }
        setSpan(ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "risco"
        const val MAX_HITS = 20
        const val REQUEST_BIND = 1
        const val REQUEST_CONFIGURE = 2

        /** Menu groups, so a line separates an app's shortcuts from what risco can do with it. */
        const val SHORTCUTS = 1
        const val ACTIONS = 2

        /** dp per row a widget asks for, about what a stock launcher gives one. Resizing goes half a row at a time. */
        const val WIDGET_ROW = 80

        /** The most of the screen's height one widget can grow to, so home's pins stay in reach. */
        const val MAX_WIDGET_SHARE = 0.6f
    }
}
