package com.xnota.ui

import com.xnota.core.model.Rgba
import com.xnota.settings.ExplorerSortKey
import com.xnota.settings.GroupBy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** What an explorer entry is, for badges, the kind filter and grouping by kind. */
internal enum class EntryKind { FOLDER, NOTE, PDF, CANVAS }

/** The plain name of a colour nobody has named, from its hue. */
internal enum class Hue { BLACK, WHITE, GREY, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK }

/** The explorer's wording for dates, counts, kinds and hues; string resources supply it on device. */
internal interface ExplorerWords {
    fun month(m: Month): String
    fun shortMonth(m: Month): String
    fun shortWeekday(d: DayOfWeek): String
    val today: String
    val yesterday: String
    val earlierThisWeek: String
    val lastWeek: String
    fun earlierIn(m: Month): String
    fun monthYear(m: Month, year: Int): String
    val justNow: String
    fun minutesAgo(n: Long): String
    fun hoursAgo(n: Long): String
    fun daysAgo(n: Long): String
    fun dayMonth(day: Int, m: Month): String
    fun dayMonthYear(day: Int, m: Month, year: Int): String
    fun withTime(date: String, clock: String): String
    fun clock12(hour: Int, minute: Int, pm: Boolean): String
    fun opened(ago: String): String
    fun folders(n: Int): String
    fun files(n: Int): String
    fun items(n: Int): String
    fun kinds(k: EntryKind): String
    fun hue(h: Hue): String
    val noColour: String

    // xnota: the fork's digit and calendar wording. `digits` shapes numerals for display
    // (Arabic-Indic ٠-٩ when the digit pref is on); the hijri members name Hijri months by
    // number 1..12 so the Umm al-Qura conversions below can hand them straight to the usual
    // date format strings. Day-relative wording ("2 days ago") stays Gregorian either way.

    /** Numerals shaped for display: ASCII 0-9 become ٠-٩ when Arabic digits are on. */
    fun digits(s: String): String

    /** Whether dates are written in the Hijri calendar ([Preferences.dateSystem] "hijri"). */
    val hijri: Boolean

    /** Hijri month name by month number, 1 = Muharram … 12 = Dhu'l-Hijja. */
    fun hijriMonth(n: Int): String

    /** Hijri "day month" ("12 Ramadan"), the sibling of [dayMonth]. */
    fun hijriDayMonth(day: Int, month: Int): String

    /** Hijri "day month year" ("12 Ramadan 1447"), the sibling of [dayMonthYear]. */
    fun hijriDayMonthYear(day: Int, month: Int, year: Int): String

    /** Hijri "month year" ("Ramadan 1447"), the sibling of [monthYear]. */
    fun hijriMonthYear(month: Int, year: Int): String

    /** "Earlier in <Hijri month>", the sibling of [earlierIn]. */
    fun hijriEarlierIn(n: Int): String
}

/** One heading's worth of entries; [color] is set when grouped by colour. An empty [label] means no heading. */
internal class EntryGroup(val key: String, val label: String, val items: List<BrowseEntry>, val color: Rgba? = null)

/** A date heading and the first day it covers, which orders the headings. */
internal class DateBucket(val label: String, val start: LocalDate)

