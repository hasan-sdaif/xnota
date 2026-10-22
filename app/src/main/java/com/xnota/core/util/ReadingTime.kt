package com.xnota.core.util

import java.util.Locale

/**
 * The reading-time tally (the fork): how many seconds each note has spent open, kept per note
 * and shown in the note's stats. Pure — the editor hands the persisted map and takes back the
 * map to keep, so the cap the tests pin is the one the device runs. [CAP] notes at most; the
 * least recently stored falls off, and storing an existing note refreshes it.
 */
object ReadingTime {

    /** The map's hard entry cap. */
    const val CAP = 500

    /**
     * The map with [seconds] more on [uri]: an existing entry adds to its count and moves to the
     * most-recent end, and the least recent entry drops once [CAP] is exceeded. Negative seconds
     * never land.
     */
    fun add(map: Map<String, Long>, uri: String, seconds: Long): Map<String, Long> {
        if (seconds <= 0 && uri in map) return map
        val kept = map.filterKeys { it != uri }
        val out = LinkedHashMap<String, Long>(kept.size + 1)
        kept.forEach { (k, v) -> out[k] = v }
        out[uri] = (map[uri] ?: 0L) + seconds.coerceAtLeast(0L)
        while (out.size > CAP) out.remove(out.keys.first())
        return out
    }

    /** Seconds [uri] has been read for, 0 when it has never been open. */
    fun seconds(map: Map<String, Long>, uri: String): Long = map[uri] ?: 0L

    /**
     * [seconds] as the compact wording the stats row shows: "45m", "2h 05m"; under a minute,
     * just the seconds. [digits] shapes the numerals for display.
     */
    fun label(seconds: Long, digits: (String) -> String = { it }): String {
        val s = seconds.coerceAtLeast(0L)
        if (s < 60) return digits("$s") + "s"
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) {
            digits("$h") + "h " + digits(String.format(Locale.ROOT, "%02d", m)) + "m"
        } else {
            digits("$m") + "m"
        }
    }
}
