package com.kayf.risco

import org.junit.Assert.assertEquals
import org.junit.Test

class IconDensityTest {
    @Test fun `icons are asked for at the smallest density that still covers their size`() =
        assertEquals(240, iconDensity(size = 62, screenDpi = 450))

    @Test fun `a small icon on a sharp screen doesn't load the screen's full density`() =
        assertEquals(160, iconDensity(size = 40, screenDpi = 640))

    @Test fun `a larger font grows the icon, so a sharper density is used`() =
        assertEquals(320, iconDensity(size = 90, screenDpi = 450))

    @Test fun `never more than the screen's own density`() {
        assertEquals(450, iconDensity(size = 124, screenDpi = 450))
        assertEquals(160, iconDensity(size = 62, screenDpi = 160))
    }

    @Test fun `an icon bigger than any standard density covers uses the screen's`() =
        assertEquals(640, iconDensity(size = 400, screenDpi = 640))
}
