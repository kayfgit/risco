package com.kayf.risco

import android.icu.text.AlphabeticIndex
import android.icu.text.Transliterator
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
 * under [OTHER] at the end. A name that lands in a catch-all is tried again through [into], if
 * given, which writes it in the rail's own script: "Камера" becomes "Kamera" on an English rail.
 */
fun alphabetOf(
    labels: List<String>,
    normal: List<Boolean>,
    bucketOf: (String) -> Int,
    into: ((String) -> String)? = null,
): Alphabet {
    val letters = labels.filterIndexed { i, _ -> normal[i] } + OTHER
    var next = 0
    val toLetter = IntArray(labels.size) { if (normal[it]) next++ else letters.lastIndex }
    fun letter(name: String) = toLetter.getOrElse(bucketOf(name)) { letters.lastIndex }
    return Alphabet(letters) { name ->
        val first = letter(name)
        if (first != letters.lastIndex || into == null) first else letter(into(name))
    }
}

/**
 * What `:alphabet` picks from. [DEFAULT] is the phone's languages plus A to Z. Each other one is
 * that alphabet alone, for a shorter rail, with names in other scripts written in it ([into], an
 * ICU transliterator) to find their letter. That only files them; the name shown doesn't change.
 */
enum class AlphabetChoice(val word: String, val language: String?, val into: String?) {
    DEFAULT("default", null, null),
    ENGLISH("english", "en", "Any-Latin; Latin-ASCII"),
    RUSSIAN("russian", "ru", "Any-Latin; Latin-Cyrillic"),
    GREEK("greek", "el", "Any-Latin; Latin-Greek"),
    JAPANESE("japanese", "ja", "Any-Latin; Latin-Katakana"),
    KOREAN("korean", "ko", "Any-Latin; Latin-Hangul"),
    ARABIC("arabic", "ar", "Any-Latin; Latin-Arabic"),
    HEBREW("hebrew", "he", "Any-Latin; Latin-Hebrew"),
    HINDI("hindi", "hi", "Any-Latin; Latin-Devanagari"),
    THAI("thai", "th", "Any-Latin; Latin-Thai"),
    ;

    companion object {
        fun of(word: String) = entries.firstOrNull { it.word == word } ?: DEFAULT
    }
}

/**
 * The phone's alphabets: its first language's, then any other language's it lists, then A to Z,
 * since most app names are Latin whatever the phone's language. This is how stock launchers index
 * apps too. A Russian phone gets А to Я and A to Z, a Japanese one あ to わ and A to Z, and a
 * Chinese one A to Z, with characters filed under their pinyin.
 */
fun phoneAlphabet(languages: LocaleList, choice: AlphabetChoice = AlphabetChoice.DEFAULT): Alphabet {
    val first = choice.language?.let { Locale.forLanguageTag(it) } ?: languages[0] ?: Locale.ENGLISH
    val index = AlphabeticIndex<String>(first).setMaxLabelCount(MAX_LETTERS)
    if (choice == AlphabetChoice.DEFAULT) {
        for (i in 1 until languages.size()) index.addLabels(languages[i])
        index.addLabels(Locale.ENGLISH)
    }
    val built = index.buildImmutableIndex()
    val buckets = (0 until built.bucketCount).map { built.getBucket(it) }
    // One transliterator isn't safe to share across threads, and loads can overlap.
    val into = choice.into?.let { Transliterator.getInstance(it) }
    return alphabetOf(
        buckets.map { it.label },
        buckets.map { it.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL },
        { built.getBucketIndex(it) },
        into?.let { t -> { name: String -> synchronized(t) { t.transliterate(name) } } },
    )
}
