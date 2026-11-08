package com.kayf.risco

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object Fuzzy {
    private val marks = Regex("\\p{M}+")

    /** "Câmera" and "camera" should be the same thing to a thumb. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

    /** The rail letter a label belongs under, as an index into [LETTERS]. */
    fun letterIndex(label: String): Int {
        val first = fold(label).firstOrNull()?.uppercaseChar() ?: '#'
        val index = LETTERS.indexOf(first)
        return if (index >= 0) index else LETTERS.length - 1
    }

    /**
     * Higher is better, -1 is no match. Both arguments must already be folded.
     * Deliberately tiers rather than a blended score: [tier] says how the query matched, and a
     * closer kind of match always beats an app that just gets opened more.
     */
    fun score(query: String, label: String): Int {
        if (query.isEmpty()) return 0
        val shorter = 100 - min(label.length, 99)
        if (label.startsWith(query)) return 4000 + shorter
        val words = label.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(query) }) return 3000 + shorter
        if (words.joinToString("") { it.take(1) }.startsWith(query)) return 2000 + shorter
        if (label.contains(query)) return 1000 + shorter

        var at = 0
        var first = -1
        for (c in query) {
            at = label.indexOf(c, at)
            if (at < 0) return -1
            if (first < 0) first = at
            at++
        }
        return max(1, 100 - (at - first))
    }

    fun tier(score: Int) = score / 1000
}
