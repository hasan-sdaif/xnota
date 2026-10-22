package com.xnota.core.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-note version history: the metadata ring that decides which snapshots of a note are
 * worth keeping. Each entry is a content hash plus when it was taken and how big the note
 * was; the bytes themselves live wherever the caller files them (keyed by the hash).
 *
 * The policy is LRU-with-dedupe: appending a hash that is already the newest refreshes its
 * stamp without a new entry (nothing changed), a hash seen earlier moves back to the
 * newest slot (content came back around), and anything older than [maxEntries] falls off
 * the front. [restore] addresses versions newest-first, index 0 being the latest.
 */
class VersionLog(private val maxEntries: Int = DEFAULT_MAX) {

    /** One remembered snapshot of a note: what it hashed to, when, and how big it was. */
    data class Version(val contentHash: String, val timestamp: Long, val size: Int)

    private val entries = ArrayList<Version>()

    /** How many versions are kept. */
    val count: Int get() = entries.size

    /**
     * Records a snapshot. Returns false when [contentHash] matches the newest version —
     * the note did not change, so only its stamp is refreshed and nothing new is kept.
     */
    fun append(contentHash: String, timestamp: Long, size: Int): Boolean {
        if (size < 0) return false
        if (entries.isNotEmpty() && entries.last().contentHash == contentHash) {
            entries[entries.size - 1] = Version(contentHash, timestamp, size)
            return false
        }
        // LRU: content that came back around reuses its slot at the newest position.
        entries.removeAll { it.contentHash == contentHash }
        entries.add(Version(contentHash, timestamp, size))
        while (entries.size > maxEntries.coerceAtLeast(1)) entries.removeAt(0)
        return true
    }

    /** Every kept version, oldest first. */
    fun list(): List<Version> = ArrayList(entries)

    /** The newest version, or null when none was ever recorded. */
    fun newest(): Version? = entries.lastOrNull()

    /**
     * The version to roll back to: [index] counts back from the newest (0 = newest, 1 the
     * one before, …), so a history list drawn newest-first can pass its row index straight
     * through. Out-of-range asks return null.
     */
    fun restore(index: Int): Version? {
        if (index < 0) return null
        val fromEnd = entries.size - 1 - index
        return entries.getOrNull(fromEnd)
    }

    /** The whole ring as JSON — how it is persisted between sessions. */
    fun toJson(): String {
        val a = JSONArray()
        for (v in entries) a.put(JSONObject().put("hash", v.contentHash).put("ts", v.timestamp).put("size", v.size))
        return a.toString()
    }

    companion object {
        /** How many versions of one note are kept by default. */
        const val DEFAULT_MAX = 20

        /** Reads a persisted ring back; an unreadable or nonsensical one comes back empty. */
        fun fromJson(json: String?, maxEntries: Int = DEFAULT_MAX): VersionLog {
            val log = VersionLog(maxEntries)
            if (json.isNullOrBlank()) return log
            runCatching {
                val a = JSONArray(json)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    log.entries.add(
                        Version(
                            o.optString("hash", ""),
                            o.optLong("ts", 0L).let { if (it in 1..Long.MAX_VALUE) it else 0L },
                            o.optInt("size", 0).let { if (it >= 0) it else 0 },
                        ),
                    )
                }
            }
            while (log.entries.size > maxEntries.coerceAtLeast(1)) log.entries.removeAt(0)
            return log
        }
    }
}
