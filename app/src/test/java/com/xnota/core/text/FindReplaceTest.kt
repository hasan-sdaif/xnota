package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindReplaceTest {

    private fun ranges(haystack: String, needle: String, caseSensitive: Boolean = false, wholeWord: Boolean = false, ignoreDiacritics: Boolean = false): String =
        FindReplace.findAll(haystack, needle, caseSensitive, wholeWord, ignoreDiacritics).joinToString(",") { "${it.start}..${it.end}" }

    @Test fun findsEveryOccurrenceInOrder() {
        assertEquals("0..1,2..3,4..5", ranges("a a a", "a"))
    }

    @Test fun findsNeedleInsideWords() {
        assertEquals("0..2,4..6", ranges("cat catalog", "ca"))
    }

    @Test fun caseFoldsUnlessItMatters() {
        assertEquals("0..5,6..11,12..17", ranges("Hello hello HELLO", "hello"))
        assertEquals("6..11", ranges("Hello hello HELLO", "hello", caseSensitive = true))
    }

    @Test fun wholeWordDropsPartialMatches() {
        assertEquals("0..3", ranges("cat catalog", "cat", wholeWord = true))
        assertEquals("0..3,14..17", ranges("cat, catalog! cat", "cat", wholeWord = true))
    }

    @Test fun arabicCaseFoldingAndDiacritics() {
        // The same word, one bare and one with a kasra: identical once the marks are stepped over.
        // The second word carries the definite article, so its inner "كتاب" starts two
        // characters in — the range covers from the ق of الكِتاب to the end of the word.
        assertEquals("0..5,8..13", ranges("كِتاب الكِتاب", "كتاب", ignoreDiacritics = true))
        // Without ignoring the marks the voweled form does not contain the bare needle.
        assertEquals("", ranges("كِتاب الكِتاب", "كتاب", ignoreDiacritics = false))
    }

    @Test fun latinCaseStillFoldsWhileDiacriticsAreIgnored() {
        assertEquals("7..11", ranges("ملاحظة Note ملاحظة", "NOTE", ignoreDiacritics = true))
    }

    @Test fun onlyTheTashkeelRangeIsStripped() {
        // The superscript alef (U+0670) is a letter outside the tashkeel range, so it still matters.
        assertEquals("", ranges("هٰذا", "هذا", ignoreDiacritics = true))
        assertEquals("0..4", ranges("هٰذا", "هٰذا", ignoreDiacritics = true))
    }

    @Test fun wholeWordSeesThroughLeadingMarks() {
        // A damma before the word is a mark, not a word character once diacritics are ignored.
        assertEquals("1..5", ranges("ُكتاب", "كتاب", wholeWord = true, ignoreDiacritics = true))
    }

    // --- the fork's loose Arabic folds (mirroring SearchFold) ---

    @Test fun hamzaAlefVariantsFoldWhenDiacriticsAreIgnored() {
        assertEquals("0..4,5..9", ranges("أحمد احمد", "احمد", ignoreDiacritics = true))
        assertEquals("0..4,5..9", ranges("أحمد احمد", "أحمد", ignoreDiacritics = true))
        assertEquals("5..9", ranges("أحمد احمد", "احمد", ignoreDiacritics = false))
    }

    @Test fun taMarbutaFoldsToHaWhenDiacriticsAreIgnored() {
        assertEquals("0..5,6..11", ranges("مدرسة مدرسه", "مدرسه", ignoreDiacritics = true))
        assertEquals("6..11", ranges("مدرسة مدرسه", "مدرسه", ignoreDiacritics = false))
    }

    @Test fun arabicIndicDigitsFoldToLatinWhenDiacriticsAreIgnored() {
        assertEquals("5..7,12..14", ranges("سورة ١٢ آية 12", "١٢", ignoreDiacritics = true))
        assertEquals("5..7,12..14", ranges("سورة ١٢ آية 12", "12", ignoreDiacritics = true))
        assertEquals("12..14", ranges("سورة ١٢ آية 12", "12", ignoreDiacritics = false))
    }

    @Test fun replaceAllThroughTheArabicFolds() {
        assertEquals("قراءة القراءة", FindReplace.replaceAll("قرأة القرأه", "قرأه", "قراءة", ignoreDiacritics = true))
    }

    @Test fun blankNeedleMatchesNothing() {
        assertEquals("", ranges("anything", ""))
    }

    @Test fun needleLongerThanHaystackMatchesNothing() {
        assertEquals("", ranges("ab", "abc"))
    }

    @Test fun replaceAllIsOverlapSafe() {
        assertEquals("aa", FindReplace.replaceAll("aaaa", "aa", "a"))
        assertEquals("bb bb", FindReplace.replaceAll("ab ab", "a", "b"))
    }

    @Test fun replaceAllWithSelfReferentialReplacementDoesNotLoop() {
        // The replacement contains the needle: replacing must not re-replace inside what it wrote.
        assertEquals("aa", FindReplace.replaceAll("a", "a", "aa"))
    }

    @Test fun replaceAllKeepsUnmatchedTextWhole() {
        assertEquals("one two three", FindReplace.replaceAll("one 1 three", "1", "two"))
    }

    @Test fun replaceAllFoldsCaseAndDiacritics() {
        assertEquals("باب باب", FindReplace.replaceAll("باب بَاب", "باب", "باب", ignoreDiacritics = true))
    }

    @Test fun replaceAllWholeWordOnly() {
        assertEquals("a catalog!", FindReplace.replaceAll("cat catalog!", "cat", "a", wholeWord = true))
    }

    @Test fun replaceAllWithNoMatchesReturnsTheOriginal() {
        val s = "nothing here"
        assertTrue(FindReplace.replaceAll(s, "needle", "x") === s)
    }
}
