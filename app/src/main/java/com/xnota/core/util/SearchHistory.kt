package com.xnota.core.util

/**
 * The search tab's remembered queries (the fork): the last [CAP] distinct queries, newest first —
 * a most-recently-used list. Searching a query again moves it back to the front instead of
 * duplicating it, and the oldest beyond the cap falls off. Pure, so the contract is testable.
 */
object SearchHistory {

    /** How many queries the tab offers back as chips. */
    const val CAP = 8

    /** [history] with [query] just searched: trimmed, deduped, newest first, capped at [CAP]. */
    fun record(history: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return history
        val out = ArrayList<String>(CAP + 1)
        out.add(q)
        for (s in history) if (s != q && out.size < CAP) out.add(s)
        return out
    }

    /** A stored list read back: trimmed, blanks and duplicates dropped, order kept, capped. */
    fun sanitize(history: List<String>): List<String> {
        val out = ArrayList<String>(CAP)
        for (s in history) {
            val t = s.trim()
            if (t.isEmpty() || t in out) continue
            if (out.size >= CAP) break
            out.add(t)
        }
        return out
    }
}
