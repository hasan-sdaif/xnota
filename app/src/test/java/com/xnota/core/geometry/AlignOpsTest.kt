package com.xnota.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlignOpsTest {

    /** Three boxes side by side at assorted heights, the usual align playground. */
    private val a = Rect(0.0, 0.0, 10.0, 10.0)
    private val b = Rect(20.0, 5.0, 10.0, 20.0)
    private val c = Rect(40.0, 12.0, 10.0, 5.0)
    private val target = Rect.bounding(listOf(a.topLeft, Pt(b.right, b.bottom), Pt(c.right, c.bottom)))

    @Test fun alignLeftMovesEveryLeftEdgeToTheTargetLeft() {
        val d = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.LEFT)
        assertEquals(0.0, d[0].x, 1e-9)
        assertEquals(0.0, d[0].y, 1e-9)
        assertEquals(target.left - b.left, d[1].x, 1e-9)
        assertEquals(0.0, d[1].y, 1e-9)
        assertEquals(target.left - c.left, d[2].x, 1e-9)
    }

    @Test fun alignRightLinesUpRightEdges() {
        val d = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.RIGHT)
        assertEquals(target.right - a.right, d[0].x, 1e-9)
        assertEquals(target.right - b.right, d[1].x, 1e-9)
        assertEquals(0.0, d[2].x, 1e-9) // c already spans to the target's right edge
    }

    @Test fun alignCentreHLinesUpCentres() {
        val d = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.CENTER_H)
        assertEquals(target.centerX - a.centerX, d[0].x, 1e-9)
        assertEquals(0.0, d[0].y, 1e-9)
        assertEquals(target.centerX - c.centerX, d[2].x, 1e-9)
    }

    @Test fun alignTopAndBottomTouchTheTargetEdges() {
        val top = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.TOP)
        assertEquals(0.0, top[0].y, 1e-9)
        assertEquals(target.top - b.top, top[1].y, 1e-9)
        assertEquals(0.0, top[1].x, 1e-9)
        val bottom = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.BOTTOM)
        assertEquals(target.bottom - a.bottom, bottom[0].y, 1e-9)
        assertEquals(0.0, bottom[1].y, 1e-9) // b spans to the target's bottom
        assertEquals(target.bottom - c.bottom, bottom[2].y, 1e-9)
    }

    @Test fun alignCentreVUsesCentresNotEdges() {
        val d = AlignOps.alignDeltas(listOf(a, b, c), target, AlignMode.CENTER_V)
        assertEquals(target.centerY - a.centerY, d[0].y, 1e-9)
        assertEquals(target.centerY - b.centerY, d[1].y, 1e-9)
        assertEquals(0.0, d[0].x, 1e-9)
    }

    @Test fun aligningALoneItemToThePage() {
        // A single item aligned to the page rect: the caller passes the page as the target.
        val page = Rect(0.0, 0.0, 100.0, 140.0)
        val box = Rect(20.0, 30.0, 10.0, 10.0)
        assertEquals(Pt(-20.0, 0.0), AlignOps.alignDeltas(listOf(box), page, AlignMode.LEFT)[0])
        assertEquals(Pt(70.0, 0.0), AlignOps.alignDeltas(listOf(box), page, AlignMode.RIGHT)[0])
        assertEquals(Pt(0.0, 35.0), AlignOps.alignDeltas(listOf(box), page, AlignMode.CENTER_V)[0])
    }

    @Test fun alignDeltaAppliedToABboxLandsOnTheTarget() {
        val d = AlignOps.alignDeltas(listOf(b), target, AlignMode.CENTER_H)
        val moved = b.translate(d[0].x, d[0].y)
        assertEquals(target.centerX, moved.centerX, 1e-9)
    }

    @Test fun distributeNeedsThreeItems() {
        assertNull(AlignOps.distributeDeltas(listOf(a), vertical = false))
        assertNull(AlignOps.distributeDeltas(listOf(a, b), vertical = false))
    }

    @Test fun distributeHEqualizesTheGapsAndKeepsTheOuterItems() {
        // Gaps 10 and 30 over two inner slots: each gap becomes 20.
        val left = Rect(0.0, 0.0, 10.0, 5.0)
        val mid = Rect(20.0, 0.0, 10.0, 5.0)
        val right = Rect(60.0, 0.0, 10.0, 5.0)
        val d = AlignOps.distributeDeltas(listOf(left, mid, right), vertical = false)!!
        assertEquals(0.0, d[0].x, 1e-9) // the outermost items stay put
        assertEquals(0.0, d[2].x, 1e-9)
        assertEquals(10.0, d[1].x, 1e-9) // mid moves from 20 to 30: gaps 20 and 20
        val moved = listOf(left, mid.translate(d[1].x, 0.0), right)
        assertEquals(moved[1].left - moved[0].right, moved[2].left - moved[1].right, 1e-9)
        assertEquals(20.0, moved[1].left - moved[0].right, 1e-9)
    }

    @Test fun distributeDeltasComeBackInInputOrder() {
        // Given out of order, the deltas still land on the input's slots.
        val left = Rect(0.0, 0.0, 10.0, 5.0)
        val mid = Rect(20.0, 0.0, 10.0, 5.0)
        val right = Rect(60.0, 0.0, 10.0, 5.0)
        val d = AlignOps.distributeDeltas(listOf(right, mid, left), vertical = false)!!
        assertEquals(0.0, d[0].x, 1e-9) // right (input slot 0) is an outermost item
        assertEquals(10.0, d[1].x, 1e-9) // mid
        assertEquals(0.0, d[2].x, 1e-9) // left
    }

    @Test fun distributeVEqualizesVerticalGaps() {
        val top = Rect(0.0, 0.0, 5.0, 10.0)
        val mid = Rect(0.0, 30.0, 5.0, 10.0)
        val bottom = Rect(0.0, 44.0, 5.0, 10.0)
        val d = AlignOps.distributeDeltas(listOf(top, mid, bottom), vertical = true)!!
        assertEquals(0.0, d[0].y, 1e-9)
        assertEquals(0.0, d[2].y, 1e-9)
        assertEquals(-8.0, d[1].y, 1e-9) // gaps 12 and 12: mid moves from 30 to 22
        assertEquals(0.0, d[1].x, 1e-9)
    }

    @Test fun distributeEqualWidthsSpreadsCentresEvenly() {
        val boxes = listOf(
            Rect(0.0, 0.0, 4.0, 4.0),
            Rect(10.0, 0.0, 4.0, 4.0),
            Rect(14.0, 0.0, 4.0, 4.0),
            Rect(40.0, 0.0, 4.0, 4.0),
        )
        val d = AlignOps.distributeDeltas(boxes, vertical = false)!!
        val moved = boxes.mapIndexed { i, r -> r.translate(d[i].x, d[i].y) }
        for (i in 1 until moved.size) {
            val gap = moved[i].left - moved[i - 1].right
            assertEquals((40.0 - 3 * 4.0) / 3.0, gap, 1e-9)
        }
    }

    @Test fun distributeOverlappingItemsShareANegativeGap() {
        // Three stacked boxes: the even spread of a negative gap interleaves them evenly.
        val boxes = listOf(
            Rect(0.0, 0.0, 10.0, 5.0),
            Rect(4.0, 0.0, 10.0, 5.0),
            Rect(8.0, 0.0, 10.0, 5.0),
        )
        val d = AlignOps.distributeDeltas(boxes, vertical = false)!!
        val moved = boxes.mapIndexed { i, r -> r.translate(d[i].x, d[i].y) }
        val g1 = moved[1].left - moved[0].right
        val g2 = moved[2].left - moved[1].right
        assertEquals(g1, g2, 1e-9)
        assertEquals(0.0, moved[0].left, 1e-9) // outer edges stay
        assertEquals(18.0, moved[2].right, 1e-9)
    }
}
