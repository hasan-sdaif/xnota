package com.xnota.core.util

/**
 * A note's tags (the fork): short labels the explorer can filter and group by, typed free-form as
 * comma- or space-separated words and stored in the document's own JSON as the optional `tags`
 * array (so they travel with the file, the same way the colour codes don't). Pure, so the parsing
 * rules are unit-testable.
 */
object Tags {
    /** How many tags one note keeps; the rest of a long paste fall off. */
    const val MAX_TAGS = 24

    /** How long one tag may run; a longer word is kept whole but capped. */
    const val MAX_TAG_LENGTH = 48

    /**
     * The tags a user typed into one field: split on commas, semicolons or whitespace, trimmed,
     * blanks dropped, duplicates folded case-insensitively (the first spelling wins) and the list
     * capped at [MAX_TAGS]. Order is the order they were typed.
     */
    fun parse(raw: String): List<String> = normalize(raw.split(',', ';', ' ', '\t', '\n'))

    /**
     * A tag list as one comma-separated line, for the edit dialog's field. Empty tags give "".
     */
    fun join(tags: List<String>): String = tags.joinToString(", ")

    /**
     * Any list of tags made well-formed: trimmed, blanks dropped, case-insensitive duplicates
     * folded (first spelling wins), each capped at [MAX_TAG_LENGTH] and the list at [MAX_TAGS].
     */
    fun normalize(list: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (raw in list) {
            val tag = raw.trim().take(MAX_TAG_LENGTH)
            if (tag.isEmpty()) continue
            if (seen.add(tag.lowercase())) out.add(tag)
            if (out.size == MAX_TAGS) break
        }
        return out
    }
}
