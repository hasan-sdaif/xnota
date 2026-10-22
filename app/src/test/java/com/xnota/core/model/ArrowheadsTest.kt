package com.xnota.core.model

import com.xnota.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's canonical endpoint styles (B9): the five classic arrowhead kinds' pure geometry —
 * what the head draws at an endpoint for a direction and a stroke width, plus the insets that
 * keep the line out of a head with a body.
 */
class ArrowheadsTest {

    private val end = Pt(100.0, 50.0)
    private val dir = Pt(1.0, 0.0)
    private val width = 4.0

    @Test fun theFiveStylesRoundTripById() {
        for (a in Arrowhead.entries) assertEquals(a, Arrowhead.fromId(a.id))
        assertEquals(Arrowhead.NONE, Arrowhead.fromId(null))
        assertEquals(Arrowhead.NONE, Arrowhead.fromId("bogus"))
    }

    @Test fun noneDrawsNothing() {
        assertTrue(Arrowhead.NONE.outline(end, dir, width).isEmpty())
        // A degenerate direction draws nothing either, whatever the style.
        assertTrue(Arrowhead.ARROW.outline(end, Pt(0.0, 0.0), width).isEmpty())
    }

    @Test fun anArrowIsAnOpenChevronPointingAlongTheLine() {
        val runs = Arrowhead.ARROW.outline(end, dir, width)
        assertEquals(1, runs.size)
        assertTrue(!runs[0].closed)
        val len = Arrowhead.length(width)
        val pts = runs[0].points
        assertEquals(3, pts.size)
        // The tip clears the line's round cap; the back sits len behind it, half a len either side.
        assertEquals(end.x + width * 0.5, pts[1].x, 1e-9)
        assertEquals(end.y, pts[1].y, 1e-9)
        assertEquals(end.x + width * 0.5 - len, pts[0].x, 1e-9)
        assertEquals(len * 0.5, kotlin.math.abs(pts[0].y - end.y), 1e-9)
        assertEquals(pts[0].y, -pts[2].y + 2 * end.y, 1e-9) // the two backs mirror about the line
    }

    @Test fun aTriangleIsAFilledPolygonWithItsTipOnTheEnd() {
        val runs = Arrowhead.TRIANGLE.outline(end, dir, width)
        assertEquals(1, runs.size)
        assertTrue(runs[0].closed)
        val len = Arrowhead.length(width)
        val pts = runs[0].points
        assertEquals(end, pts[0])
        assertEquals(end.x - len, pts[1].x, 1e-9)
        assertEquals(len * 0.5, kotlin.math.abs(pts[1].y - end.y), 1e-9)
        // The line pulls back by the head's length so it never shows through the body.
        assertEquals(len, Arrowhead.TRIANGLE.inset(width), 1e-9)
    }

    @Test fun aDotIsARoundPolygonCentredBehindTheEnd() {
        val runs = Arrowhead.DOT.outline(end, dir, width)
        assertEquals(1, runs.size)
        assertTrue(runs[0].closed)
        val len = Arrowhead.length(width)
        val r = len * Arrowhead.DOT_RADIUS_FRACTION
        val pts = runs[0].points
        assertEquals(Arrowhead.DOT_SIDES, pts.size)
        val c = Pt(end.x - len * 0.5, end.y)
        for (p in pts) assertEquals(r, p.distanceTo(c), 1e-9)
        // The inset hides the line's end under the dot without eating past it.
        assertEquals(len * 0.5 - r, Arrowhead.DOT.inset(width), 1e-9)
    }

    @Test fun aCrossIsTwoStrokesCrossingBehindTheEnd() {
        val runs = Arrowhead.CROSS.outline(end, dir, width)
        assertEquals(2, runs.size)
        assertTrue(runs.all { !it.closed && it.points.size == 2 })
        val len = Arrowhead.length(width)
        val c = Pt(end.x - len * 0.5, end.y)
        // Each stroke runs a full len diagonally through the crossing point, both ways.
        for (run in runs) {
            val (a, b) = run.points
            assertEquals(len, a.distanceTo(b), 1e-9)
            assertEquals(c, Pt((a.x + b.x) / 2.0, (a.y + b.y) / 2.0))
        }
        // The two strokes are perpendicular to each other.
        val u = runs[0].points[1] - runs[0].points[0]
        val v = runs[1].points[1] - runs[1].points[0]
        assertEquals(0.0, u.x * v.x + u.y * v.y, 1e-9)
        // And the line runs to the end under the cross.
        assertEquals(0.0, Arrowhead.CROSS.inset(width), 1e-9)
    }

    @Test fun theHeadLengthFollowsTheStrokeWidth() {
        assertEquals(12.0, Arrowhead.length(1.0), 1e-9) // the floor
        assertEquals(4.0 * 3.5, Arrowhead.length(4.0), 1e-9)
        assertEquals(40.0 * 3.5, Arrowhead.length(40.0), 1e-9)
    }

    @Test fun theDirectionTurnsTheHead() {
        val up = Pt(0.0, -1.0)
        val runs = Arrowhead.TRIANGLE.outline(end, up, width)
        val pts = runs[0].points
        assertEquals(end, pts[0])
        // The back is the midpoint of the two base corners: one len below (y down), on the line.
        assertEquals(end.y + Arrowhead.length(width), (pts[1].y + pts[2].y) / 2.0, 1e-9)
        assertEquals(end.x, (pts[1].x + pts[2].x) / 2.0, 1e-9)
    }
}
