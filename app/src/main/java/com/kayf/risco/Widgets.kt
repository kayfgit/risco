package com.kayf.risco

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.roundToInt

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

/** [ids] with [id] moved [by] places, up for negative, stopping at either end. */
fun <T> moved(ids: List<T>, id: T, by: Int): List<T> {
    val from = ids.indexOf(id)
    if (from < 0) return ids
    val to = (from + by).coerceIn(0, ids.lastIndex)
    if (to == from) return ids
    return ids.toMutableList().apply {
        removeAt(from)
        add(to, id)
    }
}

/**
 * A widget's height one [step] taller (or shorter, for a negative step), kept between the smallest
 * and largest it allows. When the largest is below the smallest, as some widgets declare, the
 * smallest wins.
 */
fun resized(height: Int, step: Int, min: Int, max: Int): Int = (height + step).coerceIn(min, maxOf(min, max))

/** The sizes a widget allows, in px, and which ways it can be resized at all. */
data class Limits(
    val minWidth: Int,
    val maxWidth: Int,
    val minHeight: Int,
    val maxHeight: Int,
    val horizontal: Boolean,
    val vertical: Boolean,
)

/** The handles on a widget being edited. */
enum class Corner(val left: Boolean, val top: Boolean) {
    TOP_LEFT(true, true),
    TOP_RIGHT(false, true),
    BOTTOM_LEFT(true, false),
    BOTTOM_RIGHT(false, false),
}

/**
 * A widget's size after dragging [corner] by ([dx], [dy]) from [width] by [height]. Dragging any
 * corner outwards grows it. Widgets stay centred, so the width changes by twice the sideways
 * drag, which keeps the corner under the finger. Each way is kept within [limits], and a widget
 * that can't resize one way keeps that size.
 */
fun cornerResize(corner: Corner, width: Int, height: Int, dx: Float, dy: Float, limits: Limits): Pair<Int, Int> {
    val w = if (limits.horizontal) {
        (width + 2 * (if (corner.left) -dx else dx)).roundToInt().coerceIn(
            limits.minWidth,
            maxOf(limits.minWidth, limits.maxWidth),
        )
    } else {
        width
    }
    val h = if (limits.vertical) {
        (height + (if (corner.top) -dy else dy)).roundToInt().coerceIn(
            limits.minHeight,
            maxOf(limits.minHeight, limits.maxHeight),
        )
    } else {
        height
    }
    return Pair(w, h)
}

/** The corner of the box ([left], [top], [right], [bottom]) within [reach] of ([x], [y]), if any. */
fun cornerAt(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float, reach: Float): Corner? =
    Corner.entries.firstOrNull { corner ->
        val cx = if (corner.left) left else right
        val cy = if (corner.top) top else bottom
        hypot(x - cx, y - cy) <= reach
    }

/**
 * Which way a dragged widget whose middle is at [center] should swap: -1 with the one above once
 * it's past that one's middle, 1 with the one below, 0 to stay. Null means there's none that side.
 */
fun swapDirection(center: Float, aboveCenter: Float?, belowCenter: Float?): Int = when {
    aboveCenter != null && center < aboveCenter -> -1
    belowCenter != null && center > belowCenter -> 1
    else -> 0
}

/** Hands out [WidgetView]s, so every widget on home can be held to edit it. */
class WidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?) =
        WidgetView(context)

    private companion object {
        const val HOST_ID = 1
    }
}

/**
 * A widget on home. Holding it anywhere starts editing it, even over the widget's own buttons,
 * which then hear the stroke cancelled instead of a click. The rest of that stroke goes to
 * [onDrag], so a widget can be held and dragged in one go.
 */
class WidgetView(context: Context) : AppWidgetHostView(context) {
    var onHold: () -> Unit = {}
    var onDrag: (MotionEvent) -> Unit = {}
    private val hold = Hold(this) { _, _ ->
        // Keeps home from taking the drag that may follow for a swipe.
        parent?.requestDisallowInterceptTouchEvent(true)
        onHold()
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = hold.track(event)

    // Only reached when no part of the widget took the press, or once the hold has fired. Taking
    // it keeps the stroke coming here; it edits rather than clicks, so no performClick.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wasHeld = hold.held
        hold.track(event)
        if (wasHeld) onDrag(event)
        return true
    }
}
