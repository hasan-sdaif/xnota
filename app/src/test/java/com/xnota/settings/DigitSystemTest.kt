package com.xnota.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DigitSystemTest {

    @Test fun applyMapsEveryAsciiDigit() {
        assertEquals("٠١٢٣٤٥٦٧٨٩", DigitSystem.apply("0123456789"))
    }

    @Test fun applyLeavesEverythingElseAlone() {
        assertEquals("٣.٤ MB", DigitSystem.apply("3.4 MB"))
        assertEquals("١٢ رمضان ١٤٤٧", DigitSystem.apply("12 رمضان 1447"))
        assertEquals("KB", DigitSystem.apply("KB"))
        assertEquals("", DigitSystem.apply(""))
    }

    @Test fun applyIsIdempotent() {
        // Already-shaped Arabic-Indic digits pass through untouched, so wrapping twice is safe.
        val once = DigitSystem.apply("16 Sep 2026, 17:30")
        assertEquals(once, DigitSystem.apply(once))
    }

    @Test fun explicitSettingsWinOverTheLanguage() {
        assertTrue(DigitSystem.active("arabic", "en"))
        assertFalse(DigitSystem.active("latin", "ar"))
    }

    @Test fun autoFollowsTheAppLanguage() {
        assertTrue(DigitSystem.active("auto", "ar"))
        assertTrue(DigitSystem.active("auto", "ar-SA"))
        assertFalse(DigitSystem.active("auto", "en"))
        // "system" defers to the OS locale, exactly the default AppLanguage falls back to.
        assertEquals(Locale.getDefault().language == "ar", DigitSystem.active("auto", "system"))
    }

    @Test fun unknownValuesBehaveLikeAuto() {
        // The forgiving read in Preferences.fromJson never produces these, but a hand-edited
        // file could: they resolve like "auto" rather than crashing.
        assertEquals(DigitSystem.active("auto", "ar"), DigitSystem.active("roman", "ar"))
        assertEquals(DigitSystem.active("auto", "en"), DigitSystem.active("", "en"))
    }

    @Test fun preferencesRoundTripTheDigitAndCalendarSystems() {
        val p = Preferences(digitSystem = "arabic", dateSystem = "hijri")
        val back = Preferences.fromJson(p.toJson())
        assertEquals("arabic", back.digitSystem)
        assertEquals("hijri", back.dateSystem)
        // Unknown values fall back to the defaults, like every other forgiving read.
        val junk = Preferences.fromJson(JSONObject().put("digit_system", "roman").put("date_system", "julian"))
        assertEquals("auto", junk.digitSystem)
        assertEquals("gregorian", junk.dateSystem)
        // The defaults themselves round trip too.
        val plain = Preferences.fromJson(Preferences().toJson())
        assertEquals("auto", plain.digitSystem)
        assertEquals("gregorian", plain.dateSystem)
    }
}
