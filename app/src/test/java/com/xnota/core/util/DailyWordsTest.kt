package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyWordsTest {

    private val day = LocalDate.of(2026, 9, 16)

    @Test fun keysAreIsoDays() {
        assertEquals("2026-09-16", DailyWords.keyOf(day))
        assertEquals("2026-09-16", DailyWords.keyOfToday(day))
    }

    @Test fun aDayWithoutAnEntryCountsZeroWords() {
        assertEquals(0L, DailyWords.words(emptyMap(), "2026-09-16"))
    }

    @Test fun wordsAccumulateOnTheirDayOnly() {
        var m = DailyWords.add(emptyMap(), "2026-09-16", 120)
        m = DailyWords.add(m, "2026-09-16", 30)
        m = DailyWords.add(m, "2026-09-17", 5)
        assertEquals(150L, DailyWords.words(m, "2026-09-16"))
        assertEquals(5L, DailyWords.words(m, "2026-09-17"))
        assertEquals(2, m.size)
    }

    @Test fun addingZeroToADayKeepsTheMapAsIs() {
        val m = DailyWords.add(emptyMap(), "2026-09-16", 10)
        assertEquals(m, DailyWords.add(m, "2026-09-16", 0))
    }

    @Test fun theCapHoldsAtNinetyDays() {
        var m = emptyMap<String, Long>()
        for (i in 1..DailyWords.CAP + 5) {
            m = DailyWords.add(m, day.plusDays(i.toLong()).toString(), 1)
        }
        assertEquals(DailyWords.CAP, m.size)
        assertEquals(0L, DailyWords.words(m, day.plusDays(1).toString()))
        assertEquals(1L, DailyWords.words(m, day.plusDays((DailyWords.CAP + 5).toLong()).toString()))
    }

    @Test fun prunedCutsTheOldestDays() {
        val m = (1..100).associate { day.plusDays(it.toLong()).toString() to it.toLong() }
        val p = DailyWords.pruned(m)
        assertEquals(DailyWords.CAP, p.size)
        assertEquals(100L, p[day.plusDays(100).toString()])
        assertEquals(null, p[day.plusDays(1).toString()])
    }
}
