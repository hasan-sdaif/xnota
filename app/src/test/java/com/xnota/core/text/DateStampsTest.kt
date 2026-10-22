package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class DateStampsTest {

    private val en = Locale.US

    @Test fun gregorianStampWritesDateThenClock() {
        val s = DateStamps.stampText(LocalDateTime.of(2026, 9, 16, 17, 30), hijri = false, arabic = false, locale = en)
        assertEquals("Sep 16, 2026, 5:30\u202fPM".replace("\u202f", " ").let {
            // The JVM's localized patterns differ slightly across versions; pin the shape, not the spacing.
            it.replace(" ", "")
        }, s.replace("\u202f", " ").replace(" ", ""))
    }

    @Test fun hijriStampNamesTheHijriMonth() {
        val s = DateStamps.stampText(LocalDateTime.of(2026, 2, 20, 17, 30), hijri = true, arabic = false, digits = { it }, locale = en)
        assertTrue("stamp was \"$s\"", s.startsWith("2 ") || s.startsWith("3 "))
        assertTrue(s.contains("Ramadan"))
        assertTrue(s.contains("1447"))
        assertTrue(s.contains(":30"))
    }

    @Test fun hijriStampUsesArabicMonthNamesWhenAsked() {
        val s = DateStamps.stampText(LocalDateTime.of(2026, 2, 20, 17, 30), hijri = true, arabic = true, digits = { it }, locale = en)
        assertTrue("stamp was \"$s\"", s.contains("رمضان"))
    }

    @Test fun digitsShapeTheStamp() {
        val s = DateStamps.stampText(LocalDateTime.of(2026, 2, 20, 6, 5), hijri = true, arabic = true, digits = { str ->
            str.map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")
        }, locale = en)
        assertTrue("stamp was \"$s\"", s.contains("١٤٤٧"))
        assertTrue(s.none { it in '0'..'9' })
    }

    @Test fun aDateOutsideTheHijrahRangeFallsBackToTheGregorianStamp() {
        // java.time's Hijrah chronology starts around 1882 CE; 1500 is far outside.
        val at = LocalDateTime.of(1500, 5, 5, 10, 0)
        val gregorian = DateStamps.stampText(at, hijri = false, arabic = false, locale = en)
        assertEquals(gregorian, DateStamps.stampText(at, hijri = true, arabic = false, locale = en))
    }

    @Test fun hijriOfCarriesTheConversionOrNull() {
        assertNotNull(DateStamps.hijriOf(LocalDateTime.of(2026, 1, 1, 0, 0)))
        assertNull(DateStamps.hijriOf(LocalDateTime.of(1500, 1, 1, 0, 0)))
    }

    @Test fun looksHijriSpotsAStampLine() {
        assertTrue(DateStamps.looksHijri("2 Ramadan 1447, 5:30 PM", listOf("رمضان")))
        assertTrue(DateStamps.looksHijri("٢ رمضان ١٤٤٧", listOf("رمضان")))
        assertEquals(false, DateStamps.looksHijri("Sep 16, 2026", listOf("رمضان")))
    }
}
