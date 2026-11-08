package com.kayf.risco

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Swipe up and down anywhere that isn't the rail. */
class HomeRoot(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    var swipes = true
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}

    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    override fun onInterceptTouchEvent(event: MotionEvent) = track(event)

    // Swipes, never clicks, so there's no performClick to call.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return true
    }

    private fun track(event: MotionEvent): Boolean {
        if (!swipes) return false
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
                    if (dy < 0) onSwipeUp() else onSwipeDown()
                    return true
                }
            }
        }
        return false
    }
}
