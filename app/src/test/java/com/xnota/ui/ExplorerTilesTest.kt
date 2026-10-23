package com.xnota.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Recent shelf's cover chooser as data: an annotated PDF reads as warm paper, anything
 * multi-paged as a notebook, the rest stays plain — pure presentation data, tested where it
 * lives (ExplorerTiles).
 */
class ExplorerTilesTest {

    @Test
    fun `the cover follows the kind and the page count`() {
        assertEquals(CoverKind.PAPER, coverKind(EntryKind.PDF, 1))
        assertEquals(CoverKind.NOTEBOOK, coverKind(EntryKind.NOTE, 28))
        assertEquals(CoverKind.NOTEBOOK, coverKind(EntryKind.CANVAS, 2))
        assertEquals(CoverKind.PLAIN, coverKind(EntryKind.NOTE, 1))
    }

    @Test
    fun `a single-page pdf still reads as paper`() {
        // The PDF's warm-paper cover is about what the file is, not how much it holds.
        assertEquals(CoverKind.PAPER, coverKind(EntryKind.PDF, 1))
        assertEquals(CoverKind.PAPER, coverKind(EntryKind.PDF, 42))
    }
}
