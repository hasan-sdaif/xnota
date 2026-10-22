package com.xnota.core.text

/**
 * List-marker semantics for Enter's auto-continue (the fork): which of the plain-text markers a
 * line may start with, the marker the next line takes, and how an ordered marker's number steps
 * up or down. Pure, so the rules the controller applies and the rules the tests pin are one and
 * the same. The model's own list continuation is the paragraph's [ListKind] property (inherited
 * by the split); this file is the plain-text view of the same idea, for gating and for markers a
 * user typed without markdown conversion.
 */
object ListMarkers {

    /** The kind of marker a line starts with, if any. */
    enum class Kind { BULLET, ORDERED, CHECK }

    /** A marker found at the start of a line: its kind and its full text (marker plus the space). */
    class Marker(val kind: Kind, val text: String) {
        override fun equals(other: Any?): Boolean = other is Marker && other.kind == kind && other.text == text
        override fun hashCode(): Int = kind.hashCode() * 31 + text.hashCode()
        override fun toString(): String = "Marker($kind, \"$text\")"
    }

    private val BULLETS = listOf("- ", "* ", "+ ", "• ", "– ")
    private val ORDERED = Regex("^(\\d{1,9})[.)] $")
    private val CHECK = Regex("^\\[( |x|X)] $")
    private val ORDERED_PREFIX = Regex("^(\\d{1,9}[.)] )")
    private val CHECK_PREFIX = Regex("^\\[( |x|X)] ")

    /**
     * The marker [line] begins with, or null. The marker must be the whole prefix up to and
     * including its trailing space, so "- " alone is an empty item (the marker a strip-on-Enter
     * removes) and "-word" is not a list at all.
     */
    fun detect(line: String): Marker? {
        CHECK.matchEntire(line)?.let { return Marker(Kind.CHECK, line) }
        ORDERED.matchEntire(line)?.let { return Marker(Kind.ORDERED, line) }
        BULLETS.firstOrNull { line == it }?.let { return Marker(Kind.BULLET, line) }
        return null
    }

    /**
     * The marker the line after [marker] takes: bullets and checkboxes repeat themselves (a
     * checkbox continues unchecked), an ordered marker's number steps up — "3. " is followed by
     * "4. ".
     */
    fun next(marker: Marker): String = when (marker.kind) {
        Kind.BULLET -> marker.text
        Kind.CHECK -> "[ ] "
        Kind.ORDERED -> {
            val number = marker.text.takeWhile { it.isDigit() }
            val sep = if (marker.text.contains(')')) ")" else "."
            "${increment(number)}$sep "
        }
    }

    /** A plain line with the marker [marker] removed from its start. */
    fun strip(line: String, marker: Marker): String =
        if (line.startsWith(marker.text)) line.drop(marker.text.length) else line

    /**
     * The number of an ordered marker as a string, stepped by [delta] without dropping below one
     * ("1. " decremented stays "1. "). A non-numeric string comes back unchanged.
     */
    fun step(number: String, delta: Int): String {
        val n = number.toIntOrNull() ?: return number
        return (n + delta).coerceAtLeast(1).toString()
    }

    /**
     * What Enter at the end of [line] should land (the fork's auto-continue): the next line's
     * marker ("2. " after "1. "), the empty string when [line] is a bare marker (Enter drops it
     * and stays put), or null when [line] starts no marker (a plain Enter). The marker must be
     * typed text, not a model list property — the model's own continuation is the paragraph's
     * [com.xnota.core.text.ListKind] split.
     */
    fun continuation(line: String): String? {
        BULLETS.firstOrNull { line.startsWith(it) }?.let { return if (line == it) "" else it }
        ORDERED_PREFIX.find(line)?.let { m ->
            if (line == m.value) return ""
            return detect(m.value)?.let(::next)
        }
        CHECK_PREFIX.find(line)?.let { m ->
            if (line == m.value) return ""
            return "[ ] "
        }
        return null
    }

    private fun increment(number: String): String = step(number, 1)
}