private fun dayOf(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

/**
 * [date] in the Hijri calendar (Umm al-Qura), or null when it falls outside the Hijrah chronology's
 * range (roughly 1882-2174 CE): callers silently fall back to the Gregorian wording there.
 */
private fun hijriOf(date: LocalDate): HijrahDate? = try {
    HijrahDate.from(date)
} catch (_: Exception) {
    null
}

// HijrahDate's day/month/year getters sit on a package-private base class, so the wording reads
// them through the TemporalField API instead of the usual synthetic properties.
private val HijrahDate.hijriDay: Int get() = get(ChronoField.DAY_OF_MONTH)
private val HijrahDate.hijriMonthNumber: Int get() = get(ChronoField.MONTH_OF_YEAR)
private val HijrahDate.hijriYear: Int get() = get(ChronoField.YEAR)

/** The month [day] heads under when dates are written Gregorian. */
private fun gregorianMonthBucket(w: ExplorerWords, day: LocalDate, today: LocalDate): DateBucket {
    val start = day.withDayOfMonth(1)
    return when {
        day.year == today.year && day.month == today.month -> DateBucket(w.earlierIn(today.month), start)
        day.year == today.year -> DateBucket(w.month(day.month), start)
        else -> DateBucket(w.monthYear(day.month, day.year), start)
    }
}

/**
 * The month [day] heads under when dates are written Hijri: this month / this year's months / month
 * and year, by Hijri month boundaries. Null when [day] or [today] is outside the Hijrah range, so
 * the caller keeps the Gregorian heading.
 */
private fun hijriMonthBucket(w: ExplorerWords, day: LocalDate, today: LocalDate): DateBucket? {
    val hDay = hijriOf(day) ?: return null
    val hToday = hijriOf(today) ?: return null
    val start = LocalDate.ofEpochDay(hDay.with(TemporalAdjusters.firstDayOfMonth()).toEpochDay())
    return when {
        hDay.hijriYear == hToday.hijriYear && hDay.hijriMonthNumber == hToday.hijriMonthNumber -> DateBucket(w.hijriEarlierIn(hDay.hijriMonthNumber), start)
        hDay.hijriYear == hToday.hijriYear -> DateBucket(w.hijriMonth(hDay.hijriMonthNumber), start)
        else -> DateBucket(w.hijriMonthYear(hDay.hijriMonthNumber, hDay.hijriYear), start)
    }
}

/** The heading [time] files under, seen from [now]: Today, Yesterday, this week, last week, earlier this month, then by month. */
internal fun dateBucket(w: ExplorerWords, time: Long, now: Long, zone: ZoneId, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateBucket {
    val today = dayOf(now, zone)
    val day = dayOf(time, zone).let { if (it.isAfter(today)) today else it }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(weekStart))
    val lastWeek = thisWeek.minusWeeks(1)
    return when {
        day == today -> DateBucket(w.today, today)
        day == today.minusDays(1) -> DateBucket(w.yesterday, day)
        !day.isBefore(thisWeek) -> DateBucket(w.earlierThisWeek, thisWeek)
        !day.isBefore(lastWeek) -> DateBucket(w.lastWeek, lastWeek)
        // Day-relative buckets done; the rest head by month — Hijri when the calendar pref says so,
        // falling back to the Gregorian heading outside the Hijrah range.
        else -> if (w.hijri) hijriMonthBucket(w, day, today) ?: gregorianMonthBucket(w, day, today) else gregorianMonthBucket(w, day, today)
    }
}

/**
 * Splits [items] (already sorted) under headings. Items keep their order within a heading. Date headings run
 * newest first unless the list is sorted by that date oldest first; they follow the created date when sorting
 * by it and the modified date otherwise.
 */
internal fun groupEntries(
    w: ExplorerWords,
    items: List<BrowseEntry>,
    by: GroupBy,
    sortKey: ExplorerSortKey,
    descending: Boolean,
    kindOf: (BrowseEntry) -> EntryKind,
    nameOf: (Rgba) -> String?,
    now: Long,
    zone: ZoneId,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): List<EntryGroup> {
    if (items.isEmpty()) return emptyList()
    return when (by) {
        GroupBy.NONE -> listOf(EntryGroup("all", "", items))
        GroupBy.DATE -> {
            val byCreated = sortKey == ExplorerSortKey.CREATED
            val buckets = LinkedHashMap<String, Pair<DateBucket, MutableList<BrowseEntry>>>()
            for (e in items) {
                val b = dateBucket(w, if (byCreated) e.created else e.modified, now, zone, weekStart)
                buckets.getOrPut(b.label) { b to ArrayList() }.second.add(e)
            }
            val oldestFirst = !descending && (sortKey == ExplorerSortKey.MODIFIED || byCreated)
            val ordered = buckets.values.sortedBy { it.first.start }
            (if (oldestFirst) ordered else ordered.reversed()).map { (b, list) -> EntryGroup(b.label, b.label, list) }
        }
        GroupBy.KIND -> {
            val byKind = items.groupBy(kindOf)
            EntryKind.entries.mapNotNull { k -> byKind[k]?.let { EntryGroup(k.name, w.kinds(k), it) } }
        }
        GroupBy.COLOUR -> {
            val byColor = items.groupBy { it.color }
            val named = byColor.keys.filterNotNull().mapNotNull { c -> nameOf(c)?.let { c to it } }.sortedBy { it.second.lowercase() }
            val unnamed = byColor.keys.filterNotNull().filter { nameOf(it) == null }
                .map { it to hueName(w, it) }.sortedWith(compareBy({ it.second }, { Rgba.toHex(it.first) }))
            (named + unnamed).map { (c, label) -> EntryGroup(Rgba.toHex(c), label, byColor.getValue(c), c) } +
                listOfNotNull(byColor[null]?.let { EntryGroup("none", w.noColour, it) })
        }
    }
}

/** A plain name for a colour nobody has named, from its hue. */
internal fun hueName(w: ExplorerWords, c: Rgba): String = w.hue(hueOf(c))

internal fun hueOf(c: Rgba): Hue {
    val r = c.r / 255.0
    val g = c.g / 255.0
    val b = c.b / 255.0
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    if (max < 0.15) return Hue.BLACK
    if (d / max < 0.15) return if (max > 0.85) Hue.WHITE else Hue.GREY
    val h = when (max) {
        r -> 60 * ((g - b) / d)
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }.let { if (it < 0) it + 360 else it }
    return when {
        h < 15 || h >= 345 -> Hue.RED
        h < 40 -> Hue.ORANGE
        h < 65 -> Hue.YELLOW
        h < 160 -> Hue.GREEN
        h < 195 -> Hue.TEAL
        h < 255 -> Hue.BLUE
        h < 290 -> Hue.PURPLE
        else -> Hue.PINK
    }
}

internal fun clockText(w: ExplorerWords, t: LocalTime, clock24: Boolean): String =
    // The 24h branch is Kotlin-formatted, so its digits are shaped here; the 12h branch is a
    // resource string and shaped inside ResourceWords.
    if (clock24) w.digits("%02d:%02d".format(Locale.ROOT, t.hour, t.minute))
    else w.clock12((t.hour + 11) % 12 + 1, t.minute, t.hour >= 12)

/** [day] as a day-month date, in whichever calendar [w] writes dates. */
private fun dayMonthDate(w: ExplorerWords, day: LocalDate): String =
    if (w.hijri) hijriOf(day)?.let { w.hijriDayMonth(it.hijriDay, it.hijriMonthNumber) } ?: w.dayMonth(day.dayOfMonth, day.month)
    else w.dayMonth(day.dayOfMonth, day.month)

/** [day] as a day-month-year date, in whichever calendar [w] writes dates. */
private fun dayMonthYearDate(w: ExplorerWords, day: LocalDate): String =
    if (w.hijri) hijriOf(day)?.let { w.hijriDayMonthYear(it.hijriDay, it.hijriMonthNumber, it.hijriYear) } ?: w.dayMonthYear(day.dayOfMonth, day.month, day.year)
    else w.dayMonthYear(day.dayOfMonth, day.month, day.year)

/**
 * [time] as the explorer writes it under a tile. [style] is "relative" (2 days ago), "day" (Wed 17:30, the
 * default) or "date" (16 Sep 2026); [withTime] adds the time of day where the style has room for it.
 */
internal fun formatWhen(w: ExplorerWords, time: Long, now: Long, style: String, withTime: Boolean, clock24: Boolean, zone: ZoneId): String {
    if (time <= 0) return ""
    val at = Instant.ofEpochMilli(time).atZone(zone)
    val today = dayOf(now, zone)
    val day = at.toLocalDate()
    val clock = clockText(w, at.toLocalTime(), clock24)
    val dayMonth = if (day.year != today.year) dayMonthYearDate(w, day) else dayMonthDate(w, day)
    return when (style) {
        "relative" -> {
            val mins = (now - time) / 60_000
            when {
                mins < 1 -> w.justNow
                day == today && mins < 60 -> w.minutesAgo(mins)
                day == today -> w.hoursAgo(mins / 60)
                day == today.minusDays(1) -> w.yesterday
                day.isAfter(today.minusDays(7)) -> w.daysAgo(ChronoUnit.DAYS.between(day, today))
                else -> dayMonth
            }
        }
        "date" -> dayMonthYearDate(w, day).let { if (withTime) w.withTime(it, clock) else it }
        else -> {
            val d = when {
                !day.isBefore(today) -> w.today
                day == today.minusDays(1) -> w.yesterday
                day.isAfter(today.minusDays(7)) -> w.shortWeekday(day.dayOfWeek)
                else -> return dayMonth
            }
            if (withTime) "$d $clock" else d
        }
    }
}

/** [time] in full, for details panes: 16 Sep 2026, 17:30. */
internal fun formatFull(w: ExplorerWords, time: Long, clock24: Boolean, zone: ZoneId): String {
    if (time <= 0) return ""
    val at = Instant.ofEpochMilli(time).atZone(zone)
    return w.withTime(dayMonthYearDate(w, at.toLocalDate()), clockText(w, at.toLocalTime(), clock24))
}

/**
 * A timeline month's heading: the month's name alone inside the current year, name and year otherwise —
 * in whichever calendar [w] writes dates. Hijri conversion samples the month's middle day so one
 * Gregorian month keeps one label; the timeline's navigation itself stays Gregorian either way.
 */
internal fun monthHeading(w: ExplorerWords, month: YearMonth, today: LocalDate): String {
    if (w.hijri) hijriOf(month.atDay(15))?.let { h ->
        val sameYear = hijriOf(today)?.hijriYear == h.hijriYear
        return if (sameYear) w.hijriMonth(h.hijriMonthNumber) else w.hijriMonthYear(h.hijriMonthNumber, h.hijriYear)
    }
    return if (month.year == today.year) w.month(month.month) else w.monthYear(month.month, month.year)
}

/** A month's bare name for inline wording ("18 notes saved in Ramadan"): Hijri when [w] says so. */
internal fun monthName(w: ExplorerWords, month: YearMonth): String {
    if (w.hijri) hijriOf(month.atDay(15))?.let { return w.hijriMonth(it.hijriMonthNumber) }
    return w.month(month.month)
}

/** When a document was opened, for Recent: "Opened 25 min ago", "Opened yesterday", "Opened 16 Aug". */
internal fun openedLabel(w: ExplorerWords, time: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val rel = formatWhen(w, time, now, "relative", false, true, zone)
    return w.opened(if (rel.firstOrNull()?.isLetter() == true) rel.replaceFirstChar { it.lowercase() } else rel)
}

/** A file size the way the explorer shows it: 940 KB, 3.4 MB. [digits] shapes the numerals for display. */
internal fun formatSize(bytes: Long, digits: (String) -> String = { it }): String = when {
    bytes < 1000 -> digits("$bytes B")
    bytes < 1000L * 1024 -> digits("${(bytes + 1023) / 1024} KB")
    bytes < 1000L * 1024 * 1024 -> digits("%.1f MB".format(Locale.ROOT, bytes / 1048576.0))
    else -> digits("%.1f GB".format(Locale.ROOT, bytes / 1073741824.0))
}

/** "1 folder · 18 files", leaving out a count that is zero. */
internal fun countsLabel(w: ExplorerWords, folders: Int, files: Int): String = listOfNotNull(
    folders.takeIf { it > 0 }?.let { w.folders(it) },
    files.takeIf { it > 0 }?.let { w.files(it) },
).joinToString(" · ")

/** "1 item" or "24 items". */
internal fun itemsLabel(w: ExplorerWords, n: Int): String = w.items(n)
