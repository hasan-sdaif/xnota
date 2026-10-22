package com.xnota.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollMemoryTest {

    @Test fun anUnknownNoteHasNothingToRestore() {
        assertNull(ScrollMemory.restore(emptyMap(), "a"))
    }

    @Test fun storingThenRestoringHandsBackTheOffset() {
        val m = ScrollMemory.store(emptyMap(), "a", 12.4, 300.6)
        assertArrayEquals(longArrayOf(12, 301), ScrollMemory.restore(m, "a"))
    }

    @Test fun restorerHandsBackACopySoTheCallerCannotMutateTheMap() {
        val m = ScrollMemory.store(emptyMap(), "a", 1.0, 2.0)
        val got = ScrollMemory.restore(m, "a")!!
        got[0] = 99
        assertArrayEquals(longArrayOf(1, 2), ScrollMemory.restore(m, "a"))
    }

    @Test fun storingAgainRefreshesANotesPosition() {
        var m = emptyMap<String, LongArray>()
        for (k in listOf("a", "b", "c")) m = ScrollMemory.store(m, k, 0.0, 0.0)
        // Re-storing "a" makes it the most recently kept entry.
        m = ScrollMemory.store(m, "a", 5.0, 5.0)
        assertArrayEquals(longArrayOf(5, 5), ScrollMemory.restore(m, "a"))
        // Fill to the cap; the next store evicts "b" (least recently stored), not "a".
        var i = 0
        while (m.size < ScrollMemory.CAP) {
            m = ScrollMemory.store(m, "note${i++}", 0.0, 0.0)
        }
        m = ScrollMemory.store(m, "one-more", 0.0, 0.0)
        assertEquals(ScrollMemory.CAP, m.size)
        assertTrue("b" !in m)
        assertTrue("a" in m)
    }

    @Test fun storingTwiceKeepsOneEntryWithTheNewOffset() {
        var m = ScrollMemory.store(emptyMap(), "a", 1.0, 1.0)
        m = ScrollMemory.store(m, "a", 5.0, 9.0)
        assertEquals(1, m.size)
        assertArrayEquals(longArrayOf(5, 9), ScrollMemory.restore(m, "a"))
    }

    @Test fun theCapHoldsAtTwoHundred() {
        var m = emptyMap<String, LongArray>()
        for (i in 1..ScrollMemory.CAP + 10) m = ScrollMemory.store(m, "note$i", i.toDouble(), 0.0)
        assertEquals(ScrollMemory.CAP, m.size)
        assertNull(ScrollMemory.restore(m, "note1"))
        assertTrue(ScrollMemory.restore(m, "note${ScrollMemory.CAP + 10}") != null)
    }

    @Test fun dropRemovesJustTheNote() {
        var m = ScrollMemory.store(emptyMap(), "a", 0.0, 0.0)
        m = ScrollMemory.store(m, "b", 0.0, 0.0)
        m = ScrollMemory.drop(m, "a")
        assertEquals(setOf("b"), m.keys)
        assertEquals(m, ScrollMemory.drop(m, "zzz"))
    }
}
