package com.kayf.dedo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTest {
    private fun score(query: String, label: String) = Fuzzy.score(Fuzzy.fold(query), Fuzzy.fold(label))

    @Test
    fun `tiers rank prefix, word, initials, substring, then subsequence`() {
        val prefix = score("ma", "Maps")
        val word = score("ma", "Google Maps")
        val initials = score("gm", "Google Maps")
        val inside = score("ai", "Gmail")
        val scattered = score("gl", "Gmail")
        assertTrue(prefix > word)
        assertTrue(word > initials)
        assertTrue(initials > inside)
        assertTrue(inside > scattered)
        assertTrue(scattered > 0)
    }

    @Test
    fun `no match is negative`() = assertEquals(-1, score("xq", "Gmail"))

    @Test
    fun `accents do not matter`() {
        assertTrue(score("camera", "Câmera") > 0)
        assertEquals(LETTERS.indexOf('E'), Fuzzy.letterIndex("Éter"))
    }

    @Test
    fun `labels that do not start with a letter go under hash`() {
        assertEquals(LETTERS.indexOf('#'), Fuzzy.letterIndex("2048"))
        assertEquals(LETTERS.indexOf('#'), Fuzzy.letterIndex(""))
    }

    @Test
    fun `plain text is a search`() = assertEquals(Input.Search("maps"), parseInput("maps"))

    @Test
    fun `a colon lists commands`() {
        assertEquals(Input.Partial(Command.entries), parseInput(":"))
        assertEquals(Input.Partial(listOf(Command.PIN)), parseInput(":pi"))
    }

    @Test
    fun `a command with an argument waits for the space`() {
        assertEquals(Input.Partial(listOf(Command.PIN)), parseInput(":pin"))
        assertEquals(Input.Full(Command.PIN, ""), parseInput(":pin "))
        assertEquals(Input.Full(Command.PIN, "google maps"), parseInput(":pin google maps"))
    }

    @Test
    fun `clock offers its positions as you type`() {
        assertEquals(Input.Full(Command.CLOCK, "bo"), parseInput(":clock bo"))
        assertEquals(listOf("top", "middle", "bottom", "off"), Command.CLOCK.choicesFor(""))
        assertEquals(listOf("bottom"), Command.CLOCK.choicesFor("bo"))
        assertEquals(listOf("off"), Command.CLOCK.choicesFor(" OF"))
        assertEquals(emptyList<String>(), Command.CLOCK.choicesFor("x"))
    }

    @Test
    fun `a command without an argument is complete at once`() {
        assertEquals(Input.Full(Command.SET, ""), parseInput(":set"))
    }
}
