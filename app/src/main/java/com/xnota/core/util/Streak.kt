package com.xnota.core.util

import java.time.LocalDate

/**
 * The edit streak (the fork): how many days in a row a note was edited, shown beside the
 * storage readout. Pure — the caller hands the set of active days (any form that names a day;
 * the editor uses [DailyWords] key form) and the day to count back from, and takes back the run
 * length. A day with no activity ends the run; today only counts once it has activity, so the
 * streak survives "not written yet today" until the day is over.
 */
object Streak {

    /**
     * The consecutive active days ending at [today] (or at the day before it, when today itself
     * is not active). [days] holds the active days in [DailyWords] key form; [today] is the day
     * the count is seen from.
     */
    fun of(days: Set<String>, today: LocalDate = LocalDate.now()): Int {
        var day = today
        // A streak that ran through yesterday still shows while today has yet to be written.
        if (day.toString() !in days) day = day.minusDays(1)
        var run = 0
        while (day.toString() in days) {
            run++
            day = day.minusDays(1)
        }
        return run
    }
}
