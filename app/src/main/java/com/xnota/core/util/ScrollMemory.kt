package com.xnota.core.util

/**
 * Per-note scroll memory (the fork): where each note was last scrolled to, so opening it returns
 * to the same place. Pure — the editor hands the map it persists and takes back the map to keep,
 * so the LRU the tests pin is the one the device runs. [CAP] entries at most; the least recently
 * touched (opened or stored) falls off the end, and storing an existing key refreshes it.
 */
object ScrollMemory {

    /** The map's hard entry cap. */
    const val CAP = 200

    /**
     * [uri]'s remembered offset, or null when the note has none (first open). A pure read — the
     * entry's position only refreshes when a later store lands (a closing note always stores).
     */
    fun restore(map: Map<String, LongArray>, uri: String): LongArray? =
        map[uri]?.copyOfRange(0, 2)

    /**
     * The map with [uri] at ([x], [y]): an existing entry moves to the most-recent end, and the
     * least recent entry drops once [CAP] is exceeded. [x]/[y] are the content-space scroll
     * offsets the canvas reports, rounded to whole numbers.
     */
    fun store(map: Map<String, LongArray>, uri: String, x: Double, y: Double): Map<String, LongArray> {
        val kept = map.filterKeys { it != uri }
        val out = LinkedHashMap<String, LongArray>(kept.size + 1)
        kept.forEach { (k, v) -> out[k] = v }
        out[uri] = longArrayOf(Math.round(x), Math.round(y))
        while (out.size > CAP) out.remove(out.keys.first())
        return out
    }

    /** The map without [uri]'s entry (a deleted note leaves no residue). */
    fun drop(map: Map<String, LongArray>, uri: String): Map<String, LongArray> =
        if (uri !in map) map else map.filterKeys { it != uri }
}
