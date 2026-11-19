package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetsTest {
    @Test fun `a widget is named after its app, then itself`() =
        assertEquals("Clock · Digital clock", widgetLabel("Clock", "Digital clock"))

    @Test fun `a widget that only repeats its app's name is named once`() {
        assertEquals("Calendar", widgetLabel("Calendar", " calendar"))
        assertEquals("Gmail inbox", widgetLabel("Gmail", "Gmail inbox"))
    }

    @Test fun `a missing name falls back to the other`() {
        assertEquals("Weather", widgetLabel("Weather", ""))
        assertEquals("Battery", widgetLabel("", "Battery"))
    }

    @Test fun `widget ids round trip in order`() = assertEquals(
        listOf(12, 3, 40),
        decodeIds(encodeIds(listOf(12, 3, 40))),
    )

    @Test fun `no widgets saved is an empty list`() = assertEquals(emptyList<Int>(), decodeIds(""))

    @Test fun `a damaged id is skipped, the rest kept`() = assertEquals(listOf(1, 3), decodeIds("1,x,3"))

    @Test fun `a widget's own minimum height wins`() = assertEquals(150, widgetHeight(150, 40, 3, 80, 160))

    @Test fun `with no minimum, the rows it asks for decide`() = assertEquals(240, widgetHeight(0, 40, 3, 80, 160))

    @Test fun `with only a resize minimum, it is never smaller than the fallback`() {
        assertEquals(160, widgetHeight(0, 30, 0, 80, 160))
        assertEquals(200, widgetHeight(0, 200, 0, 80, 160))
    }

    @Test fun `a widget that says nothing about its size gets the fallback`() =
        assertEquals(160, widgetHeight(0, 0, 0, 80, 160))

    @Test fun `moving a widget up swaps it with the one above`() =
        assertEquals(listOf(1, 3, 2), moved(listOf(1, 2, 3), 3, -1))

    @Test fun `moving a widget down swaps it with the one below`() =
        assertEquals(listOf(2, 1, 3), moved(listOf(1, 2, 3), 1, 1))

    @Test fun `a widget at the top can't move further up, nor the bottom one down`() {
        assertEquals(listOf(1, 2, 3), moved(listOf(1, 2, 3), 1, -1))
        assertEquals(listOf(1, 2, 3), moved(listOf(1, 2, 3), 3, 1))
    }

    @Test fun `moving a widget that isn't there changes nothing`() =
        assertEquals(listOf(1, 2), moved(listOf(1, 2), 9, 1))

    @Test fun `taller and shorter go one step at a time`() {
        assertEquals(240, resized(200, 40, 100, 400))
        assertEquals(160, resized(200, -40, 100, 400))
    }

    @Test fun `a widget never grows past its largest or shrinks past its smallest`() {
        assertEquals(400, resized(380, 40, 100, 400))
        assertEquals(100, resized(120, -40, 100, 400))
    }

    @Test fun `a widget whose largest is below its smallest stays at its smallest`() =
        assertEquals(100, resized(100, 40, 100, 50))

    private val free =
        Limits(minWidth = 100, maxWidth = 600, minHeight = 80, maxHeight = 900, horizontal = true, vertical = true)

    @Test fun `dragging the bottom right corner out grows both ways, the width twice since it stays centred`() =
        assertEquals(Pair(340, 260), cornerResize(Corner.BOTTOM_RIGHT, 300, 200, dx = 20f, dy = 60f, free))

    @Test fun `dragging the top left corner up and left also grows it`() =
        assertEquals(Pair(340, 260), cornerResize(Corner.TOP_LEFT, 300, 200, dx = -20f, dy = -60f, free))

    @Test fun `dragging a corner inwards shrinks it`() =
        assertEquals(Pair(260, 140), cornerResize(Corner.BOTTOM_LEFT, 300, 200, dx = 20f, dy = -60f, free))

    @Test fun `a corner drag stops at the widget's smallest and largest`() {
        assertEquals(Pair(600, 900), cornerResize(Corner.BOTTOM_RIGHT, 300, 200, dx = 500f, dy = 2000f, free))
        assertEquals(Pair(100, 80), cornerResize(Corner.TOP_RIGHT, 300, 200, dx = -500f, dy = 2000f, free))
    }

    @Test fun `a widget that only resizes one way keeps the other`() {
        val tall = free.copy(horizontal = false)
        assertEquals(Pair(300, 260), cornerResize(Corner.BOTTOM_RIGHT, 300, 200, dx = 20f, dy = 60f, tall))
        val wide = free.copy(vertical = false)
        assertEquals(Pair(340, 200), cornerResize(Corner.BOTTOM_RIGHT, 300, 200, dx = 20f, dy = 60f, wide))
    }

    @Test fun `a finger near a corner grabs it`() {
        assertEquals(Corner.TOP_LEFT, cornerAt(105f, 52f, 100f, 50f, 400f, 250f, reach = 30f))
        assertEquals(Corner.BOTTOM_RIGHT, cornerAt(420f, 270f, 100f, 50f, 400f, 250f, reach = 30f))
    }

    @Test fun `a finger in the middle or far away grabs no corner`() {
        assertEquals(null, cornerAt(250f, 150f, 100f, 50f, 400f, 250f, reach = 30f))
        assertEquals(null, cornerAt(500f, 400f, 100f, 50f, 400f, 250f, reach = 30f))
    }

    @Test fun `a dragged widget swaps once its middle passes a neighbour's`() {
        assertEquals(-1, swapDirection(center = 140f, aboveCenter = 150f, belowCenter = 500f))
        assertEquals(1, swapDirection(center = 510f, aboveCenter = 150f, belowCenter = 500f))
        assertEquals(0, swapDirection(center = 300f, aboveCenter = 150f, belowCenter = 500f))
    }

    @Test fun `the first and last widgets have nothing to swap with on their open side`() {
        assertEquals(0, swapDirection(center = -100f, aboveCenter = null, belowCenter = 500f))
        assertEquals(0, swapDirection(center = 900f, aboveCenter = 150f, belowCenter = null))
    }
}
