package com.xnota.core.text

import com.xnota.core.util.HijriMonths
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The editor's insert-stamp (the fork): the current date and time as a line of text, in the
 * Gregorian calendar or the Hijri one. Pure — the caller hands in the moment and takes back the
 * text, so the stamp the tests pin is exactly the one the toolbar writes.
 *
 * The Gregorian stamp reads like "16 Sep 2026, 17:30" (the locale's medium date and short time,
 * which most locales keep compact); the Hijri stamp reads like "25 Ramadan 1447" via
 * [HijriMonths], whose month names are the ones the rest of the app already uses.
 */
object DateStamps {

    private val GREGORIAN_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /**
     * The stamp for [at]: date and clock in the Gregorian wording, or the Hijri date (with the
     * Gregorian clock) when [hijri]. [digits] shapes the numerals for display; [locale] picks the
     * Gregorian date wording.
     */
    fun stampText(at: LocalDateTime, hijri: Boolean, arabic: Boolean, digits: (String) -> String = { it }, locale: Locale = Locale.getDefault()): String {
        val clock = at.format(CLOCK.withLocale(locale))
        return if (hijri) {
            val h = HijriMonths.of(at.toLocalDate()) ?: return gregorian(at, clock, digits, locale)
            digits(HijriMonths.format(h, arabic)) + ", " + digits(clock)
        } else {
            gregorian(at, clock, digits, locale)
        }
    }

    private fun gregorian(at: LocalDateTime, clock: String, digits: (String) -> String, locale: Locale): String =
        digits(at.format(GREGORIAN_DATE.withLocale(locale))) + ", " + digits(clock)

    /** Whether [text] was written by a Hijri stamp (used to flip the menu's last-used variant). */
    fun looksHijri(text: String, arabicMonthNames: List<String>): Boolean =
        arabicMonthNames.any { text.contains(it) } || HijriMonths.MONTHS_EN.any { text.contains(it) }

    /** [HijrahDate] for a stamp's convenience; see [HijriMonths.of]. */
    fun hijriOf(at: LocalDateTime): HijrahDate? = HijriMonths.of(at.toLocalDate())
}
