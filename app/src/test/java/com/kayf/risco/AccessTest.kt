package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessTest {
    @Test fun `a letter is read as itself`() = assertEquals("B", letterName("B", "Other"))

    @Test fun `the hash letter is read as Other, not as a symbol`() = assertEquals("Other", letterName(OTHER, "Other"))

    @Test fun `Other is read in risco's language`() = assertEquals("Andere", letterName(OTHER, "Andere"))
}
