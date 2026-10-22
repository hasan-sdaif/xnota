package com.xnota.core.util

/**
 * Notable dates of the Hijri year (the fork): the Islamic anniversaries an Arabic reader keeps
 * an eye on, keyed by Hijri month and day so a caller can ask "is today one?" without any
 * calendar arithmetic of its own — [com.xnota.ui.ExplorerGroups] turns a Gregorian today into a
 * [java.time.chrono.HijrahDate] and looks it up here. Multi-day festivals (both Eids, the days
 * of Tashreeq) list each day, all naming the festival; the first day stays the canonical one.
 *
 * [Event.key] is a stable token the UI maps to its localized name (see the `islamic_*` strings);
 * nothing in core knows a display name, so the English JVM tests and the Arabic resources stay
 * free to word them as their readers expect.
 */
object IslamicCalendar {

    class Event(val key: String, val month: Int, val day: Int)

    val EVENTS: List<Event> = listOf(
        Event("islamic_new_year", 1, 1),
        Event("islamic_ashura", 1, 10),
        Event("islamic_arbaeen", 2, 20),
        Event("islamic_mawlid", 3, 12),
        Event("islamic_isra_miraj", 7, 27),
        Event("islamic_mid_shaban", 8, 15),
        Event("islamic_ramadan_first", 9, 1),
        Event("islamic_badr", 9, 17),
        Event("islamic_qadr", 9, 27),
        Event("islamic_eid_fitr", 10, 1),
        Event("islamic_eid_fitr", 10, 2),
        Event("islamic_eid_fitr", 10, 3),
        Event("islamic_arafah", 12, 9),
        Event("islamic_eid_adha", 12, 10),
        Event("islamic_eid_adha", 12, 11),
        Event("islamic_eid_adha", 12, 12),
        Event("islamic_eid_adha", 12, 13),
    )

    /** The event Hijri (`month`, `day`) is a day of, or null for an ordinary day. */
    fun eventOf(month: Int, day: Int): Event? =
        EVENTS.firstOrNull { it.month == month && it.day == day }
}
