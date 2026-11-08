package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLayoutTest {
    @Test fun `with the keyboard up, results sit on it`() {
        assertEquals(900, searchBottom(keyboardUp = true, keyboardBottom = 900, barsBottom = 60, lastKeyboard = 800))
    }

    @Test fun `before the keyboard arrives, results already sit where it will be`() {
        assertEquals(900, searchBottom(keyboardUp = false, keyboardBottom = 60, barsBottom = 60, lastKeyboard = 900))
    }

    @Test fun `with no keyboard seen yet, results sit on the navigation bar`() {
        assertEquals(60, searchBottom(keyboardUp = false, keyboardBottom = 60, barsBottom = 60, lastKeyboard = 0))
    }
}
