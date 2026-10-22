package com.xnota.core.model

import com.xnota.core.FakeRenderer
import com.xnota.core.FakeTextMeasurer
import com.xnota.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's page-number stamp (the folio): placement modes, the bottom-margin geometry the
 * [paintPageNumber] helper settles, and the region/ink gates the cached background layers rely on.
 * Text is set through [FakeTextMeasurer] so widths and metrics are exact: every character advances
 * 0.6·pointSize and the 9pt folio's ascent/descent are 9.0/2.7.
 */
class PageNumberStampTest {

    private val ink = Rgba(90, 90, 90, 128)
    private val paper = Rect(0.0, 0.0, 600.0, 800.0)
    private val measurer = FakeTextMeasurer()

    /** The one stamp the renderer saw, as (text, x, baseline); null when none landed. */
    private fun stamped(text: String, placement: Int, region: Rect = paper, color: Rgba = ink): Triple<String, Double, Double>? {
        val r = FakeRenderer()
        val landed = paintPageNumber(r, measurer, text, placement, paper, color, region)
        val op = r.ops.firstOrNull { it.startsWith("drawTextRun:") } ?: return if (landed) throw AssertionError("landed but not drawn") else null
        if (!landed) throw AssertionError("drawn but reported not landed")
        val m = Regex("drawTextRun:(.+)@([\\d.\\-]+),([\\d.\\-]+)").matchEntire(op)!!
        return Triple(m.groupValues[1], m.groupValues[2].toDouble(), m.groupValues[3].toDouble())
    }

    @Test fun theFolioIsSetAtNinePoints() {
        assertEquals(9.0, PAGE_NUMBER_PT, 0.0)
    }

    @Test fun centrePlacementCentresOnThePaper() {
        val adv = PAGE_NUMBER_PT * FakeTextMeasurer.ADVANCE_PER_POINT
        val (text, x, baseline) = stamped("3", 1)!!
        assertEquals("3", text)
        assertEquals(paper.centerX - adv / 2.0, x, 1e-9)
        assertEquals(paper.bottom - PAGE_NUMBER_PT * 0.3 - 6.0, baseline, 1e-9)
    }

    @Test fun rightPlacementTucksIntoTheRightEdge() {
        val adv = PAGE_NUMBER_PT * FakeTextMeasurer.ADVANCE_PER_POINT * 2 // two digits
        val (text, x, baseline) = stamped("12", 2)!!
        assertEquals("12", text)
        assertEquals(paper.right - 10.0 - adv, x, 1e-9)
        assertEquals(paper.bottom - PAGE_NUMBER_PT * 0.3 - 6.0, baseline, 1e-9)
    }

    @Test fun offAndUnknownModesDrawNothing() {
        assertEquals(null, stamped("3", 0))
        assertEquals(null, stamped("3", 7))
        assertEquals(null, stamped("", 1))
    }

    @Test fun aStampOutsideThePaintedRegionIsSkipped() {
        // The stamp sits at the paper's bottom; a slice that stops above it never sees it.
        assertEquals(null, stamped("3", 1, region = Rect(0.0, 0.0, 600.0, 700.0)))
        // ...while a slice over the bottom margin does.
        assertTrue(stamped("3", 1, region = Rect(0.0, 750.0, 600.0, 50.0)) != null)
    }

    @Test fun aStampInThePaperIsStillOnThePaper() {
        val (_, x, baseline) = stamped("7", 2)!!
        assertTrue(x >= paper.left && x <= paper.right)
        assertTrue(baseline <= paper.bottom && baseline >= paper.bottom - 40.0)
    }

    @Test fun invisibleInkStampsNothing() {
        assertEquals(null, stamped("3", 1, color = Rgba(0, 0, 0, 0)))
    }
}
