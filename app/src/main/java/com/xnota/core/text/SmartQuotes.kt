package com.xnota.core.text

/**
 * Smart quotes (the fork): the straight quotes a keyboard sends become real typographic ones as
 * they land. Latin typing gets curly “ ” and ‘ ’; Arabic gets « », the marks an Arabic reader
 * expects, where the Latin guillemets would be. Pure — the controller hands in the typed run and
 * the character that sits before it, and takes back what to insert instead.
 */
object SmartQuotes {

    /** The quotes [typed] should land as, given the character that sits at the insertion point. */
    fun transform(typed: String, precededBy: Char?, arabic: Boolean): String {
        if (typed.isEmpty()) return typed
        var prev = precededBy
        val out = StringBuilder(typed.length)
        for (c in typed) {
            val q = when (c) {
                '"' -> {
                    val open = opensAfter(prev)
                    if (arabic) if (open) '«' else '»' else if (open) '“' else '”'
                }
                '\'' -> if (arabic) c else if (opensAfter(prev)) '‘' else '’'
                else -> c
            }
            out.append(q)
            prev = q
        }
        return out.toString()
    }

    /**
     * A straight quote opens after nothing, whitespace or punctuation; it closes after writing or
     * after another quote (a pair right beside its opener is the empty “” a double tap types).
     */
    private fun opensAfter(prev: Char?): Boolean = when {
        prev == null -> true
        prev.isWhitespace() -> true
        Character.isLetterOrDigit(prev) -> false
        isQuote(prev) -> false
        else -> true
    }

    private fun isQuote(c: Char): Boolean =
        c == '“' || c == '”' || c == '‘' || c == '’' || c == '«' || c == '»' || c == '"' || c == '\''
}
