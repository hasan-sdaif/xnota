package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslamicCalendarTest {

    @Test fun theCanonicalDaysMapToTheirEvents() {
        assertEquals("islamic_new_year", IslamicCalendar.eventOf(1, 1)?.key)
        assertEquals("islamic_ashura", IslamicCalendar.eventOf(1, 10)?.key)
        assertEquals("islamic_arbaeen", IslamicCalendar.eventOf(2, 20)?.key)
        assertEquals("islamic_mawlid", IslamicCalendar.eventOf(3, 12)?.key)
        assertEquals("islamic_isra_miraj", IslamicCalendar.eventOf(7, 27)?.key)
        assertEquals("islamic_mid_shaban", IslamicCalendar.eventOf(8, 15)?.key)
        assertEquals("islamic_ramadan_first", IslamicCalendar.eventOf(9, 1)?.key)
        assertEquals("islamic_badr", IslamicCalendar.eventOf(9, 17)?.key)
        assertEquals("islamic_qadr", IslamicCalendar.eventOf(9, 27)?.key)
        assertEquals("islamic_eid_fitr", IslamicCalendar.eventOf(10, 1)?.key)
        assertEquals("islamic_arafah", IslamicCalendar.eventOf(12, 9)?.key)
        assertEquals("islamic_eid_adha", IslamicCalendar.eventOf(12, 10)?.key)
    }

    @Test fun multiDayFestivalsNameEveryDayTheySpan() {
        for (day in 1..3) assertEquals("islamic_eid_fitr", IslamicCalendar.eventOf(10, day)?.key)
        for (day in 10..13) assertEquals("islamic_eid_adha", IslamicCalendar.eventOf(12, day)?.key)
    }

    @Test fun ordinaryDaysHaveNoEvent() {
        assertNull(IslamicCalendar.eventOf(1, 2))
        assertNull(IslamicCalendar.eventOf(4, 15))
        assertNull(IslamicCalendar.eventOf(11, 20))
        assertNull(IslamicCalendar.eventOf(9, 15))
    }

    @Test fun everyEventIsARealHijriMonthAndDay() {
        for (e in IslamicCalendar.EVENTS) {
            assertTrue("month ${e.month}", e.month in 1..12)
            assertTrue("day ${e.day}", e.day in 1..30)
        }
    }

    @Test fun theTwelveNamesAreAllDistinctKeys() {
        val keys = IslamicCalendar.EVENTS.map { it.key }.distinct()
        assertEquals(12, keys.size)
        assertEquals(17, IslamicCalendar.EVENTS.size)
    }

    @Test fun fixedHijriDatesResolveAsTheUmmAlQuraCalendarSays() {
        // Pin the lookup end to end — Gregorian today -> HijrahDate -> event — on days this
        // JDK's Umm al-Qura tables place exactly: 20 March 2026 = 1 Shawwal 1447 (Eid
        // al-Fitr), 25 June 2026 = 10 Muharram 1448 (Ashura), 19 June 2026 = 4 Muharram 1448
        // (an ordinary day).
        fun h(d: java.time.LocalDate) = java.time.chrono.HijrahDate.from(d)
        fun month(d: java.time.LocalDate) = h(d).get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        fun day(d: java.time.LocalDate) = h(d).get(java.time.temporal.ChronoField.DAY_OF_MONTH)

        val eid = java.time.LocalDate.of(2026, 3, 20)
        assertEquals(10, month(eid)); assertEquals(1, day(eid))
        assertEquals("islamic_eid_fitr", IslamicCalendar.eventOf(month(eid), day(eid))?.key)

        val ashura = java.time.LocalDate.of(2026, 6, 25)
        assertEquals(1, month(ashura)); assertEquals(10, day(ashura))
        assertEquals("islamic_ashura", IslamicCalendar.eventOf(month(ashura), day(ashura))?.key)

        assertNull(IslamicCalendar.eventOf(month(java.time.LocalDate.of(2026, 6, 19)), day(java.time.LocalDate.of(2026, 6, 19))))
    }
}
