package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTest {

    @Test fun anUnknownNoteCountsZeroSeconds() {
        assertEquals(0L, ReadingTime.seconds(emptyMap(), "a"))
    }

    @Test fun secondsAccumulatePerNote() {
        var m = ReadingTime.add(emptyMap(), "a", 30)
        m = ReadingTime.add(m, "a", 45)
        m = ReadingTime.add(m, "b", 10)
        assertEquals(75L, ReadingTime.seconds(m, "a"))
        assertEquals(10L, ReadingTime.seconds(m, "b"))
        assertEquals(2, m.size)
    }

    @Test fun negativeSecondsNeverLand() {
        val m = ReadingTime.add(emptyMap(), "a", 10)
        assertEquals(m, ReadingTime.add(m, "a", -5))
        assertEquals(10L, ReadingTime.seconds(m, "a"))
    }

    @Test fun theCapHoldsAtFiveHundred() {
        var m = emptyMap<String, Long>()
        for (i in 1..ReadingTime.CAP + 3) m = ReadingTime.add(m, "note$i", 1)
        assertEquals(ReadingTime.CAP, m.size)
        assertEquals(0L, ReadingTime.seconds(m, "note1"))
        assertEquals(1L, ReadingTime.seconds(m, "note${ReadingTime.CAP + 3}"))
    }

    @Test fun storingAgainRefreshesANotesPosition() {
        var m = emptyMap<String, Long>()
        for (k in listOf("a", "b", "c")) m = ReadingTime.add(m, k, 1)
        m = ReadingTime.add(m, "a", 1) // "a" becomes most recently stored
        while (m.size < ReadingTime.CAP) m = ReadingTime.add(m, "note-${m.size}", 1)
        m = ReadingTime.add(m, "one-more", 1)
        assertEquals(ReadingTime.CAP, m.size)
        assertEquals("b" !in m, true)
        assertEquals("a" in m, true)
    }

    @Test fun labelsReadNaturally() {
        assertEquals("45s", ReadingTime.label(45))
        assertEquals("1m", ReadingTime.label(60))
        assertEquals("45m", ReadingTime.label(45 * 60))
        assertEquals("2h 05m", ReadingTime.label(2 * 3600 + 5 * 60))
        assertEquals("٠s", ReadingTime.label(0) { s -> s.map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("") })
    }
}
