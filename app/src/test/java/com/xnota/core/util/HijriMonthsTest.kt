package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriMonthsTest {

    @Test fun monthNamesCoverTwelveMonthsInBothLanguages() {
        assertEquals(12, HijriMonths.MONTHS_EN.size)
        assertEquals(12, HijriMonths.MONTHS_AR.size)
        assertEquals("Muharram", HijriMonths.monthName(1, arabic = false))
        assertEquals("Ramadan", HijriMonths.monthName(9, arabic = false))
        assertEquals("Dhu\u02BFl-Hijja", HijriMonths.MONTHS_EN[11])
        assertEquals("محرم", HijriMonths.monthName(1, arabic = true))
        assertEquals("رمضان", HijriMonths.monthName(9, arabic = true))
        assertEquals("ذو الحجة", HijriMonths.monthName(12, arabic = true))
    }

    @Test fun monthNumbersOutsideTheYearClampRatherThanThrow() {
        assertEquals("Muharram", HijriMonths.monthName(0, arabic = false))
        assertEquals("Dhu\u02BFl-Hijja", HijriMonths.monthName(13, arabic = false))
    }

    @Test fun aKnownGregorianDayConvertsToItsHijriDate() {
        // 1 January 2026 is 12 Rajab 1447 in the Umm al-Qura reckoning java.time carries.
        val h = HijriMonths.of(LocalDate.of(2026, 1, 1))
        assertEquals(1447, h?.get(java.time.temporal.ChronoField.YEAR))
        assertEquals(7, h?.get(java.time.temporal.ChronoField.MONTH_OF_YEAR))
        assertEquals(12, h?.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
    }

    @Test fun datesOutsideTheHijrahRangeComeBackNull() {
        assertNull(HijriMonths.of(LocalDate.of(1500, 1, 1)))
    }

    @Test fun formatWritesDayMonthYearWithShapedDigits() {
        assertEquals("12 Rajab 1447", HijriMonths.format(12, 7, 1447, arabic = false))
        assertEquals("١٢ رجب ١٤٤٧", HijriMonths.format(12, 7, 1447, arabic = true) { s ->
            s.map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")
        })
    }

    @Test fun formatOfAHijrahDateAgreesWithTheFieldForm() {
        val h = HijriMonths.of(LocalDate.of(2026, 1, 1)) ?: return
        assertEquals(HijriMonths.format(12, 7, 1447, arabic = false), HijriMonths.format(h, arabic = false))
    }

    @Test fun hijriMonthsAgreeWithTheExplorerCalendarForARamadanDay() {
        // 20 February 2026 falls in Ramadan 1447 (Umm al-Qura).
        val h = HijriMonths.of(LocalDate.of(2026, 2, 20))
        assertEquals(9, h?.get(java.time.temporal.ChronoField.MONTH_OF_YEAR))
        assertTrue((h?.get(java.time.temporal.ChronoField.DAY_OF_MONTH) ?: -1) in 1..29)
    }
}
