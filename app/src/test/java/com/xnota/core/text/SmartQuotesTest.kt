package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartQuotesTest {

    private fun typed(run: String, before: Char? = null) = SmartQuotes.transform(run, before, arabic = false)

    @Test fun doubleQuotesOpenAfterNothingAndSpace() {
        assertEquals("“", typed("\"", null))
        assertEquals("“", typed("\"", ' '))
        assertEquals("“", typed("\"", '('))
        assertEquals("“", typed("\"", '—'))
    }

    @Test fun doubleQuotesCloseAfterWriting() {
        assertEquals("”", typed("\"", 'd'))
        assertEquals("”", typed("\"", '5'))
    }

    @Test fun singleQuotesFollowTheSameRule() {
        assertEquals("‘", typed("'", null))
        assertEquals("‘", typed("'", '('))
        assertEquals("’", typed("'", 's'))
    }

    @Test fun quotesInsideOneTypedRunPairUp() {
        // Typing both quotes in one burst: the opener makes the next one close.
        assertEquals("“”", typed("\"\""))
        assertEquals("‘’", typed("''"))
    }

    @Test fun aQuoteAfterAClosingQuoteOpens() {
        assertEquals("” “", typed("\" \"", 'x'))
    }

    @Test fun textAroundTheQuotesPassesThrough() {
        assertEquals("a “b” c", typed("a \"b\" c", null))
    }

    @Test fun apostrophesAfterLettersStayClosers() {
        assertEquals("’", typed("'", 'n'))
    }

    @Test fun arabicGetsGuillemetsForDoubles() {
        assertEquals("«", SmartQuotes.transform("\"", null, arabic = true))
        assertEquals("»", SmartQuotes.transform("\"", 'ب', arabic = true))
        assertEquals("كتاب»", SmartQuotes.transform("كتاب\"", '«', arabic = true))
    }

    @Test fun arabicLeavesSingleQuotesAlone() {
        // A single straight quote in Arabic typing is an apostrophe or nothing familiar: keep it.
        assertEquals("'", SmartQuotes.transform("'", 'ا', arabic = true))
    }

    @Test fun emptyRunComesBackEmpty() {
        assertEquals("", typed(""))
    }

    @Test fun nonQuoteTextIsUntouched() {
        assertEquals("plain text 123", typed("plain text 123", 'x'))
    }
}
