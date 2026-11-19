package com.kayf.risco

import android.icu.text.AlphabeticIndex
import android.os.LocaleList
import java.util.Locale

/** The rail's last letter, for names that start with no letter of the phone's alphabets. */
const val OTHER = "#"

/** More than any alphabet needs, so none is cut short. */
const val MAX_LETTERS = 300

/**
 * The rail's letters, top to bottom, and which one a name goes under. They come from the phone's
 * languages, never from the installed apps, so they don't move as apps come and go.
 */
class Alphabet(val letters: List<String>, private val bucket: (String) -> Int) {
    fun letterOf(name: String): Int = bucket(name).coerceIn(0, letters.lastIndex)
}

/**
 * An [Alphabet] from an alphabetic index's buckets, in order. [normal] says which are real
 * letters; the rest are ICU's catch-alls (before, between and after the alphabets), which all go
 * under [OTHER] at the end.
 */
fun alphabetOf(labels: List<String>, normal: List<Boolean>, bucketOf: (String) -> Int): Alphabet {
    val letters = labels.filterIndexed { i, _ -> normal[i] } + OTHER
    var next = 0
    val toLetter = IntArray(labels.size) { if (normal[it]) next++ else letters.lastIndex }
    return Alphabet(letters) { name -> toLetter.getOrElse(bucketOf(name)) { letters.lastIndex } }
}

/**
 * The phone's alphabets: its first language's, then any other language's it lists, then A to Z,
 * since most app names are Latin whatever the phone's language. This is how stock launchers index
 * apps too. A Russian phone gets А to Я and A to Z, a Japanese one あ to わ and A to Z, and a
 * Chinese one A to Z, with characters filed under their pinyin.
 */
fun phoneAlphabet(languages: LocaleList): Alphabet {
    val index = AlphabeticIndex<String>(languages[0] ?: Locale.ENGLISH).setMaxLabelCount(MAX_LETTERS)
    for (i in 1 until languages.size()) index.addLabels(languages[i])
    index.addLabels(Locale.ENGLISH)
    val built = index.buildImmutableIndex()
    val buckets = (0 until built.bucketCount).map { built.getBucket(it) }
    return alphabetOf(
        buckets.map { it.label },
        buckets.map { it.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL },
    ) { built.getBucketIndex(it) }
}
