package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionLogTest {

    @Test fun appendsInOrder() {
        val log = VersionLog()
        assertTrue(log.append("aa", 100, 10))
        assertTrue(log.append("bb", 200, 20))
        assertEquals(2, log.count)
        assertEquals(listOf("aa", "bb"), log.list().map { it.contentHash })
    }

    @Test fun anUnchangedHashRefreshesTheNewestWithoutANewEntry() {
        val log = VersionLog()
        log.append("aa", 100, 10)
        assertFalse(log.append("aa", 500, 10))
        assertEquals(1, log.count)
        assertEquals(500, log.list().single().timestamp)
    }

    @Test fun aHashThatComesBackAroundMovesToNewest() {
        val log = VersionLog()
        log.append("aa", 100, 10)
        log.append("bb", 200, 20)
        log.append("cc", 300, 30)
        assertTrue(log.append("aa", 400, 10))
        assertEquals(listOf("bb", "cc", "aa"), log.list().map { it.contentHash })
    }

    @Test fun onlyTheLastNStay() {
        val log = VersionLog(maxEntries = 3)
        for (i in 0 until 10) log.append("h$i", (i + 1) * 100L, i)
        assertEquals(3, log.count)
        assertEquals(listOf("h7", "h8", "h9"), log.list().map { it.contentHash })
    }

    @Test fun restoreCountsBackFromTheNewest() {
        val log = VersionLog()
        log.append("aa", 100, 10)
        log.append("bb", 200, 20)
        log.append("cc", 300, 30)
        assertEquals("cc", log.restore(0)?.contentHash)
        assertEquals("bb", log.restore(1)?.contentHash)
        assertEquals("aa", log.restore(2)?.contentHash)
        assertNull(log.restore(3))
        assertNull(log.restore(-1))
    }

    @Test fun newestIsNullOnAnEmptyLog() {
        assertNull(VersionLog().newest())
        assertNull(VersionLog().restore(0))
        assertTrue(VersionLog().list().isEmpty())
    }

    @Test fun jsonRoundTrips() {
        val log = VersionLog()
        log.append("aa", 100, 10)
        log.append("bb", 200, 20)
        val back = VersionLog.fromJson(log.toJson())
        assertEquals(log.list(), back.list())
        assertEquals("bb", back.restore(0)?.contentHash)
    }

    @Test fun unreadableJsonComesBackEmpty() {
        assertTrue(VersionLog.fromJson(null).list().isEmpty())
        assertTrue(VersionLog.fromJson("").list().isEmpty())
        assertTrue(VersionLog.fromJson("not json at all").list().isEmpty())
        assertTrue(VersionLog.fromJson("[{}]").list().isNotEmpty()) // forgiving: blank entry kept
    }

    @Test fun aNegativeSizeIsRejected() {
        assertFalse(VersionLog().append("aa", 100, -5))
    }

    @Test fun aClampedMaxOfOneKeepsOnlyTheNewest() {
        val log = VersionLog(maxEntries = 1)
        log.append("aa", 100, 10)
        log.append("bb", 200, 20)
        assertEquals(listOf("bb"), log.list().map { it.contentHash })
    }
}
