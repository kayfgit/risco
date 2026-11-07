package com.kayf.dedo

import com.kayf.dedo.Edge.Flick
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeTest {
    private val t = 30f

    private fun slot(inward: Float, down: Float, upper: Boolean) = Edge.classify(inward, down, upper, t)

    @Test
    fun `too short is still pending`() {
        assertEquals(Flick.Pending, slot(10f, 10f, upper = false))
        assertEquals(Flick.Pending, slot(-20f, 0f, upper = true))
    }

    @Test
    fun `moving towards the edge is rejected`() {
        assertEquals(Flick.Rejected, slot(-40f, 0f, upper = false))
        assertEquals(Flick.Rejected, slot(0f, 40f, upper = false))
    }

    @Test
    fun `lower half gives slots 0, 1 and 4`() {
        assertEquals(Flick.Slot(0), slot(40f, 0f, upper = false))
        assertEquals(Flick.Slot(1), slot(30f, -40f, upper = false))
        assertEquals(Flick.Slot(4), slot(30f, 40f, upper = false))
    }

    @Test
    fun `upper half gives slots 2, 3 and the leader`() {
        assertEquals(Flick.Slot(2), slot(40f, 0f, upper = true))
        assertEquals(Flick.Slot(3), slot(30f, -40f, upper = true))
        assertEquals(Flick.Slot(Edge.LEADER), slot(30f, 40f, upper = true))
    }

    @Test
    fun `the 30 degree borders split straight from diagonal`() {
        assertEquals(Flick.Slot(0), slot(40f, -20f, upper = false))
        assertEquals(Flick.Slot(1), slot(40f, -25f, upper = false))
    }

    @Test
    fun `every slot is reachable exactly once`() {
        val seen = mutableListOf<Int>()
        for (upper in listOf(false, true)) {
            for (down in listOf(0f, -40f, 40f)) {
                seen += (slot(40f, down, upper) as Flick.Slot).index
            }
        }
        assertEquals((0..Edge.LEADER).toList(), seen.sorted())
    }
}
