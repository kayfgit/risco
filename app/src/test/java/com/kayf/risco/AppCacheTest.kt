package com.kayf.risco

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppCacheTest {
    private val header = AppCache.header(locales = "en-US", iconSize = 62, iconDpi = 240)
    private val icon = byteArrayOf(1, 2, 3, 4)
    private val cache = AppCache(
        header,
        mapOf(
            "com.a/.Main@0" to AppCache.Entry("/data/app/a-1/base.apk", "Alarmy", icon),
            "com.b/.Main@0" to AppCache.Entry("/data/app/b-1/base.apk", "Bixby", null),
        ),
    )

    private fun bytes(c: AppCache) = ByteArrayOutputStream().also { c.write(it) }.toByteArray()

    @Test fun `what was saved comes back the same`() {
        val back = AppCache.read(ByteArrayInputStream(bytes(cache)), header)
        assertEquals(cache.entries.keys, back.entries.keys)
        val a = back.entries.getValue("com.a/.Main@0")
        assertEquals("Alarmy", a.label)
        assertEquals("/data/app/a-1/base.apk", a.source)
        assertArrayEquals(icon, a.icon)
        assertNull(back.entries.getValue("com.b/.Main@0").icon)
    }

    @Test fun `a cache made in another language or at another icon size is thrown away`() {
        val japanese = AppCache.header(locales = "ja-JP", iconSize = 62, iconDpi = 240)
        assertTrue(AppCache.read(ByteArrayInputStream(bytes(cache)), japanese).entries.isEmpty())
        val biggerFont = AppCache.header(locales = "en-US", iconSize = 90, iconDpi = 320)
        assertTrue(AppCache.read(ByteArrayInputStream(bytes(cache)), biggerFont).entries.isEmpty())
    }

    @Test fun `no cache yet, or a damaged one, reads as empty`() {
        assertTrue(AppCache.read(null, header).entries.isEmpty())
        val cut = bytes(cache).copyOf(20)
        assertTrue(AppCache.read(ByteArrayInputStream(cut), header).entries.isEmpty())
        assertTrue(AppCache.read(ByteArrayInputStream(byteArrayOf(9, 9, 9)), header).entries.isEmpty())
    }

    @Test fun `an app installed where it was is still fresh`() =
        assertEquals("Alarmy", cache.fresh("com.a/.Main@0", "/data/app/a-1/base.apk", icons = true)?.label)

    @Test fun `an updated app is read again, since Android installs each update in a new folder`() =
        assertNull(cache.fresh("com.a/.Main@0", "/data/app/a-2/base.apk", icons = true))

    @Test fun `with icons on, an app saved without one is read again`() =
        assertNull(cache.fresh("com.b/.Main@0", "/data/app/b-1/base.apk", icons = true))

    @Test fun `with icons off, a saved icon doesn't matter`() =
        assertEquals("Bixby", cache.fresh("com.b/.Main@0", "/data/app/b-1/base.apk", icons = false)?.label)

    @Test fun `a new app isn't in the cache`() =
        assertNull(cache.fresh("com.c/.Main@0", "/data/app/c-1/base.apk", icons = false))
}
