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
}
