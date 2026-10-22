package com.xnota.settings

import com.xnota.core.util.Tags
import org.json.JSONArray
import org.json.JSONObject

/**
 * A user-defined saved search (the fork): a name the sidebar lists it under, a query the note's
 * name must contain (case-insensitively, blank = every note) and an optional tag the note must
 * carry. Stored in [Settings.smartFolders] as the JSON array `smart_folders`.
 */
data class SmartFolder(
    val name: String,
    val query: String = "",
    val tag: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("query", query)
        .apply { tag?.let { put("tag", it) } }

    companion object {
        /** Forgiving read: a blank name drops the entry, a blank tag reads as "any tag". */
        fun of(o: JSONObject?): SmartFolder? {
            if (o == null) return null
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) return null
            val tag = o.optString("tag", "").trim().ifEmpty { null }
            return SmartFolder(name, o.optString("query", "").trim(), tag)
        }
    }
}

/**
 * The smart-folder list's JSON codec and its pure matcher. The matcher takes what the explorer
 * already knows about each note — its display name and its tags — so running a smart folder is a
 * filter over a listing, never a second walk of the tree.
 */
object SmartFolders {
    /** How many smart folders are kept; the oldest beyond the cap fall off. */
    const val MAX_FOLDERS = 12

    /** Read the `smart_folders` array; unreadable entries are dropped, never fatal. */
    fun of(a: JSONArray?): List<SmartFolder> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i -> SmartFolder.of(a.optJSONObject(i)) }
    }

    fun toJson(list: List<SmartFolder>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

    /**
     * Save [folder]: one with the same name is replaced in place, a new one is appended, and the
     * list is capped at [MAX_FOLDERS] by dropping the oldest. A blank name is rejected. Pure.
     */
    fun save(list: List<SmartFolder>, folder: SmartFolder): List<SmartFolder> {
        val name = folder.name.trim()
        if (name.isEmpty()) return list
        val named = folder.copy(name = name)
        val next = if (list.any { it.name == name }) list.map { if (it.name == name) named else it } else list + named
        return if (next.size > MAX_FOLDERS) next.drop(next.size - MAX_FOLDERS) else next
    }

    /** Remove the folder named [name]. Pure. */
    fun delete(list: List<SmartFolder>, name: String): List<SmartFolder> = list.filterNot { it.name == name }

    /**
     * Whether a note called [name] carrying [tags] lands in [folder]: the query (blank = any
     * name) is contained case-insensitively in the name, and the tag (null/blank = any tag) is
     * among the note's tags, case-insensitively. Pure.
     */
    fun matches(folder: SmartFolder, name: String, tags: List<String>): Boolean {
        val query = folder.query.trim()
        if (query.isNotEmpty() && !name.contains(query, ignoreCase = true)) return false
        val tag = folder.tag?.trim()?.ifEmpty { null } ?: return true
        return tags.any { it.equals(tag, ignoreCase = true) }
    }

    /** The distinct tags of [entries], for the smart-folder dialog's tag suggestions. */
    fun tagOptions(tagsOf: List<List<String>>): List<String> =
        Tags.normalize(tagsOf.flatten()).sortedBy { it.lowercase() }
}
