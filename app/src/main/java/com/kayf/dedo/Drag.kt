package com.kayf.dedo

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The rail's letters, top to bottom. They never move, so the thumb can learn where they are. */
const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

/** Where things are, in pixels, always as if the rail were on the right. */
data class Geometry(
    val width: Float,
    val railLeft: Float,
    val railTop: Float,
    val railBottom: Float,
    val panelLeft: Float,
    val panelTop: Float,
    val panelBottom: Float,
    val rowHeight: Float,
    val hysteresis: Float,
)

enum class Phase { IDLE, RAIL, LIST, PINNED }

/** There's a rail on each edge, one per thumb. Both share one [Drag], with x mirrored for the left. */
enum class Side { LEFT, RIGHT }

sealed class Release<out T> {
    data class Open<T>(val app: T) : Release<T>()
    data object Pin : Release<Nothing>()
    data object Cancel : Release<Nothing>()
}

/**
 * The whole drag, with no Android in it so it can be tested on the PC.
 *
 * One stroke: press the rail, slide along it to a letter, slide off it towards the apps, slide to
 * an app, let go. Letting go on the rail keeps that letter's apps open to tap instead. Sliding back
 * onto the rail reopens the letters, and letting go past the left of the panel cancels.
 */
class Drag<T>(private val g: Geometry, private val appsIn: (letter: Int) -> List<T>) {
    var phase = Phase.IDLE
        private set
    var letter = -1
        private set
    var rows: List<T> = emptyList()
        private set
    var panelTop = 0f
        private set
    var scroll = 0f
        private set
    var selected = -1
        private set

    private val available get() = g.panelBottom - g.panelTop
    private val contentHeight get() = rows.size * g.rowHeight
    private val maxScroll get() = max(0f, contentHeight - available)

    val active get() = phase == Phase.RAIL || phase == Phase.LIST
    val panelBottom get() = min(g.panelBottom, panelTop + contentHeight)

    fun onRail(x: Float, y: Float) = x >= g.railLeft && y >= g.railTop && y <= g.railBottom

    /** Which rail a touch at screen x lands on, if either. */
    fun railAt(screenX: Float, y: Float): Side? = when {
        onRail(screenX, y) -> Side.RIGHT
        onRail(g.width - screenX, y) -> Side.LEFT
        else -> null
    }

    /** Screen x as this class sees it, which is always as if the rail were on the right. */
    fun local(screenX: Float, side: Side) = if (side == Side.LEFT) g.width - screenX else screenX

    fun onPanel(x: Float, y: Float) =
        phase == Phase.PINNED && rows.isNotEmpty() && x >= g.panelLeft && x < g.railLeft && y >= panelTop && y < panelBottom

    fun pressRail(y: Float) {
        phase = Phase.RAIL
        selected = -1
        slideRail(y)
    }

    fun pressPanel(x: Float, y: Float) {
        phase = Phase.LIST
        move(x, y)
    }

    fun move(x: Float, y: Float) {
        when (phase) {
            Phase.RAIL -> {
                if (x < g.railLeft - g.hysteresis && rows.isNotEmpty()) {
                    phase = Phase.LIST
                    selectAt(x, y)
                } else {
                    slideRail(y)
                }
            }
            Phase.LIST -> {
                if (x > g.railLeft + g.hysteresis) {
                    phase = Phase.RAIL
                    selected = -1
                    slideRail(y)
                } else {
                    selectAt(x, y)
                }
            }
            else -> {}
        }
    }

    /** Called every frame during a drag, so a list taller than the screen scrolls at its ends. */
    fun tick(x: Float, y: Float): Boolean {
        if (phase != Phase.LIST || maxScroll == 0f) return false
        val band = g.rowHeight * 1.5f
        val step = when {
            y < g.panelTop + band -> -g.rowHeight / 6
            y > g.panelBottom - band -> g.rowHeight / 6
            else -> return false
        }
        val next = (scroll + step).coerceIn(0f, maxScroll)
        if (next == scroll) return false
        scroll = next
        selectAt(x, y)
        return true
    }

    fun release(): Release<T> {
        val result = when (phase) {
            Phase.RAIL -> if (rows.isNotEmpty()) Release.Pin else Release.Cancel
            Phase.LIST -> if (selected >= 0) Release.Open(rows[selected]) else Release.Cancel
            else -> Release.Cancel
        }
        if (result == Release.Pin) phase = Phase.PINNED else reset()
        return result
    }

    fun reset() {
        phase = Phase.IDLE
        letter = -1
        rows = emptyList()
        selected = -1
        scroll = 0f
    }

    private fun slideRail(y: Float) {
        val next = nearestFilled(letterAt(y))
        if (next != letter) {
            letter = next
            rows = if (next >= 0) appsIn(next) else emptyList()
            scroll = 0f
        }
        val top = y - contentHeight / 2
        panelTop = if (maxScroll > 0f) g.panelTop else top.coerceIn(g.panelTop, g.panelBottom - contentHeight)
    }

    private fun selectAt(x: Float, y: Float) {
        selected = if (x < g.panelLeft || rows.isEmpty()) {
            -1
        } else {
            floor((y - panelTop + scroll) / g.rowHeight).toInt().coerceIn(0, rows.size - 1)
        }
    }

    private fun letterAt(y: Float): Int {
        val slot = (g.railBottom - g.railTop) / LETTERS.length
        return floor((y - g.railTop) / slot).toInt().coerceIn(0, LETTERS.length - 1)
    }

    /** A letter with no apps gives way to the closest one that has some, so no stretch of the rail is dead. */
    private fun nearestFilled(from: Int): Int {
        for (distance in LETTERS.indices) {
            val before = from - distance
            if (before >= 0 && appsIn(before).isNotEmpty()) return before
            val after = from + distance
            if (after < LETTERS.length && appsIn(after).isNotEmpty()) return after
        }
        return -1
    }
}
