package com.xnota.core.text

/**
 * Find &amp; replace over plain text (the fork): where every match sits in the ORIGINAL string and
 * what replacing them leaves behind. Pure, so the flow editor's find bar and any other caller share
 * exactly one set of rules.
 *
 * Matching runs over a normalized view of the haystack — case folded (unless it matters), and with
 * the fork's loose Arabic matching when diacritics are ignored: tashkeel (U+064B–U+0652) stepped
 * over, the hamza-carrying alefs (أ إ آ ٱ) and the ta-marbuta (ة) folded to their bare partners
 * (ا, ه), Arabic-Indic numerals (٠–٩) folded to Latin — while the results keep the original
 * string's indices, so a caller can select or replace without re-deriving anything. The same
 * normalization is what the note search folds characters through ([com.xnota.core.search.SearchFold]
 * folds the same marks and letters), so a search that ignores diacritics there and a find bar that
 * ignores them here agree on what matches.
 */
object FindReplace {

    /** One match: the half-open range [start, end) in the original (un-normalized) string. */
    class Match(val start: Int, val end: Int) {
        override fun equals(other: Any?): Boolean = other is Match && other.start == start && other.end == end
        override fun hashCode(): Int = start * 31 + end
        override fun toString(): String = "Match($start..$end)"
    }

    /**
     * Every match of [needle] in [haystack], in order, as ranges of [haystack] itself. A blank
     * needle matches nothing. [wholeWord] drops matches that touch a letter or digit; with
     * [ignoreDiacritics] the loose Arabic folds apply (see the object doc), which never breaks the
     * ranges because a folded pair is one char mapped to one char and a mark skipped by both
     * sides is not a character mapped away.
     */
    fun findAll(
        haystack: String,
        needle: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        ignoreDiacritics: Boolean = false,
    ): List<Match> {
        if (needle.isEmpty()) return emptyList()
        val hay = normalize(haystack, caseSensitive, ignoreDiacritics)
        val pat = normalize(needle, caseSensitive, ignoreDiacritics)
        if (pat.text.isEmpty() || hay.text.isEmpty() || pat.text.length > hay.text.length) return emptyList()
        val out = ArrayList<Match>()
        var at = 0
        while (at <= hay.text.length - pat.text.length) {
            val found = hay.text.indexOf(pat.text, at)
            if (found < 0) break
            // Word edges are judged on the normalized text, so a mark the matching stepped over is
            // not a word character the edge check trips on either.
            if (!wholeWord || isWordEdge(hay.text, found, found + pat.text.length)) {
                out.add(Match(hay.index[found], hay.index[found + pat.text.length]))
            }
            // Advance past this match's start so overlapping occurrences are found from the next
            // position on — replaceAll relies on the same stepping to be overlap-safe.
            at = found + 1
        }
        return out
    }

    /**
     * [haystack] with every match of [needle] replaced by [replacement]. Advancing past each
     * replaced range means a replacement that itself contains the needle is never re-replaced, and
     * overlapping candidates resolve to the leftmost span, the way every editor does it.
     */
    fun replaceAll(
        haystack: String,
        needle: String,
        replacement: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        ignoreDiacritics: Boolean = false,
    ): String {
        val matches = findAll(haystack, needle, caseSensitive, wholeWord, ignoreDiacritics)
        if (matches.isEmpty()) return haystack
        val out = StringBuilder(haystack.length + matches.size * replacement.length)
        var at = 0
        for (m in matches) {
            if (m.start < at) continue // inside the span an earlier replacement already covered
            out.append(haystack, at, m.start)
            out.append(replacement)
            at = m.end
        }
        out.append(haystack, at, haystack.length)
        return out.toString()
    }

    /** A normalized string plus, for each of its characters, the index it came from in the source. */
    private class Folded(val text: String, val index: IntArray)

    /** Case-fold [s], and apply the loose Arabic folds when the caller wants them. */
    private fun normalize(s: String, caseSensitive: Boolean, ignoreDiacritics: Boolean): Folded {
        if (caseSensitive && !ignoreDiacritics) return Folded(s, IntArray(s.length + 1) { it })
        val text = StringBuilder(s.length)
        val index = IntArray(s.length + 1)
        for (i in s.indices) {
            val c = if (ignoreDiacritics) looseFold(s[i]) else s[i]
            if (ignoreDiacritics && isTashkeel(c)) continue
            text.append(if (caseSensitive) c else Character.toLowerCase(c))
            index[text.length - 1] = i
        }
        // One past the end maps to one past the source's end, so the last match's end rounds off.
        index[text.length] = s.length
        return Folded(text.toString(), index)
    }

    /** The loose Arabic folds: hamza-carrying alefs and ta-marbuta to their bare partners,
     *  Arabic-Indic (and extended) numerals to Latin — the spelling variants one note writes one
     *  way and the next another. Mirrors [com.xnota.core.search.SearchFold]. */
    private fun looseFold(c: Char): Char = when (c) {
        '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627' // أ إ آ ٱ -> ا
        '\u0629' -> '\u0647'                           // ة -> ه
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')  // ٠-٩ -> 0-9
        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')  // ۰-۹ -> 0-9
        else -> c
    }

    /** The Arabic vowel marks U+064B–U+0652 (fatha, damma, kasra and friends). */
    private fun isTashkeel(c: Char): Boolean = c.code in 0x064B..0x0652

    /** Whether [from, to) has a non-word character (or nothing) on both of its sides. */
    private fun isWordEdge(s: String, from: Int, to: Int): Boolean {
        if (from > 0 && isWordChar(s[from - 1])) return false
        if (to < s.length && isWordChar(s[to])) return false
        return true
    }

    /** A letter, digit or combining mark — what a whole word may not touch. */
    private fun isWordChar(c: Char): Boolean {
        if (Character.isLetterOrDigit(c)) return true
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }
}
