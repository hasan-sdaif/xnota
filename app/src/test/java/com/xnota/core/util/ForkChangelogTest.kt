package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkChangelogTest {

    @Test fun everyVersionIsUniqueAndCarriesItsHeadlines() {
        val versions = ForkChangelog.versions()
        assertTrue("the ledger is not empty", versions.isNotEmpty())
        assertEquals("no version repeats", versions.size, versions.toSet().size)
        for (v in versions) {
            val entries = ForkChangelog.entriesFor(v)
            assertNotNull("version $v has entries", entries)
            assertTrue("version $v carries headlines", entries!!.isNotEmpty())
            assertEquals("no headline repeats within $v", entries.size, entries.toSet().size)
        }
    }

    @Test fun versionsListNewestFirstAndLookupMissesAreNull() {
        // The ledger's own order is the listing order: 1.7.0 leads and 1.0 closes.
        assertEquals(ForkChangelog.releases.map { it.version }, ForkChangelog.versions())
        assertEquals("1.7.0", ForkChangelog.versions().first())
        assertEquals("1.0", ForkChangelog.versions().last())
        // A version the ledger never held resolves to nothing, never to a wrong release's list.
        assertNull(ForkChangelog.entriesFor("0.9"))
        assertEquals(ForkChangelog.releases[0].entries.map { it.id }, ForkChangelog.entriesFor(ForkChangelog.versions()[0]))
    }
}
