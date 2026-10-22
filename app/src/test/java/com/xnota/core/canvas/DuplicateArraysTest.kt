package com.xnota.core.canvas

import com.xnota.core.geometry.Pt
import com.xnota.core.model.CanvasItem
import com.xnota.core.model.Rgba
import com.xnota.core.model.ShapeItem
import com.xnota.core.shapes.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's duplicate arrays (B9): radial copies fanned around a centre and grid copies laid out
 * rows × columns — counts, spacing and the transform each copy bakes, using a plain line shape
 * the way the controllers' clone-then-transform wiring will.
 */
class DuplicateArraysTest {

    private fun line(from: Pt, to: Pt) =
        ShapeItem(ShapeKind.LINE, from, to, Rgba(0, 0, 0), 3.0)

    private val clone: (CanvasItem) -> CanvasItem = { s ->
        (s as ShapeItem).let { ShapeItem(it.shape, it.start, it.end, it.strokeRgba, it.strokeWidth) }
    }

    @Test fun radialFansCopiesAroundTheCentre() {
        val item = line(Pt(10.0, 0.0), Pt(20.0, 0.0))
        val copies = DuplicateArrays.radial(item, Pt.ZERO, copies = 4, spanDegrees = 360.0, clone = clone)
        assertEquals(4, copies.size)
        // Each copy keeps its distance from the centre and sits a quarter-turn from its neighbour.
        val angles = copies.map { Math.toDegrees(kotlin.math.atan2((it as ShapeItem).start.y, it.start.x)) }
        for (i in angles.indices) {
            assertEquals(10.0, (copies[i] as ShapeItem).start.distanceTo(Pt.ZERO), 1e-9)
            val next = angles[(i + 1) % angles.size]
            assertEquals(90.0, kotlin.math.abs(angleDiff(angles[i], next)), 1e-6)
        }
        // The original is untouched — every copy is new.
        assertEquals(Pt(10.0, 0.0), item.start)
    }

    @Test fun aHalfTurnFansOverHalfTheRing() {
        val item = line(Pt(10.0, 0.0), Pt(20.0, 0.0))
        val copies = DuplicateArrays.radial(item, Pt.ZERO, copies = 3, spanDegrees = 180.0, clone = clone)
        assertEquals(3, copies.size)
        val angles = copies.map { Math.toDegrees(kotlin.math.atan2((it as ShapeItem).start.y, it.start.x)) }.map { kotlin.math.abs(it) }.sorted()
        val expected = listOf(60.0, 120.0, 180.0)
        for (i in expected.indices) assertEquals(expected[i], angles[i], 1e-6)
    }

    @Test fun theCountClampsIntoItsRange() {
        val item = line(Pt(10.0, 0.0), Pt(20.0, 0.0))
        assertEquals(DuplicateArrays.MIN_COPIES, DuplicateArrays.radial(item, Pt.ZERO, 1, 360.0, clone).size)
        assertEquals(DuplicateArrays.MAX_COPIES, DuplicateArrays.radial(item, Pt.ZERO, 999, 360.0, clone).size)
    }

    @Test fun aDegenerateSpanMakesNoCopies() {
        val item = line(Pt(10.0, 0.0), Pt(20.0, 0.0))
        assertTrue(DuplicateArrays.radial(item, Pt.ZERO, 4, 0.0, clone).isEmpty())
        assertTrue(DuplicateArrays.radial(item, Pt.ZERO, 4, Double.NaN, clone).isEmpty())
    }

    @Test fun gridLaysCopiesOutWithTheGap() {
        val item = line(Pt.ZERO, Pt(10.0, 5.0))
        val b = item.bounds() // the line's own box plus its ink's reach
        val cellX = b.w + 5.0
        val cellY = b.h + 5.0
        val copies = DuplicateArrays.grid(item, rows = 2, cols = 3, gapX = 5.0, gapY = 5.0, clone = clone)
        assertEquals(5, copies.size) // the original's own slot (0,0) is skipped
        // Each copy sits one cell over, gap included, from the original's top-left.
        val starts = copies.map { (it as ShapeItem).start }.toSet()
        assertEquals(
            setOf(
                Pt(cellX, 0.0), Pt(2 * cellX, 0.0),
                Pt(0.0, cellY), Pt(cellX, cellY), Pt(2 * cellX, cellY),
            ),
            starts,
        )
        assertEquals(Pt.ZERO, item.start)
    }

    @Test fun aNegativeGapJustMeansNoGap() {
        val item = line(Pt.ZERO, Pt(10.0, 5.0))
        val copies = DuplicateArrays.grid(item, rows = 1, cols = 2, gapX = -100.0, gapY = -100.0, clone = clone)
        assertEquals(1, copies.size)
        assertEquals(Pt(item.bounds().w, 0.0), (copies[0] as ShapeItem).start)
    }

    @Test fun gridCountClampsIntoItsRange() {
        val item = line(Pt.ZERO, Pt(10.0, 5.0))
        // A 1×1 array is the original's own slot alone: no copies at all.
        assertEquals(0, DuplicateArrays.grid(item, 1, 1, 0.0, 0.0, clone).size)
        assertEquals(DuplicateArrays.MAX_COPIES * DuplicateArrays.MAX_COPIES - 1, DuplicateArrays.grid(item, 999, 999, 0.0, 0.0, clone).size)
    }

    /** The signed smallest difference between two angles, in degrees. */
    private fun angleDiff(a: Double, b: Double): Double {
        var d = (b - a) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
