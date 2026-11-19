package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessTest {
    @Test fun `a letter is read with how many apps it holds`() = assertEquals("B, 3 apps", letterDescription('B', 3))

    @Test fun `one app is read as one app`() = assertEquals("Z, 1 app", letterDescription('Z', 1))

    @Test fun `the hash letter is read as Other, not as a symbol`() =
        assertEquals("Other, 2 apps", letterDescription('#', 2))
}
