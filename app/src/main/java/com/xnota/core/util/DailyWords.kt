package com.xnota.core.util

import java.time.LocalDate

/**
 * The daily word tally (the fork): how many words were written each day, for the explorer's
 * word-goal chip. Pure — the editor hands the persisted map and takes back the map to keep. Days
 * are [LocalDate] keys in ISO form ("2026-09-16"); [CAP] days at most, oldest first, so a long
 * history stays small. A day's words only ever add.
 */
object DailyWords {

    /** The map's hard day cap. */
    const val CAP = 90

    /** [date] as the map's key form, "2026-09-16". */
    fun keyOf(date: LocalDate): String = date.toString()

    /** Today's key (the caller's zone already chose the date). */
    fun keyOfToday(now: LocalDate = LocalDate.now()): String = now.toString()

    /** Words counted on [dayKey], 0 when the day has no entry. */
    fun words(map: Map<String, Long>, dayKey: String): Long = map[dayKey] ?: 0L

    /**
     * The map with [added] more words on [dayKey]: a missing day starts at [added], an existing
     * one adds to it (never below zero), and the oldest days fall away past [CAP] entries.
     */
    fun add(map: Map<String, Long>, dayKey: String, added: Int): Map<String, Long> {
        if (added == 0 && dayKey in map) return map
        val out = LinkedHashMap<String, Long>(map.size + 1)
        // A new day lands at the most-recent end; days already in order keep it.
        map.forEach { (k, v) -> if (k != dayKey) out[k] = v }
        out[dayKey] = ((map[dayKey] ?: 0L) + added).coerceAtLeast(0L)
        while (out.size > CAP) out.remove(out.keys.first())
        return out
    }

    /** The map pruned to at most [CAP] of its newest days (by key order). */
    fun pruned(map: Map<String, Long>): Map<String, Long> =
        if (map.size <= CAP) map else map.entries.sortedBy { it.key }.takeLast(CAP).associate { it.toPair() }
}
