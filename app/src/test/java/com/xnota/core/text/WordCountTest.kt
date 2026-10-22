package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class WordCountTest {

    @Test fun countsWordsByWhitespace() {
        val c = WordCount.count("one two  three")
        assertEquals(3, c.words)
    }

    @Test fun arabicWordsSegmentTheSameWay() {
        // Whitespace segmentation: four words, with diacritics inside them changing nothing.
        val c = WordCount.count("كَتبُ المُعلِّمُ الدرسَ الجديد")
        assertEquals(4, c.words)
    }

    @Test fun charactersExcludeLineBreaksOnly() {
        val c = WordCount.count("ab\ncd")
        assertEquals(4, c.chars)
        assertEquals(2, c.paragraphs)
    }

    @Test fun spacesCountAsCharactersButNotWords() {
        val c = WordCount.count("a b")
        assertEquals(3, c.chars)
        assertEquals(2, c.words)
    }

    @Test fun paragraphsAreNonBlankLines() {
        val c = WordCount.count("first\n\nsecond\n  \nthird")
        assertEquals(3, c.paragraphs)
    }

    @Test fun emptyTextCountsNothing() {
        val c = WordCount.count("")
        assertEquals(WordCount.Counts(0, 0, 0), c)
    }

    @Test fun blankLinesStayEmpty() {
        val c = WordCount.count("\n \n\t\n")
        assertEquals(WordCount.Counts(0, 0, 0), c)
    }

    @Test fun readingTimeLatinAt200Wpm() {
        assertEquals(0, WordCount.readingMinutes(0))
        assertEquals(1, WordCount.readingMinutes(200))
        assertEquals(2, WordCount.readingMinutes(201))
        assertEquals(5, WordCount.readingMinutes(1000))
    }

    @Test fun readingTimeArabicAt180Wpm() {
        assertEquals(1, WordCount.readingMinutes(180, arabic = true))
        assertEquals(2, WordCount.readingMinutes(181, arabic = true))
        assertEquals(2, WordCount.readingMinutes(200, arabic = true))
    }
}
