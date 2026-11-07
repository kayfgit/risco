package com.kayf.dedo

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Which flick a stroke from the edge strip is, with no Android in it so it can be tested on the PC.
 *
 * The strip has two halves and each takes three inward directions, giving six flicks: five open the
 * first five pins, and the sixth opens dedo itself (the leader, later the way into the marks).
 */
object Edge {
    const val LEADER = 5

    /** Slot for (half, direction). Halves: 0 lower, 1 upper. Directions: 0 straight in, 1 up, 2 down. */
    private val slots = arrayOf(
        intArrayOf(0, 1, 4),
        intArrayOf(2, 3, LEADER),
    )

    sealed class Flick {
        data object Pending : Flick()
        data object Rejected : Flick()
        data class Slot(val index: Int) : Flick()
    }

    /**
     * @param inward how far the finger has moved away from the edge, in px
     * @param down how far it has moved down the screen, in px
     * @param upper whether the stroke started in the upper half of the strip
     * @param threshold how far it must move before it counts
     */
    fun classify(inward: Float, down: Float, upper: Boolean, threshold: Float): Flick {
        if (hypot(inward, down) < threshold) return Flick.Pending
        if (inward <= 0f) return Flick.Rejected
        val angle = Math.toDegrees(atan2(down.toDouble(), inward.toDouble()))
        val direction = when {
            angle < -30 -> 1
            angle > 30 -> 2
            else -> 0
        }
        return Flick.Slot(slots[if (upper) 1 else 0][direction])
    }
}
