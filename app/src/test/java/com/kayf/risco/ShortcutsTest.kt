package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsTest {
    private data class S(val name: String, val declared: Boolean, val rank: Int)

    private fun pick(vararg all: S) = menuShortcuts(all.toList(), { it.declared }, { it.rank }).map { it.name }

    @Test fun `an app's declared shortcuts come before the ones it adds while running`() = assertEquals(
        listOf("compose", "search", "recent chat"),
        pick(S("recent chat", false, 0), S("search", true, 1), S("compose", true, 0)),
    )

    @Test fun `each kind keeps the order the app ranked it in`() = assertEquals(
        listOf("a", "b", "c", "d"),
        pick(S("d", false, 1), S("b", true, 1), S("c", false, 0), S("a", true, 0)),
    )

    @Test fun `the menu shows at most four, dropping the added ones first`() = assertEquals(
        listOf("m0", "m1", "m2", "d0"),
        pick(S("d1", false, 1), S("d0", false, 0), S("m2", true, 2), S("m1", true, 1), S("m0", true, 0)),
    )

    @Test fun `an app with no shortcuts gets none`() = assertEquals(emptyList<String>(), pick())

    @Test fun `a shortcut's key names its app, its id and its profile`() =
        assertEquals("s:com.android.chrome/site-1@0", shortcutKey("com.android.chrome", "site-1", 0))

    @Test fun `shortcut keys are told apart from app keys`() {
        assertTrue(isShortcutKey(shortcutKey("com.android.chrome", "site-1", 0)))
        assertFalse(isShortcutKey("com.android.chrome/.Main@0"))
    }

    @Test fun `a newly pinned shortcut goes to the end of home`() = assertEquals(
        listOf("app1", "s:a/1@0", "app2", "s:b/2@0"),
        withNewPins(listOf("app1", "s:a/1@0", "app2"), listOf("s:a/1@0", "s:b/2@0")),
    )

    @Test fun `a pin the system no longer reports keeps its place`() = assertEquals(
        listOf("app1", "s:a/1@0"),
        withNewPins(listOf("app1", "s:a/1@0"), emptyList()),
    )
}
