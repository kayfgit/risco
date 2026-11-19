package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class RenameTest {
    private class Named(val names: List<String>, val uses: Int = 0)

    private fun named(label: String, original: String = label, uses: Int = 0) =
        Named(listOf(label, original).map { Fuzzy.fold(it) }.distinct(), uses)

    private fun rank(pool: List<Named>, query: String) = Fuzzy.rank(pool, query, { it.names }, { it.uses }, 20)

    @Test fun `a custom name replaces the app's own`() = assertEquals("Maps", displayLabel("Google Maps", "Maps"))

    @Test fun `with no custom name, the app keeps its own`() =
        assertEquals("Google Maps", displayLabel("Google Maps", null))

    @Test fun `a blank custom name counts as none`() = assertEquals("Google Maps", displayLabel("Google Maps", "   "))

    @Test fun `a custom name is trimmed`() = assertEquals("Maps", displayLabel("Google Maps", "  Maps "))

    @Test fun `a renamed app is found by its new name`() {
        val maps = named("Karte", "Google Maps")
        assertEquals(listOf(maps), rank(listOf(maps, named("Gmail")), "kar"))
    }

    @Test fun `a renamed app is still found by its old name`() {
        val maps = named("Karte", "Google Maps")
        assertEquals(listOf(maps), rank(listOf(maps, named("Gmail")), "maps"))
    }

    @Test fun `a better match on either name wins over how often an app is used`() {
        val maps = named("Karte", "Google Maps")
        val camera = named("Kamera", uses = 50)
        assertEquals(listOf(maps, camera), rank(listOf(camera, maps), "kar"))
    }

    @Test fun `with equal matches, the app used more comes first`() {
        val a = named("Notes", uses = 1)
        val b = named("Notion", uses = 9)
        assertEquals(listOf(b, a), rank(listOf(a, b), "no"))
    }

    @Test fun `ranking stops at the limit`() =
        assertEquals(2, Fuzzy.rank((1..5).map { named("App $it") }, "app", { it.names }, { it.uses }, 2).size)
}
