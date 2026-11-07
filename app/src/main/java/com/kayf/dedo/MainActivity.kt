package com.kayf.dedo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private class Hit(val text: String, val app: App?, val run: () -> Unit)

    private lateinit var store: Store
    private lateinit var apps: Apps
    private lateinit var root: HomeRoot
    private lateinit var home: LinearLayout
    private lateinit var pins: LinearLayout
    private lateinit var hint: TextView
    private lateinit var clock: View
    private lateinit var spaceTop: View
    private lateinit var spaceMid: View
    private lateinit var search: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var results: LinearLayout
    private lateinit var input: EditText
    private lateinit var rail: RailView
    private lateinit var anchor: View

    private var all: List<App> = emptyList()
    private var visible: List<App> = emptyList()
    private var hits: List<Hit> = emptyList()
    private var searching = false
    private var keyboardWasUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        pins = findViewById(R.id.pins)
        hint = findViewById(R.id.hint)
        clock = findViewById(R.id.clock)
        spaceTop = findViewById(R.id.space_top)
        spaceMid = findViewById(R.id.space_mid)
        search = findViewById(R.id.search)
        scroller = findViewById(R.id.scroller)
        results = findViewById(R.id.results)
        input = findViewById(R.id.input)
        rail = findViewById(R.id.rail)
        anchor = findViewById(R.id.anchor)

        store = Store(this)
        apps = Apps(this) { loaded ->
            all = loaded
            refresh()
        }

        root.onSwipeUp = ::openSearch
        root.onSwipeDown = ::expandShade
        rail.onOpen = ::open
        rail.side = store.railSide
        rail.onSide = { store.railSide = it }
        applyClock()

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val withKeyboard = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            home.setPadding(home.paddingLeft, bars.top, home.paddingRight, bars.bottom)
            search.setPadding(search.paddingLeft, bars.top, search.paddingRight, withKeyboard.bottom)
            rail.setInsets(bars.top, bars.bottom)

            // Back hides the keyboard before the app ever hears about it, so that is the signal.
            val keyboardUp = insets.isVisible(WindowInsets.Type.ime())
            if (keyboardWasUp && !keyboardUp) closeSearch()
            keyboardWasUp = keyboardUp
            WindowInsets.CONSUMED
        }

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

    override fun onStop() {
        super.onStop()
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
        closeSearch()
    }

    private fun refresh() {
        visible = all.filter { it.key !in store.hidden }
        rail.setApps(visible)
        renderPins()
        if (searching) renderHits()
    }

    private fun renderPins() {
        val byKey = visible.associateBy { it.key }
        pins.removeAllViews()
        for (key in store.pins) {
            val app = byKey[key] ?: continue
            val view = row(app.label, on = false)
            view.setOnClickListener { open(app) }
            view.setOnLongClickListener {
                showMenu(app, view.x, root.height / 2f)
                true
            }
            pins.addView(view)
        }
        hint.visibility = if (store.welcomed) View.GONE else View.VISIBLE
    }

    private fun open(app: App) {
        store.launched(app.key)
        if (!store.welcomed) {
            store.welcomed = true
            hint.visibility = View.GONE
        }
        // The app can vanish between the list being drawn and the finger lifting.
        try {
            apps.launch(app)
        } catch (e: RuntimeException) {
            Log.w(TAG, "can't open ${app.key}", e)
            Toast.makeText(this, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
            apps.changed()
        }
    }

    private fun openSearch() {
        searching = true
        root.swipes = false
        home.visibility = View.INVISIBLE
        rail.visibility = View.GONE
        search.visibility = View.VISIBLE
        input.setText("")
        input.requestFocus()
        // Unlike InputMethodManager, this still works when the field only just became visible.
        window.insetsController?.show(WindowInsets.Type.ime())
        renderHits()
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
    }

    private fun renderHits() {
        if (!searching) return
        hits = hitsFor(input.text.toString())
        results.removeAllViews()
        // Best match last, so it sits right above the keyboard where the thumb already is.
        hits.asReversed().forEachIndexed { i, hit ->
            val view = row(hit.text, on = i == hits.lastIndex)
            view.setOnClickListener { hit.run() }
            hit.app?.let { app ->
                view.setOnLongClickListener {
                    showMenu(app, root.width / 2f, root.height / 2f)
                    true
                }
            }
            results.addView(view)
        }
        scroller.post { scroller.scrollTo(0, results.height) }
    }

    private fun hitsFor(text: String): List<Hit> = when (val parsed = parseInput(text)) {
        is Input.Search -> ranked(visible, parsed.query).map { app ->
            Hit(app.label, app) {
                open(app)
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
            Arg.NONE -> listOf(Hit(":${parsed.command.word}  ${parsed.command.help}", null) { run(parsed.command, null) })
            Arg.CHOICE -> parsed.command.choicesFor(parsed.argument).map { choice ->
                Hit(":${parsed.command.word} $choice", null) { run(parsed.command, null, choice) }
            }
            else -> ranked(pool(parsed.command.arg), parsed.argument).map { app ->
                Hit(":${parsed.command.word} ${app.label}", app) { run(parsed.command, app) }
            }
        }
    }

    private fun pool(arg: Arg) = when (arg) {
        Arg.PINNED -> visible.filter { it.key in store.pins }
        Arg.HIDDEN -> all.filter { it.key in store.hidden }
        else -> visible
    }

    private fun ranked(pool: List<App>, query: String): List<App> {
        val folded = Fuzzy.fold(query)
        return pool.map { it to Fuzzy.score(folded, it.folded) }
            .filter { it.second >= 0 }
            .sortedWith(
                compareBy<Pair<App, Int>>(
                    { -Fuzzy.tier(it.second) },
                    { -store.launches(it.first.key) },
                    { -it.second },
                    { it.first.folded },
                )
            )
            .take(MAX_HITS)
            .map { it.first }
    }

    /** Places the clock block among home's spacers, or hides it. */
    private fun applyClock() {
        val position = store.clock
        clock.visibility = if (position == "off") View.GONE else View.VISIBLE
        spaceTop.visibility = if (position == "middle") View.VISIBLE else View.GONE
        home.removeView(clock)
        home.addView(clock, home.indexOfChild(if (position == "bottom") spaceMid else spaceTop) + 1)
    }

    private fun run(command: Command, app: App?, choice: String? = null) {
        when (command) {
            Command.PIN -> store.pin(app!!.key)
            Command.UNPIN -> store.unpin(app!!.key)
            Command.HIDE -> store.hide(app!!.key)
            Command.UNHIDE -> store.unhide(app!!.key)
            Command.INFO -> apps.details(app!!)
            Command.RM -> uninstall(app!!)
            Command.SET -> startActivity(Intent(Settings.ACTION_SETTINGS))
            Command.DEFAULT -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Command.CLOCK -> {
                store.clock = choice!!
                applyClock()
            }
            Command.Q -> {}
        }
        closeSearch()
        refresh()
    }

    private fun uninstall(app: App) {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
    }

    private fun showMenu(app: App, x: Float, y: Float) {
        anchor.x = x
        anchor.y = y
        val menu = PopupMenu(this, anchor)
        val pinned = app.key in store.pins
        val pinCommand = if (pinned) Command.UNPIN else Command.PIN
        menu.menu.add(pinCommand.word).setOnMenuItemClickListener { run(pinCommand, app); true }
        menu.menu.add(Command.HIDE.word).setOnMenuItemClickListener { run(Command.HIDE, app); true }
        menu.menu.add(Command.INFO.word).setOnMenuItemClickListener { run(Command.INFO, app); true }
        menu.menu.add(Command.RM.word).setOnMenuItemClickListener { run(Command.RM, app); true }
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

    private fun row(text: String, on: Boolean) = TextView(this).apply {
        this.text = text
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "dedo"
        const val MAX_HITS = 20
    }
}
