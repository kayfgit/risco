package com.kayf.dedo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * The alphabet rail and the panel of apps beside it, drawn straight to the canvas so a drag only
 * ever repaints this one view. All the decisions are in [Drag]; this turns touches into calls on
 * it and draws what it says.
 */
class RailView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var onOpen: (App) -> Unit = {}
    var onHold: (App, Float, Float) -> Unit = { _, _, _ -> }

    var onLeft = false
        set(value) {
            field = value
            configure()
        }

    private val density = resources.displayMetrics.density
    private val scaled = resources.configuration.fontScale * density
    private val railWidth = 64 * density
    private val railCenter = 38 * density
    private val rowHeight = 52 * density
    private val rowInset = 14 * density
    private val letterSlot = 22 * density

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
    private var holdable = false

    private val glyphs = LETTERS.toCharArray()
    private val letter = paint(13f, 0xD0FFFFFF.toInt(), Paint.Align.CENTER).apply { setShadowLayer(3 * density, 0f, 0f, Color.BLACK) }
    private val letterEmpty = paint(13f, 0x55FFFFFF, Paint.Align.CENTER).apply { setShadowLayer(3 * density, 0f, 0f, Color.BLACK) }
    private val letterOn = paint(20f, Color.WHITE, Paint.Align.CENTER).apply { isFakeBoldText = true }
    private val row = paint(18f, Color.WHITE, Paint.Align.RIGHT)
    private val rowOn = paint(18f, Color.BLACK, Paint.Align.RIGHT).apply { isFakeBoldText = true }
    private val cursor = Paint().apply { color = Color.WHITE }

    private val hold = Runnable {
        val app = drag.rows.getOrNull(drag.selected) ?: return@Runnable
        val x = screenX(g.railLeft)
        val y = fingerY
        drag.reset()
        pointer = -1
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
        onHold(app, x, y)
    }

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
    }

    fun setInsets(top: Int, bottom: Int) {
        if (top == insetTop && bottom == insetBottom) return
        insetTop = top
        insetBottom = bottom
        configure()
    }

    fun dismiss(): Boolean {
        val open = drag.phase != Phase.IDLE
        removeCallbacks(hold)
        pointer = -1
        drag.reset()
        invalidate()
        return open
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
        row.textAlign = if (onLeft) Paint.Align.LEFT else Paint.Align.RIGHT
        rowOn.textAlign = row.textAlign

        // The system only honours about 200dp of this per edge, which is why the letters also sit
        // inboard of the edge: outside this rect they are still past the back gesture's narrow zone.
        val left = if (onLeft) 0 else g.railLeft.toInt()
        systemGestureExclusionRects = listOf(Rect(left, railTop.toInt(), left + railWidth.toInt(), bottom.toInt()))
        invalidate()
    }

    private fun screenX(x: Float) = if (onLeft) width - x else x

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = screenX(event.x)
                val y = event.y
                when {
                    drag.onRail(x, y) -> drag.pressRail(y)
                    drag.onPanel(x, y) -> drag.pressPanel(x, y)
                    drag.phase == Phase.PINNED -> {
                        dismiss()
                        return true
                    }
                    else -> return false
                }
                pointer = event.getPointerId(0)
                fingerX = x
                fingerY = y
                lastLetter = -1
                lastSelected = -1
                // Pausing mid-drag means deciding, not asking for the menu.
                holdable = drag.phase == Phase.LIST
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
                removeCallbacks(hold)
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
            removeCallbacks(hold)
            if (holdable && drag.selected >= 0) postDelayed(hold, HOLD_MS)
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
        val textX = screenX(near)
        val barLeft = if (onLeft) g.width - g.railLeft else far
        val barRight = if (onLeft) g.width - far else g.railLeft
        canvas.save()
        canvas.clipRect(0f, g.panelTop, g.width, g.panelBottom)
        for (i in rows.indices) {
            val top = drag.panelTop + i * g.rowHeight - drag.scroll
            if (top + g.rowHeight < g.panelTop || top > g.panelBottom) continue
            val on = i == drag.selected
            if (on) canvas.drawRect(barLeft, top, barRight, top + g.rowHeight, cursor)
            val paint = if (on) rowOn else row
            val text = TextUtils.ellipsize(rows[i].label, paint, near - far - rowInset, TextUtils.TruncateAt.END)
            val y = top + g.rowHeight / 2 - (paint.ascent() + paint.descent()) / 2
            canvas.drawText(text, 0, text.length, textX, y, paint)
        }
        canvas.restore()
    }

    private companion object {
        const val SCRIM = 0xD0000000.toInt()
        const val HOLD_MS = 600L
    }
}
