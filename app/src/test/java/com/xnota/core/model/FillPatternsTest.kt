package com.xnota.core.model

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's shape fill patterns (B9): the hatch lines and dot lattice a painter draws inside a
 * rect — clipped segments, an origin-anchored lattice that any sub-rect reproduces, and the
 * angles each pattern owns.
 */
class FillPatternsTest {

    private val rect = Rect(0.0, 0.0, 10.0, 10.0)

    @Test fun theFiveStylesRoundTripById() {
        for (p in FillPattern.entries) assertEquals(p, FillPattern.fromId(p.id))
        assertEquals(FillPattern.SOLID, FillPattern.fromId(null))
        assertEquals(FillPattern.SOLID, FillPattern.fromId("bogus"))
    }

    @Test fun blankIsDeliberatelyEmptyAndSolidIsNot() {
        assertTrue(FillPattern.BLANK.hatchLines(rect, 2.0).isEmpty())
        assertTrue(FillPattern.BLANK.dotGrid(rect, 2.0).isEmpty())
        assertTrue(!FillPattern.BLANK.drawsArea)
        assertTrue(FillPattern.SOLID.drawsArea)
        // Solid and dots draw no hatch lines either — only BLANK draws nothing at all.
        assertTrue(FillPattern.SOLID.hatchLines(rect, 2.0).isEmpty())
        assertTrue(FillPattern.DOTS.hatchLines(rect, 2.0).isEmpty())
    }

    @Test fun aDiagonalHatchCoversTheSquareAtTheRightPitch() {
        val lines = FillPattern.HATCH.hatchLines(rect, 2.0, 45.0)
        // The 45° family spans 10·√2 ≈ 14.14 of normal distance: offsets 2, 4, …, 14 cross the
        // square (offset 0 only grazes the corner and draws nothing) — seven real segments.
        assertEquals(7, lines.size)
        fun inside(p: Pt) = p.x >= rect.left - 1e-9 && p.x <= rect.right + 1e-9 &&
            p.y >= rect.top - 1e-9 && p.y <= rect.bottom + 1e-9
        for ((a, b) in lines) {
            // Every segment stays inside the rect (up to clipping's float dust) and runs at exactly 45° (y down).
            for (p in listOf(a, b)) assertTrue("($p) inside", inside(p))
            assertEquals(1.0, kotlin.math.abs((b.y - a.y) / (b.x - a.x)), 1e-9)
        }
    }

    @Test fun aHorizontalHatchIsThePlainLines() {
        val lines = FillPattern.HATCH.hatchLines(Rect(0.0, 0.0, 10.0, 6.0), 2.0, 0.0)
        assertEquals(4, lines.size) // y = 0, 2, 4, 6 — the boundary lines included
        for (i in lines.indices) {
            assertEquals(0.0, lines[i].first.x, 1e-9)
            assertEquals(2.0 * i, lines[i].first.y, 1e-9)
            assertEquals(0.0, lines[i].first.x, 1e-9)
            assertEquals(10.0, lines[i].second.x, 1e-9)
        }
    }

    @Test fun crossHatchDoublesTheFamily() {
        val one = FillPattern.HATCH.hatchLines(rect, 2.0, 45.0)
        val both = FillPattern.CROSS_HATCH.hatchLines(rect, 2.0, 45.0)
        assertEquals(2 * one.size, both.size)
    }

    @Test fun theLatticeIsAnchoredToTheOriginNotTheRect() {
        val whole = FillPattern.HATCH.hatchLines(Rect(0.0, 0.0, 20.0, 20.0), 5.0, 0.0)
        val slice = FillPattern.HATCH.hatchLines(Rect(10.0, 10.0, 10.0, 10.0), 5.0, 0.0)
        // The slice's lines (y = 10, 15, 20) are the whole's lines restricted to it.
        val wholeYs = whole.map { it.first.y }.toSet()
        for ((a, _) in slice) assertTrue("y=${a.y} should be on the shared lattice", a.y in wholeYs)
    }

    @Test fun aDegenerateSpacingOrRectDrawsNothing() {
        assertTrue(FillPattern.HATCH.hatchLines(rect, 0.0).isEmpty())
        assertTrue(FillPattern.HATCH.hatchLines(rect, Double.NaN).isEmpty())
        assertTrue(FillPattern.HATCH.hatchLines(Rect(0.0, 0.0, 0.0, 5.0), 2.0).isEmpty())
        assertTrue(FillPattern.DOTS.dotGrid(Rect(0.0, 0.0, 0.0, 5.0), 2.0).isEmpty())
    }

    @Test fun theDotGridWalksTheLatticeInsideTheRect() {
        val dots = FillPattern.DOTS.dotGrid(rect, 3.0)
        assertEquals(
            listOf(Pt(0.0, 0.0), Pt(3.0, 0.0), Pt(6.0, 0.0), Pt(9.0, 0.0)).sortedBy { it.x },
            dots.filter { it.y == 0.0 }.sortedBy { it.x },
        )
        assertEquals(16, dots.size) // 4 columns × 4 rows
        assertTrue(dots.all { rect.contains(it) })
    }

    @Test fun aDotGridOffsetFromTheOriginStaysOnItsOwnLattice() {
        val dots = FillPattern.DOTS.dotGrid(Rect(1.0, 1.0, 9.0, 9.0), 4.0)
        // ceil(1/4)=1: the first column lands at x=4, not at 1.
        assertTrue(dots.all { it.x % 4.0 == 0.0 && it.y % 4.0 == 0.0 })
        assertEquals(listOf(4.0, 8.0), dots.map { it.x }.distinct().sorted())
    }
}
