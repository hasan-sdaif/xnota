package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test fun parseSplitsOnCommasSemicolonsAndWhitespace() {
        assertEquals(listOf("a", "b", "c", "d"), Tags.parse("a, b;c   d"))
    }

    @Test fun parseTrimsAndDropsBlanks() {
        assertEquals(listOf("x"), Tags.parse(" , x ,  "))
        assertEquals(emptyList<String>(), Tags.parse("   ,  "))
    }

    @Test fun parseFoldsCaseInsensitiveDuplicatesKeepingTheFirstSpelling() {
        assertEquals(listOf("Work", "DRAFT"), Tags.parse("Work, work, DRAFT, draft"))
    }

    @Test fun parseCapsEachTagLengthAndTheTagCount() {
        val long = "x".repeat(80)
        assertEquals(Tags.MAX_TAG_LENGTH, Tags.parse(long).single().length)
        val many = (1..40).joinToString(",") { "t$it" }
        assertEquals(Tags.MAX_TAGS, Tags.parse(many).size)
    }

    @Test fun joinMakesACommaSeparatedLine() {
        assertEquals("a, b", Tags.join(listOf("a", "b")))
        assertEquals("", Tags.join(emptyList()))
    }

    @Test fun normalizeDedupesCaseInsensitively() {
        assertEquals(listOf("One", "two"), Tags.normalize(listOf(" One ", "two", "ONE", "  ")))
    }
}
