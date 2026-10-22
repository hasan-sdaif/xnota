package com.xnota.ui

import com.xnota.core.model.Rgba
import com.xnota.settings.ExplorerSortKey
import com.xnota.settings.GroupBy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExplorerGroupsTest {

    private val zone = ZoneOffset.UTC

    // Friday 18 September 2026, noon.
    private val now = at(2026, 9, 18, 12, 0)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(zone).toEpochMilli()

    private fun file(name: String, modified: Long, created: Long = modified, color: Rgba? = null, dir: Boolean = false) =
        BrowseEntry(name, "content://t/$name", dir, modified = modified, created = created, color = color)

    private fun bucket(time: Long) = dateBucket(EnglishWords, time, now, zone).label

    @Test
    fun `dates fall under the headings the design names`() {
        assertEquals("Today", bucket(at(2026, 9, 18, 8)))
        assertEquals("Today", bucket(at(2026, 9, 19)))
        assertEquals("Yesterday", bucket(at(2026, 9, 17)))
        assertEquals("Earlier this week", bucket(at(2026, 9, 14)))
        assertEquals("Last week", bucket(at(2026, 9, 13)))
        assertEquals("Last week", bucket(at(2026, 9, 7)))
        assertEquals("Earlier in September", bucket(at(2026, 9, 6)))
        assertEquals("August", bucket(at(2026, 8, 20)))
        assertEquals("December 2025", bucket(at(2025, 12, 1)))
    }

    @Test
    fun `a week that starts on Sunday moves the boundary`() {
        assertEquals("Earlier this week", dateBucket(EnglishWords, at(2026, 9, 13), now, zone, java.time.DayOfWeek.SUNDAY).label)
    }

    private fun group(items: List<BrowseEntry>, by: GroupBy, key: ExplorerSortKey = ExplorerSortKey.MODIFIED, descending: Boolean = true) =
        groupEntries(EnglishWords, items, by, key, descending, { if (it.isDir) EntryKind.FOLDER else EntryKind.NOTE }, { null }, now, zone)

    @Test
    fun `date headings run newest first and keep each list's order`() {
        val items = listOf(
            file("a", at(2026, 9, 18, 9)),
            file("b", at(2026, 9, 18, 8)),
            file("c", at(2026, 9, 17)),
            file("d", at(2026, 8, 3)),
        )
        val groups = group(items, GroupBy.DATE)
        assertEquals(listOf("Today", "Yesterday", "August"), groups.map { it.label })
        assertEquals(listOf("a", "b"), groups[0].items.map { it.name })
    }

    @Test
    fun `sorting by date oldest first turns the headings round too`() {
        val items = listOf(file("d", at(2026, 8, 3)), file("c", at(2026, 9, 17)), file("a", at(2026, 9, 18)))
        assertEquals(listOf("August", "Yesterday", "Today"), group(items, GroupBy.DATE, descending = false).map { it.label })
        assertEquals(listOf("Today", "Yesterday", "August"), group(items, GroupBy.DATE, ExplorerSortKey.NAME, descending = false).map { it.label })
    }

    @Test
    fun `sorting by created groups by the created date`() {
        val items = listOf(file("a", modified = at(2026, 9, 18), created = at(2026, 8, 1)))
        assertEquals(listOf("August"), group(items, GroupBy.DATE, ExplorerSortKey.CREATED).map { it.label })
    }

    @Test
    fun `kind headings keep a fixed order`() {
        val items = listOf(file("n", now), file("f", now, dir = true))
        assertEquals(listOf("Folders", "Notes"), group(items, GroupBy.KIND).map { it.label })
    }

    @Test
    fun `named colours come first by name, then unnamed ones by hue, then the uncoloured`() {
        val red = Rgba(220, 40, 40)
        val blue = Rgba(40, 90, 220)
        val green = Rgba(40, 200, 80)
        val items = listOf(file("x", now), file("r", now, color = red), file("b", now, color = blue), file("g", now, color = green))
        val names = mapOf(blue to "Courses", red to "Important")
        val groups = groupEntries(EnglishWords, items, GroupBy.COLOUR, ExplorerSortKey.MODIFIED, true, { EntryKind.NOTE }, { names[it] }, now, zone)
        assertEquals(listOf("Courses", "Important", "Green", "No colour"), groups.map { it.label })
        assertEquals(green, groups[2].color)
    }

    @Test
    fun `no grouping keeps one headless group`() {
        val groups = group(listOf(file("a", now)), GroupBy.NONE)
        assertEquals(listOf(""), groups.map { it.label })
        assertEquals(emptyList<EntryGroup>(), group(emptyList(), GroupBy.DATE))
    }

    @Test
    fun `the day style names recent days and dates the rest`() {
        fun day(t: Long, time: Boolean = true) = formatWhen(EnglishWords, t, now, "day", time, clock24 = true, zone = zone)
        assertEquals("Today 09:12", day(at(2026, 9, 18, 9, 12)))
        assertEquals("Today", day(at(2026, 9, 18, 9, 12), time = false))
        assertEquals("Yesterday 16:05", day(at(2026, 9, 17, 16, 5)))
        assertEquals("Tue 17:30", day(at(2026, 9, 15, 17, 30)))
        assertEquals("11 Sep", day(at(2026, 9, 11)))
        assertEquals("16 Aug 2025", day(at(2025, 8, 16)))
        assertEquals("Today 9:12 AM", formatWhen(EnglishWords, at(2026, 9, 18, 9, 12), now, "day", true, clock24 = false, zone = zone))
        assertEquals("Yesterday 5:30 PM", formatWhen(EnglishWords, at(2026, 9, 17, 17, 30), now, "day", true, clock24 = false, zone = zone))
    }

    @Test
    fun `the relative and date styles`() {
        fun rel(t: Long) = formatWhen(EnglishWords, t, now, "relative", true, clock24 = true, zone = zone)
        assertEquals("Just now", rel(now - 20_000))
        assertEquals("25 min ago", rel(now - 25 * 60_000))
        assertEquals("3 h ago", rel(at(2026, 9, 18, 9)))
        assertEquals("Yesterday", rel(at(2026, 9, 17)))
        assertEquals("3 days ago", rel(at(2026, 9, 15)))
        assertEquals("16 Aug", rel(at(2026, 8, 16)))
        assertEquals("16 Sep 2026, 09:12", formatWhen(EnglishWords, at(2026, 9, 16, 9, 12), now, "date", true, clock24 = true, zone = zone))
        assertEquals("16 Sep 2026", formatWhen(EnglishWords, at(2026, 9, 16, 9, 12), now, "date", false, clock24 = true, zone = zone))
        assertEquals("", formatWhen(EnglishWords, 0, now, "day", true, clock24 = true, zone = zone))
        assertEquals("16 Sep 2026, 09:12", formatFull(EnglishWords, at(2026, 9, 16, 9, 12), clock24 = true, zone = zone))
    }

    @Test
    fun `recent says when a note was opened`() {
        assertEquals("Opened 25 min ago", openedLabel(EnglishWords, now - 25 * 60_000, now, zone))
        assertEquals("Opened just now", openedLabel(EnglishWords, now - 5_000, now, zone))
        assertEquals("Opened yesterday", openedLabel(EnglishWords, at(2026, 9, 17), now, zone))
        assertEquals("Opened 16 Aug", openedLabel(EnglishWords, at(2026, 8, 16), now, zone))
    }

    @Test
    fun `sizes and counts read the way the design writes them`() {
        assertEquals("500 B", formatSize(500))
        assertEquals("1 KB", formatSize(1000))
        assertEquals("24 KB", formatSize(24 * 1024))
        assertEquals("940 KB", formatSize(940 * 1024))
        assertEquals("3.4 MB", formatSize(3480L * 1024))
        assertEquals("5 folders · 18 files", countsLabel(EnglishWords, 5, 18))
        assertEquals("1 file", countsLabel(EnglishWords, 0, 1))
        assertEquals("1 item", itemsLabel(EnglishWords, 1))
        assertEquals("24 items", itemsLabel(EnglishWords, 24))
    }

    @Test
    fun `the hijri calendar writes the dates the umm al-qura tables give`() {
        // Umm al-Qura: 16 Sep 2026 = 5 Rabi' II 1448; the "now" above (18 Sep 2026) = 7 Rabi' II 1448.
        assertEquals("5 Rabi' II 1448", formatWhen(HijriEnglishWords, at(2026, 9, 16, 9, 12), now, "date", false, true, zone))
        assertEquals("5 Rabi' II 1448, 09:12", formatWhen(HijriEnglishWords, at(2026, 9, 16, 9, 12), now, "date", true, true, zone))
        assertEquals("5 Rabi' II 1448, 09:12", formatFull(HijriEnglishWords, at(2026, 9, 16, 9, 12), true, zone))
        // Beyond a week back the day style writes the day-month date: 20 Aug 2026 = 7 Rabi' I 1448.
        assertEquals("7 Rabi' I", formatWhen(HijriEnglishWords, at(2026, 8, 20), now, "day", false, true, zone))
        // Day-relative wording stays day-relative, exactly as with the Gregorian calendar.
        assertEquals("3 days ago", formatWhen(HijriEnglishWords, at(2026, 9, 15), now, "relative", false, true, zone))
        assertEquals("Yesterday", formatWhen(HijriEnglishWords, at(2026, 9, 17), now, "day", false, true, zone))
    }

    @Test
    fun `hijri month buckets head by hijri month`() {
        // 6 Sep 2026 = 24 Rabi' I 1448 — this Hijri year, but another Hijri month than today's.
        assertEquals("Rabi' I", dateBucket(HijriEnglishWords, at(2026, 9, 6), now, zone).label)
        // 1 Dec 2025 = 10 Jumada II 1447 — another Hijri year altogether.
        assertEquals("Jumada II 1447", dateBucket(HijriEnglishWords, at(2025, 12, 1), now, zone).label)
    }

    @Test
    fun `dates outside the hijri range fall back to the gregorian wording`() {
        // 2200 lies past the Hijrah chronology's coverage (1600 AH ≈ Nov 2174): the wording stays Gregorian.
        assertEquals("1 Jan 2200", formatWhen(HijriEnglishWords, at(2200, 1, 1), now, "date", false, true, zone))
        assertEquals(
            formatWhen(EnglishWords, at(2200, 1, 1), now, "date", false, true, zone),
            formatWhen(HijriEnglishWords, at(2200, 1, 1), now, "date", false, true, zone),
        )
    }

    @Test
    fun `timeline month headings follow the calendar`() {
        val today = java.time.LocalDate.of(2026, 9, 18)
        assertEquals("September", monthHeading(EnglishWords, java.time.YearMonth.of(2026, 9), today))
        assertEquals("September 2025", monthHeading(EnglishWords, java.time.YearMonth.of(2025, 9), today))
        // Sep 2026 straddles Rabi' I/II 1448; the mid-month sample lands in Rabi' II, this Hijri year.
        assertEquals("Rabi' II", monthHeading(HijriEnglishWords, java.time.YearMonth.of(2026, 9), today))
        // Dec 2025 = Jumada II 1447, a Hijri year away from today's 1448.
        assertEquals("Jumada II 1447", monthHeading(HijriEnglishWords, java.time.YearMonth.of(2025, 12), today))
        assertEquals("Sha'ban", monthName(HijriEnglishWords, java.time.YearMonth.of(2026, 2)))
        assertEquals("February", monthName(EnglishWords, java.time.YearMonth.of(2026, 2)))
    }

    @Test
    fun `unnamed colours are called by their hue`() {
        assertEquals("Red", hueName(EnglishWords, Rgba(230, 40, 40)))
        assertEquals("Orange", hueName(EnglishWords, Rgba(242, 166, 90)))
        assertEquals("Blue", hueName(EnglishWords, Rgba(116, 169, 245)))
        assertEquals("Teal", hueName(EnglishWords, Rgba(95, 211, 200)))
        assertEquals("Purple", hueName(EnglishWords, Rgba(160, 100, 240)))
        assertEquals("Grey", hueName(EnglishWords, Rgba(128, 128, 128)))
        assertEquals("Black", hueName(EnglishWords, Rgba(10, 10, 10)))
    }

    @Test
    fun `created sorts by the created date, and folders can mix in`() {
        val a = file("a", modified = 5, created = 1)
        val b = file("b", modified = 1, created = 5)
        val d = file("d", modified = 3, created = 3, dir = true)
        val cmp = explorerComparator(ExplorerSortKey.CREATED, descending = true, foldersFirst = false) { it.created }
        assertEquals(listOf("b", "d", "a"), listOf(a, b, d).sortedWith(cmp).map { it.name })
        val top = explorerComparator(ExplorerSortKey.CREATED, descending = true) { it.created }
        assertEquals(listOf("d", "b", "a"), listOf(a, b, d).sortedWith(top).map { it.name })
    }
}
