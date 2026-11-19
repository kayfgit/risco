package com.kayf.risco

import com.ibm.icu.text.AlphabeticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Uses ICU4J, the library Android's own android.icu is built from, so these check real alphabets
 * on the PC. [phoneAlphabet] does the same with android.icu on the phone.
 */
class AlphabetTest {
    private fun alphabet(vararg languages: Locale): Alphabet {
        val index = AlphabeticIndex<String>(languages.first()).setMaxLabelCount(MAX_LETTERS)
        languages.drop(1).forEach { index.addLabels(it) }
        index.addLabels(Locale.ENGLISH)
        val built = index.buildImmutableIndex()
        val buckets = (0 until built.bucketCount).map { built.getBucket(it) }
        return alphabetOf(
            buckets.map { it.label },
            buckets.map { it.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL },
        ) { built.getBucketIndex(it) }
    }

    private fun Alphabet.under(name: String) = letters[letterOf(name)]

    @Test fun `an English or German phone gets A to Z, then hash`() {
        val expected = ('A'..'Z').map { it.toString() } + OTHER
        assertEquals(expected, alphabet(Locale.GERMANY, Locale.US).letters)
        assertEquals(expected, alphabet(Locale.US).letters)
    }

    @Test fun `accented names go under their plain letter`() {
        val a = alphabet(Locale.GERMANY, Locale.US)
        assertEquals("U", a.under("Übersetzer"))
        assertEquals("E", a.under("Éter"))
        assertEquals("C", a.under("Câmera"))
    }

    @Test fun `names that don't start with a letter go under hash`() {
        val a = alphabet(Locale.US)
        assertEquals(OTHER, a.under("2048"))
        assertEquals(OTHER, a.under(""))
        assertEquals(OTHER, a.under("שלום"))
    }

    @Test fun `a Russian phone gets its own alphabet and still A to Z`() {
        val a = alphabet(Locale.forLanguageTag("ru-RU"))
        assertTrue("А" in a.letters && "Я" in a.letters)
        assertTrue("A" in a.letters && "Z" in a.letters)
        assertEquals("Т", a.under("Телеграм"))
        assertEquals("W", a.under("WhatsApp"))
        assertEquals(OTHER, a.letters.last())
    }

    @Test fun `a Japanese phone groups kana by row`() {
        val a = alphabet(Locale.JAPAN)
        assertTrue("あ" in a.letters && "わ" in a.letters)
        assertEquals("か", a.under("カメラ"))
        assertEquals("C", a.under("Chrome"))
    }

    @Test fun `a Chinese phone files characters under their pinyin letter`() =
        assertEquals("W", alphabet(Locale.SIMPLIFIED_CHINESE).under("微信"))

    @Test fun `a second language adds its letters after the first`() {
        val a = alphabet(Locale.US, Locale.forLanguageTag("el-GR"))
        assertEquals("Λ", a.under("Λεξικό"))
        assertEquals("M", a.under("Maps"))
    }

    @Test fun `how TalkBack reads hash`() = assertEquals("Other, 1 app", letterDescription(OTHER, 1))
}
