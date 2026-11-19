package com.kayf.risco

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent

/** A widget someone could add, as `:widget` lists it. */
class WidgetChoice(val info: AppWidgetProviderInfo, val label: String) {
    val folded = Fuzzy.fold(label)
}

/** The app's name, then the widget's, unless the widget only repeats it. */
fun widgetLabel(app: String, widget: String): String {
    val a = app.trim()
    val w = widget.trim()
    return when {
        w.isEmpty() || Fuzzy.fold(w) == Fuzzy.fold(a) -> a
        a.isEmpty() || Fuzzy.fold(w).startsWith(Fuzzy.fold(a)) -> w
        else -> "$a · $w"
    }
}

/**
 * How tall a widget sits on home, in px. Some widgets leave their minimum at 0 (Samsung's clocks
 * do, sizing themselves for Samsung's launcher), so this falls back to the smallest they can be
 * resized to, then the rows they ask for, then [fallback].
 */
fun widgetHeight(minHeight: Int, minResizeHeight: Int, targetRows: Int, row: Int, fallback: Int): Int = when {
    minHeight > 0 -> minHeight
    targetRows > 0 -> maxOf(targetRows * row, minResizeHeight)
    minResizeHeight > 0 -> maxOf(minResizeHeight, fallback)
    else -> fallback
}

/** Hands out [WidgetView]s, so every widget on home can be held for its menu. */
class WidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?) =
        WidgetView(context)

    private companion object {
        const val HOST_ID = 1
    }
}

/**
 * A widget on home. Holding it anywhere opens its menu, even over the widget's own buttons, which
 * then hear the stroke cancelled instead of a click.
 */
class WidgetView(context: Context) : AppWidgetHostView(context) {
    var onHold: () -> Unit = {}
    private val hold = Hold(this) { _, _ -> onHold() }

    override fun onInterceptTouchEvent(event: MotionEvent) = hold.track(event)

    // Only reached when no part of the widget took the press. Taking it keeps the stroke coming
    // here, so the hold can finish; it opens a menu rather than clicking, so no performClick.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        hold.track(event)
        return true
    }
}
