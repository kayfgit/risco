package com.kayf.risco

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Button
import kotlin.math.floor
import kotlin.math.max

/**
 * The alphabet rail and the panel of apps beside it, drawn straight to the canvas so a drag only
 * ever repaints this one view. All the decisions are in [Drag]; this turns touches into calls on
 * it and draws what it says.
 *
 * Both edges take touches, one per thumb, but only the rail last used is drawn.
 *
 * TalkBack can't follow a stroke, so to it each letter of the drawn rail is a button, found by
 * touch or by swiping, that lists its apps through [onLetter].
 */
class RailView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var onOpen: (App) -> Unit = {}
    var onSide: (Side) -> Unit = {}

    /** A letter tapped through TalkBack or Switch Access, with its apps and its middle's y. */
    var onLetter: (apps: List<App>, y: Float) -> Unit = { _, _ -> }

    /** Offered on every letter, since TalkBack can't swipe or hold on home. */
    var actions: List<HomeAction> = emptyList()

    /** The rail that is drawn, and that the current drag belongs to. */
    var side = Side.RIGHT
        set(value) {
            field = value
            invalidate()
            contentChanged()
        }

    private val density = resources.displayMetrics.density
    private val scaled = resources.configuration.fontScale * density
    private val railWidth = 64 * density
    private val railCenter = 14 * density
    private val rowHeight = 52 * density
    private val rowInset = 14 * density
    private val letterSlot = 22 * density
    private val iconGap = 10 * density

    private var byLetter: Array<List<App>> = Array(LETTERS.length) { emptyList() }
    private var insetTop = 0
    private var insetBottom = 0
    private var g = Geometry(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    private var drag = Drag(g) { byLetter[it] }

    private var pointer = -1
    private var fingerX = 0f
    private var fingerY = 0f
    private var lastLetter = -1
    private var lastSelected = -1

    private val access = context.getSystemService(AccessibilityManager::class.java)
    private var hovered = NONE
    private var focused = NONE

    private val glyphs = LETTERS.toCharArray()
    private val letter = paint(13f, 0xD0FFFFFF.toInt(), Paint.Align.CENTER).apply {
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val letterEmpty = paint(13f, 0x55FFFFFF, Paint.Align.CENTER).apply {
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val letterOn = paint(20f, Color.WHITE, Paint.Align.CENTER).apply { isFakeBoldText = true }
    private val row = paint(18f, Color.WHITE, Paint.Align.RIGHT)
    private val rowOn = paint(18f, Color.BLACK, Paint.Align.RIGHT).apply { isFakeBoldText = true }
    private val cursor = Paint().apply { color = Color.WHITE }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val ticker = object : Runnable {
        override fun run() {
            if (!drag.active) return
            if (drag.tick(fingerX, fingerY)) changed()
            postOnAnimation(this)
        }
    }

    private fun paint(size: Float, tint: Int, alignment: Paint.Align) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = size * scaled
        color = tint
        textAlign = alignment
    }

    fun setApps(apps: List<App>) {
        val grouped = apps.groupBy { it.letter }
        byLetter = Array(LETTERS.length) { grouped[it] ?: emptyList() }
        invalidate()
        contentChanged()
    }

    fun setInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        configure()
    }

    fun dismiss() {
        pointer = -1
        drag.reset()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = configure()

    private fun configure() {
        val w = width.toFloat()
        val bottom = height - insetBottom - 16 * density
        val railTop = max(insetTop + 8 * density, bottom - letterSlot * LETTERS.length)
        g = Geometry(
            width = w,
            railLeft = w - railWidth,
            railTop = railTop,
            railBottom = bottom,
            panelLeft = w * 0.2f,
            panelTop = insetTop + 8 * density,
            panelBottom = bottom,
            rowHeight = rowHeight,
            hysteresis = 8 * density,
        )
        drag = Drag(g) { byLetter[it] }
        contentChanged()

        // The system only honours about 200dp of this per edge, from the bottom up. Above that, a
        // stroke along the rail still works, since the back gesture only takes strokes going inward.
        val top = railTop.toInt()
        val wide = railWidth.toInt()
        systemGestureExclusionRects = listOf(
            Rect(0, top, wide, bottom.toInt()),
            Rect(width - wide, top, width, bottom.toInt()),
        )
        invalidate()
    }

    private fun screenX(x: Float) = drag.local(x, side)

    // A stroke opens an app on release, not on a click, so there's no performClick to call.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val y = event.y
                val rail = drag.railAt(event.x, y) ?: return false
                if (rail != side) {
                    side = rail
                    onSide(rail)
                }
                drag.pressRail(y)
                pointer = event.getPointerId(0)
                fingerX = screenX(event.x)
                fingerY = y
                lastLetter = -1
                lastSelected = -1
                parent.requestDisallowInterceptTouchEvent(true)
                removeCallbacks(ticker)
                postOnAnimation(ticker)
                changed()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0 || !drag.active) return true
                fingerX = screenX(event.getX(index))
                fingerY = event.getY(index)
                drag.move(fingerX, fingerY)
                changed()
            }
            MotionEvent.ACTION_UP -> {
                if (drag.active) {
                    val result = drag.release()
                    pointer = -1
                    invalidate()
                    if (result is Release.Open) onOpen(result.app)
                }
            }
            MotionEvent.ACTION_CANCEL -> dismiss()
        }
        return true
    }

    private fun changed() {
        if (drag.letter != lastLetter || drag.selected != lastSelected) {
            lastLetter = drag.letter
            lastSelected = drag.selected
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val open = drag.phase != Phase.IDLE
        if (open) canvas.drawColor(SCRIM)
        drawRail(canvas)
        if (open) drawPanel(canvas)
    }

    private fun drawRail(canvas: Canvas) {
        val slot = (g.railBottom - g.railTop) / LETTERS.length
        val x = screenX(g.width - railCenter)
        for (i in glyphs.indices) {
            val paint = when {
                i == drag.letter -> letterOn
                byLetter[i].isEmpty() -> letterEmpty
                else -> letter
            }
            val y = g.railTop + slot * (i + 0.5f) - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(glyphs, i, 1, x, y, paint)
        }
    }

    private fun drawPanel(canvas: Canvas) {
        val rows = drag.rows
        val near = g.railLeft - rowInset
        val far = g.panelLeft
        val onLeft = side == Side.LEFT
        val textX = screenX(near)
        val barLeft = if (onLeft) g.width - g.railLeft else far
        val barRight = if (onLeft) g.width - far else g.railLeft
        row.textAlign = if (onLeft) Paint.Align.LEFT else Paint.Align.RIGHT
        rowOn.textAlign = row.textAlign
        canvas.save()
        canvas.clipRect(0f, g.panelTop, g.width, g.panelBottom)
        // Icons go between the name and the rail, so they line up in a column by the thumb.
        val iconWidth = rows.firstNotNullOfOrNull { it.icon }?.let { it.width + iconGap } ?: 0f
        val nameX = screenX(near - iconWidth)
        for (i in rows.indices) {
            val top = drag.panelTop + i * g.rowHeight - drag.scroll
            if (top + g.rowHeight < g.panelTop || top > g.panelBottom) continue
            val on = i == drag.selected
            if (on) canvas.drawRect(barLeft, top, barRight, top + g.rowHeight, cursor)
            val paint = if (on) rowOn else row
            val room = near - far - rowInset - iconWidth
            val text = TextUtils.ellipsize(rows[i].label, paint, room, TextUtils.TruncateAt.END)
            val y = top + g.rowHeight / 2 - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(text, 0, text.length, nameX, y, paint)
            rows[i].icon?.let { icon ->
                val left = if (onLeft) textX else textX - icon.width
                canvas.drawBitmap(icon, left, top + (g.rowHeight - icon.height) / 2, iconPaint)
            }
        }
        canvas.restore()
    }

    // TalkBack and Switch Access. AndroidX's ExploreByTouchHelper does this, done here by hand to
    // keep risco free of dependencies.

    private val provider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? =
            if (id == HOST_VIEW_ID) hostNode() else letterNode(id)

        override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean =
            if (id == HOST_VIEW_ID) performAccessibilityAction(action, arguments) else performLetter(id, action)
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = provider

    /** With TalkBack exploring by touch, a finger on the rail reads the letter under it. */
    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (!access.isEnabled || !access.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        val letter = when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> letterUnder(event.x, event.y)
            MotionEvent.ACTION_HOVER_EXIT -> NONE
            else -> return super.dispatchHoverEvent(event)
        }
        hover(letter)
        // Anywhere off the rail, the views underneath (home's pins) get the finger instead.
        return letter != NONE
    }

    private fun hostNode() = AccessibilityNodeInfo(this).also { info ->
        onInitializeAccessibilityNodeInfo(info)
        for (i in LETTERS.indices) if (byLetter[i].isNotEmpty()) info.addChild(this, i)
    }

    private fun letterNode(id: Int): AccessibilityNodeInfo? {
        if (id !in LETTERS.indices || byLetter[id].isEmpty()) return null
        return AccessibilityNodeInfo(this, id).apply {
            setParent(this@RailView)
            packageName = context.packageName
            className = Button::class.java.name
            contentDescription = letterDescription(LETTERS[id], byLetter[id].size)
            isEnabled = true
            isClickable = true
            isScreenReaderFocusable = true
            isVisibleToUser = true
            isAccessibilityFocused = focused == id
            addAction(AccessibilityAction.ACTION_CLICK)
            addAction(
                if (focused == id) {
                    AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                } else {
                    AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS
                },
            )
            this@RailView.actions.forEach { addAction(it.action) }
            setBoundsInScreen(onScreen(letterBounds(id)))
        }
    }

    private fun performLetter(id: Int, action: Int): Boolean {
        if (id !in LETTERS.indices || byLetter[id].isEmpty()) return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                if (focused == id) return false
                val old = focused
                focused = id
                if (old != NONE) send(old, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                if (focused != id) return false
                focused = NONE
                send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLICK -> {
                send(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                onLetter(byLetter[id], letterBounds(id).exactCenterY())
                true
            }
            else -> actions.perform(action)
        }
    }

    private fun hover(letter: Int) {
        if (letter == hovered) return
        val old = hovered
        hovered = letter
        if (letter != NONE) send(letter, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        if (old != NONE) send(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    /** The letter of the drawn rail under a finger, if it has apps. */
    private fun letterUnder(x: Float, y: Float): Int {
        if (drag.railAt(x, y) != side) return NONE
        val slot = (g.railBottom - g.railTop) / LETTERS.length
        val i = floor((y - g.railTop) / slot).toInt().coerceIn(0, LETTERS.length - 1)
        return if (byLetter[i].isEmpty()) NONE else i
    }

    /** Where a letter of the drawn rail sits, in this view. */
    private fun letterBounds(i: Int): Rect {
        val slot = (g.railBottom - g.railTop) / LETTERS.length
        val top = (g.railTop + slot * i).toInt()
        val bottom = (g.railTop + slot * (i + 1)).toInt()
        val wide = railWidth.toInt()
        return if (side == Side.LEFT) Rect(0, top, wide, bottom) else Rect(width - wide, top, width, bottom)
    }

    private fun onScreen(rect: Rect): Rect {
        val at = IntArray(2)
        getLocationOnScreen(at)
        rect.offset(at[0], at[1])
        return rect
    }

    private fun send(id: Int, type: Int) {
        if (!access.isEnabled) return
        val event = AccessibilityEvent(type).apply {
            setSource(this@RailView, id)
            packageName = context.packageName
            className = Button::class.java.name
            contentDescription = letterDescription(LETTERS[id], byLetter[id].size)
        }
        parent?.requestSendAccessibilityEvent(this, event)
    }

    /** The letters, their counts or the rail's side changed, so TalkBack reads them afresh. */
    private fun contentChanged() {
        if (access?.isEnabled == true) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private companion object {
        const val SCRIM = 0xD0000000.toInt()
        const val NONE = -1
    }
}
