package com.xnota.core.text

/**
 * Word, character and paragraph counts over a block of plain text (the fork), plus the reading
 * time they imply. Pure, so the format bar's counter, any export and the tests all read the same
 * numbers. Words are whitespace-separated runs of non-whitespace — Arabic words carry no spaces
 * inside them, so the same segmentation counts them correctly, diacritics or not.
 */
object WordCount {

    /** What [count] reports: whole numbers, never negative. */
    class Counts(val words: Int, val chars: Int, val paragraphs: Int) {
        override fun equals(other: Any?): Boolean = other is Counts && other.words == words && other.chars == chars && other.paragraphs == paragraphs
        override fun hashCode(): Int = (words * 31 + chars) * 31 + paragraphs
        override fun toString(): String = "Counts(words=$words, chars=$chars, paragraphs=$paragraphs)"
    }

    /**
     * Count [text]: characters excluding line breaks (a paragraph break is structure, not
     * writing), words as whitespace-separated non-blank runs, paragraphs as non-blank
     * line-separated blocks — so an empty note is 0/0/0 and a note of blank lines stays that way.
     */
    fun count(text: String): Counts {
        // Line-based: a blank line (spaces and tabs included) is structure, not writing — it
        // counts as no paragraph and contributes no characters; inner spaces on a written line
        // count as characters exactly as "a b" is three.
        var chars = 0
        var words = 0
        var paragraphs = 0
        var inWord = false
        for (c in text) {
            if (c == '\n') {
                inWord = false
                continue
            }
            if (c.isWhitespace()) {
                inWord = false
                continue
            }
            if (!inWord) words++
            inWord = true
        }
        for (line in text.split('\n')) {
            if (line.any { !it.isWhitespace() }) {
                paragraphs++
                chars += line.length
            }
        }
        return Counts(words, chars, paragraphs)
    }

    /**
     * The reading time of [words] words, in whole minutes (a partial minute rounds up: 201 words
     * is two full minutes and a start on a third). Latin reading is taken at 200 words a minute,
     * Arabic at 180 — its script carries fewer words per thought.
     */
    fun readingMinutes(words: Int, arabic: Boolean = false): Int {
        if (words <= 0) return 0
        val wpm = if (arabic) ARABIC_WPM else LATIN_WPM
        return (words + wpm - 1) / wpm
    }

    /** Words a minute the estimate assumes for Latin-script and Arabic reading. */
    const val LATIN_WPM = 200
    const val ARABIC_WPM = 180
}
