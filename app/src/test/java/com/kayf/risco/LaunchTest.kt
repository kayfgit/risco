package com.kayf.risco

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens home as the phone does. A launcher that crashes on open takes the home button with it,
 * so this guards the whole start, such as reading translated text before the activity exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test fun `home opens without crashing`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertFalse(controller.get().isFinishing)
        }
    }

    @Config(qualifiers = "ja")
    @Test
    fun `home opens in another language too`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertFalse(controller.get().isFinishing)
        }
    }
}
