package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListMarkersTest {

    private fun marker(line: String): ListMarkers.Marker? = ListMarkers.detect(line)

    @Test fun detectsThePlainBullets() {
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.BULLET, "- "), marker("- "))
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.BULLET, "* "), marker("* "))
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.BULLET, "+ "), marker("+ "))
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.BULLET, "• "), marker("• "))
    }

    @Test fun detectsOrderedMarkersWithEitherSeparator() {
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.ORDERED, "1. "), marker("1. "))
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.ORDERED, "12) "), marker("12) "))
    }

    @Test fun detectsTheCheckMarkers() {
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.CHECK, "[ ] "), marker("[ ] "))
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.CHECK, "[x] "), marker("[x] "))
    }

    @Test fun aMarkerMustEndWithItsSpace() {
        // "-word" is a hyphenated word, "-" is a bare hyphen: neither is a list item.
        assertNull(marker("-word"))
        assertNull(marker("-"))
        assertNull(marker("1.2 "))
        assertNull(marker("nothing here"))
    }

    @Test fun aMarkerIsAPrefixNotTheWholeVocabulary() {
        // Detection asks about the marker itself (the prefix a line starts with); content follows.
        assertEquals(ListMarkers.Marker(ListMarkers.Kind.BULLET, "- "), marker("- "))
    }

    @Test fun bulletsRepeatOnTheNextLine() {
        assertEquals("- ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.BULLET, "- ")))
        assertEquals("* ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.BULLET, "* ")))
    }

    @Test fun orderedMarkersStepUp() {
        assertEquals("4. ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.ORDERED, "3. ")))
        assertEquals("10) ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.ORDERED, "9) ")))
        assertEquals("2. ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.ORDERED, "1. ")))
    }

    @Test fun checksContinueUnchecked() {
        assertEquals("[ ] ", ListMarkers.next(ListMarkers.Marker(ListMarkers.Kind.CHECK, "[x] ")))
    }

    @Test fun stripRemovesTheMarkerItWasGiven() {
        assertEquals("item", ListMarkers.strip("- item", ListMarkers.Marker(ListMarkers.Kind.BULLET, "- ")))
        assertEquals("item", ListMarkers.strip("3. item", ListMarkers.Marker(ListMarkers.Kind.ORDERED, "3. ")))
        // A marker the line does not carry leaves the line alone.
        assertEquals("- item", ListMarkers.strip("- item", ListMarkers.Marker(ListMarkers.Kind.BULLET, "* ")))
    }

    @Test fun stepNeverDropsBelowOne() {
        assertEquals("1", ListMarkers.step("1", -1))
        assertEquals("1", ListMarkers.step("1", 0))
        assertEquals("5", ListMarkers.step("3", 2))
        assertEquals("2", ListMarkers.step("3", -1))
        // A non-number comes back as itself.
        assertEquals("x", ListMarkers.step("x", 1))
    }

    // --- continuation (the fork's auto-continue) ---

    @Test fun aPlainLineContinuesToNothing() {
        assertNull(ListMarkers.continuation("hello"))
        assertNull(ListMarkers.continuation("-word"))
        assertNull(ListMarkers.continuation("-"))
        assertNull(ListMarkers.continuation(""))
    }

    @Test fun aBulletedItemContinuesItsBullet() {
        assertEquals("- ", ListMarkers.continuation("- hello"))
        assertEquals("* ", ListMarkers.continuation("* hello"))
        assertEquals("• ", ListMarkers.continuation("• hello"))
    }

    @Test fun anOrderedItemStepsItsNumberUp() {
        assertEquals("2. ", ListMarkers.continuation("1. hello"))
        assertEquals("4. ", ListMarkers.continuation("3. hello"))
        assertEquals("13) ", ListMarkers.continuation("12) hello"))
    }

    @Test fun aCheckedItemContinuesUnchecked() {
        assertEquals("[ ] ", ListMarkers.continuation("[ ] hello"))
        assertEquals("[ ] ", ListMarkers.continuation("[x] hello"))
    }

    @Test fun aBareMarkerDropsOutInsteadOfContinuing() {
        assertEquals("", ListMarkers.continuation("- "))
        assertEquals("", ListMarkers.continuation("1. "))
        assertEquals("", ListMarkers.continuation("[x] "))
    }
}
