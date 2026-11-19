package com.kayf.risco

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Editing one widget on home, over everything else: an outline with a handle on each corner, and
 * a Remove button below it. Drag a corner to resize, drag the widget to move it among the others,
 * tap anywhere else to finish.
 *
 * This only decides and draws; [onResize], [onSwap] and the rest change home and save.
 */
class WidgetEditor(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    /** Live, on every move of a corner, in px. */
    var onResize: (view: WidgetView, width: Int, height: Int) -> Unit = { _, _, _ -> }

    /** A corner was let go: the size to keep. */
    var onResized: (view: WidgetView) -> Unit = {}

    /** Live, while dragging: the widget should swap with the one above (-1) or below (1). */
    var onSwap: (view: WidgetView, by: Int) -> Unit = { _, _ -> }

    /** The widget was let go after dragging: the order to keep. */
    var onMoved: () -> Unit = {}
    var onRemove: (view: WidgetView) -> Unit = {}
    var onFinish: () -> Unit = {}

    private val density = resources.displayMetrics.density
    private val reach = 28 * density
    private val handle = 7 * density
    private val pad = 6 * density
    private val chipGap = 14 * density
    private val chipHeight = 40 * density
    private val chipWidth = 120 * density

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0202020.toInt() }
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15 * resources.configuration.fontScale * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val removeLabel = resources.getString(R.string.remove)

    private var target: WidgetView? = null
    private var limits = Limits(0, 0, 0, 0, horizontal = false, vertical = false)
    private var widgets: () -> List<View> = { emptyList() }

    private var corner: Corner? = null
    private var moving = false
    private var startX = 0f
    private var startY = 0f
    private var startWidth = 0
    private var startHeight = 0
    private var startShift = 0f

    val editing get() = target != null

    /** Starts editing [view], one of [widgets] (home's, top to bottom), within [limits]. */
    fun start(view: WidgetView, limits: Limits, widgets: () -> List<View>) {
        finish()
        target = view
        this.limits = limits
        this.widgets = widgets
        visibility = VISIBLE
        // A little wobble, so it's clear the widget came loose.
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, -1.2f, 1.2f, -0.8f, 0.8f, 0f).setDuration(320).start()
        postInvalidateOnAnimation()
    }

    fun finish() {
        val view = target ?: return
        view.translationY = 0f
        target = null
        corner = null
        moving = false
        visibility = GONE
        onFinish()
    }

    // Edits are drags, not clicks, so there's no performClick to call.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = track(event.actionMasked, event.x, event.y)

    /** The rest of the stroke whose hold started editing, from the widget, in screen coordinates. */
    fun drag(event: MotionEvent) {
        val at = IntArray(2)
        getLocationOnScreen(at)
        val action = if (event.actionMasked == MotionEvent.ACTION_DOWN) MotionEvent.ACTION_MOVE else event.actionMasked
        // The hold left the finger on the widget, so it's already a move unless it's on a corner.
        if (!moving && corner == null) begin(event.rawX - at[0], event.rawY - at[1], fromHold = true)
        track(action, event.rawX - at[0], event.rawY - at[1])
    }

    private fun track(action: Int, x: Float, y: Float): Boolean {
        val view = target ?: return false
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val box = box(view)
                when {
                    chipBox(box).contains(x, y) -> {
                        onRemove(view)
                        finish()
                    }
                    !begin(x, y, fromHold = false) -> finish()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val grabbed = corner
                if (grabbed != null) {
                    val (w, h) = cornerResize(grabbed, startWidth, startHeight, x - startX, y - startY, limits)
                    if (w != view.width || h != view.height) onResize(view, w, h)
                } else if (moving) {
                    view.translationY = startShift + (y - startY)
                    swapIfPast(view)
                }
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (corner != null) onResized(view)
                if (moving) {
                    view.translationY = 0f
                    onMoved()
                }
                corner = null
                moving = false
                postInvalidateOnAnimation()
            }
        }
        return true
    }

    /** Grabs a corner, or the widget itself, under ([x], [y]). False when it's neither. */
    private fun begin(x: Float, y: Float, fromHold: Boolean): Boolean {
        val view = target ?: return false
        val box = box(view)
        val resizable = limits.horizontal || limits.vertical
        val grabbed = if (resizable) cornerAt(x, y, box.left, box.top, box.right, box.bottom, reach) else null
        startX = x
        startY = y
        when {
            grabbed != null -> {
                corner = grabbed
                startWidth = view.width
                startHeight = view.height
            }
            fromHold || box.contains(x, y) -> {
                moving = true
                startShift = view.translationY
            }
            else -> return false
        }
        return true
    }

    /**
     * Swaps the dragged widget with a neighbour once its middle passes theirs. The neighbour moves,
     * not the dragged one, so the stroke stays with it; the shift is corrected so it stays under the finger.
     */
    private fun swapIfPast(view: WidgetView) {
        val all = widgets()
        val at = all.indexOf(view)
        if (at < 0) return
        val center = view.top + view.translationY + view.height / 2f
        val above = all.getOrNull(at - 1)
        val below = all.getOrNull(at + 1)
        val by = swapDirection(center, above?.let { it.top + it.height / 2f }, below?.let { it.top + it.height / 2f })
        if (by == 0) return
        val passed = if (by < 0) above!!.height else -below!!.height
        onSwap(view, by)
        view.translationY += passed
        startShift += passed
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun onDraw(canvas: Canvas) {
        val view = target ?: return
        val box = box(view)
        canvas.drawRoundRect(box, 12 * density, 12 * density, outline)
        if (limits.horizontal || limits.vertical) {
            for (c in Corner.entries) {
                canvas.drawCircle(if (c.left) box.left else box.right, if (c.top) box.top else box.bottom, handle, dot)
            }
        }
        val remove = chipBox(box)
        canvas.drawRoundRect(remove, chipHeight / 2, chipHeight / 2, chip)
        val textY = remove.centerY() - (chipText.ascent() + chipText.descent()) / 2
        canvas.drawText(removeLabel, remove.centerX(), textY, chipText)
        // Follows the widget while it lays itself out at a new size.
        if (corner != null || moving) postInvalidateOnAnimation()
    }

    /** The widget's outline in this view, a little outside it. */
    private fun box(view: WidgetView): RectF {
        val me = IntArray(2)
        val it = IntArray(2)
        getLocationInWindow(me)
        view.getLocationInWindow(it)
        val left = (it[0] - me[0]).toFloat()
        val top = (it[1] - me[1]).toFloat()
        return RectF(left - pad, top - pad, left + view.width + pad, top + view.height + pad)
    }

    /** The Remove button: below the widget, or above it when there's no room below. */
    private fun chipBox(box: RectF): RectF {
        val below = box.bottom + chipGap
        val top = if (below + chipHeight < height - 48 * density) below else box.top - chipGap - chipHeight
        return RectF(box.centerX() - chipWidth / 2, top, box.centerX() + chipWidth / 2, top + chipHeight)
    }
}
