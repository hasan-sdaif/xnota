package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search tab's remembered queries (the fork): an MRU list of the last eight distinct searches
 * — a revisit moves its query to the front instead of duplicating it, the oldest falls off the
 * end, and a stored list read back is trimmed, deduped and capped.
 */
class SearchHistoryTest {

    private val full = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")

    @Test fun theCapIsEight() {
        assertEquals(8, SearchHistory.CAP)
    }

    @Test fun aQueryLandsNewestFirst() {
        assertEquals(listOf("ink"), SearchHistory.record(emptyList(), "ink"))
        assertEquals(listOf("ink", "one", "two"), SearchHistory.record(listOf("one", "two"), "ink"))
    }

    @Test fun aRevisitMovesToFrontWithoutDuplicating() {
        assertEquals(
            listOf("three", "one", "two", "four"),
            SearchHistory.record(listOf("one", "two", "three", "four"), "three"),
        )
    }

    @Test fun theOldestFallsOffBeyondTheCap() {
        val next = SearchHistory.record(full, "new")
        assertEquals(listOf("new", "one", "two", "three", "four", "five", "six", "seven"), next)
        assertTrue(next.size == SearchHistory.CAP)
    }

    @Test fun aBlankQueryChangesNothing() {
        assertEquals(full, SearchHistory.record(full, ""))
        assertEquals(full, SearchHistory.record(full, "   "))
    }

    @Test fun aQueryIsTrimmed() {
        assertEquals(listOf("ink"), SearchHistory.record(emptyList(), "  ink "))
    }

    @Test fun sanitizeTrimsDedupesAndCaps() {
        assertEquals(
            listOf("one", "two", "three"),
            SearchHistory.sanitize(listOf(" one ", "", "two", "one", "three")),
        )
        assertEquals(full, SearchHistory.sanitize(full + "nine" + full.reversed()))
    }
}
