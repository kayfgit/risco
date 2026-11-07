package com.kayf.dedo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragTest {
    private val g = Geometry(
        width = 400f, railLeft = 340f, railTop = 270f, railBottom = 810f,
        panelLeft = 100f, panelTop = 50f, panelBottom = 850f, rowHeight = 50f, hysteresis = 8f,
    )
    private val slot = (g.railBottom - g.railTop) / LETTERS.length
    private val railX = 370f
    private val listX = 200f

    private fun yOf(letter: Char) = g.railTop + (LETTERS.indexOf(letter) + 0.5f) * slot

    private fun drag(vararg apps: String): Drag<String> {
        val byLetter = apps.groupBy { Fuzzy.letterIndex(it) }
        return Drag(g) { byLetter[it] ?: emptyList() }
    }

    @Test
    fun `one stroke from rail to app opens that app`() {
        val d = drag("Gmail", "Google", "Maps", "Photos")
        assertTrue(d.onRail(railX, yOf('G')))
        d.pressRail(yOf('G'))
        assertEquals(listOf("Gmail", "Google"), d.rows)

        d.move(listX, d.panelTop + 75f)
        assertEquals(Phase.LIST, d.phase)
        assertEquals(1, d.selected)
        assertEquals(Release.Open("Google"), d.release())
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test
    fun `sliding along the rail changes letter`() {
        val d = drag("Camera", "Maps", "Zoom")
        d.pressRail(yOf('C'))
        assertEquals(listOf("Camera"), d.rows)
        d.move(railX, yOf('M'))
        assertEquals(listOf("Maps"), d.rows)
        d.move(railX, yOf('Z'))
        assertEquals(listOf("Zoom"), d.rows)
    }

    @Test
    fun `an empty letter snaps to the closest filled one`() {
        val d = drag("Camera", "Zoom")
        d.pressRail(yOf('D'))
        assertEquals(LETTERS.indexOf('C'), d.letter)
        d.move(railX, yOf('Y'))
        assertEquals(LETTERS.indexOf('Z'), d.letter)
    }

    @Test
    fun `letting go on the rail keeps the apps open to tap`() {
        val d = drag("Maps", "Messages")
        d.pressRail(yOf('M'))
        assertEquals(Release.Pin, d.release())
        assertEquals(Phase.PINNED, d.phase)

        val y = d.panelTop + 25f
        assertTrue(d.onPanel(listX, y))
        d.pressPanel(listX, y)
        assertEquals(Release.Open("Maps"), d.release())
    }

    @Test
    fun `letting go past the panel cancels`() {
        val d = drag("Maps")
        d.pressRail(yOf('M'))
        d.move(listX, d.panelTop)
        assertEquals(0, d.selected)
        d.move(g.panelLeft - 1f, d.panelTop)
        assertEquals(-1, d.selected)
        assertEquals(Release.Cancel, d.release())
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test
    fun `sliding back to the rail reopens the letters`() {
        val d = drag("Camera", "Maps")
        d.pressRail(yOf('C'))
        d.move(listX, d.panelTop)
        assertEquals(Phase.LIST, d.phase)

        d.move(railX, yOf('M'))
        assertEquals(Phase.RAIL, d.phase)
        assertEquals(-1, d.selected)
        assertEquals(listOf("Maps"), d.rows)
    }

    @Test
    fun `small sideways wobble near the rail does not flip phases`() {
        val d = drag("Maps")
        d.pressRail(yOf('M'))
        d.move(g.railLeft - 3f, yOf('M'))
        assertEquals(Phase.RAIL, d.phase)
        d.move(g.railLeft - 20f, yOf('M'))
        assertEquals(Phase.LIST, d.phase)
        d.move(g.railLeft + 3f, yOf('M'))
        assertEquals(Phase.LIST, d.phase)
    }

    @Test
    fun `no apps loaded yet cancels instead of crashing`() {
        val d = drag()
        d.pressRail(yOf('A'))
        assertEquals(-1, d.letter)
        d.move(listX, yOf('A'))
        assertEquals(Phase.RAIL, d.phase)
        assertEquals(Release.Cancel, d.release())
    }

    @Test
    fun `panel stays on screen near the edges`() {
        val d = drag("Zoom", "Zedge", "Zara")
        d.pressRail(yOf('#'))
        assertEquals(g.panelBottom, d.panelBottom, 0.01f)
    }

    @Test
    fun `a list taller than the screen scrolls to reach its last row`() {
        val apps = (1..30).map { "Z%02d".format(it) }.toTypedArray()
        val d = drag(*apps)
        d.pressRail(yOf('Z'))
        d.move(listX, g.panelBottom - 1f)
        assertEquals(15, d.selected)
        var ticks = 0
        while (d.tick(listX, g.panelBottom - 1f)) ticks++
        assertTrue(ticks > 0)
        assertEquals(29, d.selected)
        assertEquals(Release.Open("Z30"), d.release())
    }

    @Test
    fun `a list that fits never scrolls`() {
        val d = drag("Maps")
        d.pressRail(yOf('M'))
        d.move(listX, g.panelBottom - 1f)
        assertFalse(d.tick(listX, g.panelBottom - 1f))
    }

    @Test
    fun `every letter on the rail is reachable by y alone`() {
        val d = drag(*LETTERS.map { "$it-app" }.toTypedArray())
        for (c in LETTERS) {
            d.pressRail(yOf(c))
            assertEquals(LETTERS.indexOf(c), d.letter)
        }
    }
}
