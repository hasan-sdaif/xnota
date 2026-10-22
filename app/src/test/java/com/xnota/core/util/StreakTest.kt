package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 9, 16)

    private fun days(vararg back: Long): Set<String> =
        back.map { today.minusDays(it).toString() }.toSet()

    @Test fun anEmptyHistoryIsNoStreak() {
        assertEquals(0, Streak.of(emptySet(), today))
    }

    @Test fun consecutiveDaysBackFromTodayCount() {
        assertEquals(3, Streak.of(days(0, 1, 2), today))
        assertEquals(1, Streak.of(days(0), today))
    }

    @Test fun todayNotWrittenYetKeepsYesterdayRunAlive() {
        assertEquals(2, Streak.of(days(1, 2), today))
    }

    @Test fun aGapEndsTheRunAtTheGap() {
        assertEquals(2, Streak.of(days(0, 1, 3, 4), today))
        assertEquals(1, Streak.of(days(1, 4, 5), today))
    }

    @Test fun futureDaysDoNotCount() {
        assertEquals(1, Streak.of(days(0, -1, -2), today))
    }

    @Test fun aLongRunCountsEveryDay() {
        assertEquals(30, Streak.of((0..29L).map { today.minusDays(it).toString() }.toSet(), today))
    }
}
