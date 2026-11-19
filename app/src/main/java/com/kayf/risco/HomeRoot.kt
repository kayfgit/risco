package com.kayf.risco

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Swipe up and down anywhere that isn't the rail, and hold anywhere that's empty. */
class HomeRoot(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    var swipes = true
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}
    var onHold: (x: Float, y: Float) -> Unit = { _, _ -> }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val hold = Hold(this) { x, y ->
        fired = true
        onHold(x, y)
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = track(event)

    // Only reached when nothing under the finger took the press, which makes it empty space, the
    // one place a hold means the home menu. Swipes and holds, never clicks, so no performClick.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (swipes) hold.track(event)
        track(event)
        return true
    }

    private fun track(event: MotionEvent): Boolean {
        if (!swipes) {
            hold.cancel()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                fired = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!fired && abs(dy) > slop && abs(dy) > abs(dx) * 2) {
                    fired = true
                    hold.cancel()
                    if (dy < 0) onSwipeUp() else onSwipeDown()
                    return true
                }
            }
        }
        return false
    }
}
