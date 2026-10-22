package com.xnota.core.util

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Hijri month names and date wording (the fork), for the places that write a Hijri date the
 * explorer's date machinery does not reach: the explorer's today chip, the editor's insert-stamp
 * and any HUD that names the day. Pure — the caller picks the language ([MONTHS_EN] or
 * [MONTHS_AR]) and hands the digits through its own shaper, so core stays free of resources.
 *
 * The month numbers are the Hijri calendar's own (1 = Muharram … 12 = Dhu'l-Hijja); the
 * conversion itself is [java.time.chrono.HijrahDate] (Umm al-Qura), the same one the explorer's
 * date wording uses, so every Hijri date in the app agrees with every other.
 */
object HijriMonths {

    /** English Hijri month names, index 0 = Muharram. */
    val MONTHS_EN = listOf(
        "Muharram", "Safar", "Rabiʿ I", "Rabiʿ II", "Jumada I", "Jumada II",
        "Rajab", "Shaʿban", "Ramadan", "Shawwal", "Dhuʿl-Qiʿda", "Dhuʿl-Hijja",
    )

    /** Arabic Hijri month names, index 0 = Muharram. */
    val MONTHS_AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    /** The month name for Hijri month number [month] (1..12), in [arabic] or English. */
    fun monthName(month: Int, arabic: Boolean): String {
        val names = if (arabic) MONTHS_AR else MONTHS_EN
        return names[(month - 1).coerceIn(0, names.lastIndex)]
    }

    /** The Hijri date of [date] (Umm al-Qura), or null outside the Hijrah range. */
    fun of(date: LocalDate): HijrahDate? = try {
        HijrahDate.from(date)
    } catch (_: Exception) {
        null
    }

    /** "25 Ramadan 1447" — the day-month-year wording of Hijri (`day`, `month`, `year`). */
    fun format(day: Int, month: Int, year: Int, arabic: Boolean, digits: (String) -> String = { it }): String =
        digits("$day") + " " + monthName(month, arabic) + " " + digits("$year")

    /** [hijri] as "25 Ramadan 1447" (see [format]). */
    fun format(hijri: HijrahDate, arabic: Boolean, digits: (String) -> String = { it }): String =
        format(
            hijri.get(ChronoField.DAY_OF_MONTH),
            hijri.get(ChronoField.MONTH_OF_YEAR),
            hijri.get(ChronoField.YEAR),
            arabic,
            digits,
        )
}
