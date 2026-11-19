package com.kayf.risco

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * A press held still for the system's long-press time. Fed every touch event of a stroke, it fires
 * [onHold] once with where the finger is, and [track] then says the stroke is taken.
 */
class Hold(private val view: View, private val onHold: (x: Float, y: Float) -> Unit) {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    var held = false
        private set

    private val fire = Runnable {
        held = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onHold(downX, downY)
    }

    /** True once the hold has fired, for the rest of the stroke. */
    fun track(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                held = false
                view.removeCallbacks(fire)
                view.postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (hypot(event.x - downX, event.y - downY) > slop) cancel()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancel()
        }
        return held
    }

    fun cancel() = view.removeCallbacks(fire)
}
