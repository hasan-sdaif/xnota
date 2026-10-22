package com.xnota.core.canvas

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's magnetic snapping (B9): grid nodes, shape edges/centres, and rotation steps. Each
 * test locks one rule the drag/rotate wiring relies on — the axes decide independently, a snap
 * out of reach never happens, and a broken setting disables rather than explodes.
 */
class SnapEngineTest {

    // --- grid ---

    @Test fun gridSnapsOntoTheNearestNode() {
        assertEquals(Pt(20.0, 10.0), SnapEngine.snapToGrid(Pt(18.0, 11.0), 10.0))
        assertEquals(Pt(0.0, 0.0), SnapEngine.snapToGrid(Pt(4.0, -4.0), 10.0))
        assertEquals(Pt(-20.0, -10.0), SnapEngine.snapToGrid(Pt(-16.0, -14.0), 10.0))
    }

    @Test fun gridDeltaIsTheVectorOntoTheNode() {
        assertEquals(Pt(2.0, -1.0), SnapEngine.gridDelta(Pt(18.0, 11.0), 10.0))
        assertEquals(Pt.ZERO, SnapEngine.gridDelta(Pt(30.0, 20.0), 10.0))
    }

    @Test fun aBrokenGridStepLeavesThePointAlone() {
        assertEquals(Pt(7.0, 9.0), SnapEngine.snapToGrid(Pt(7.0, 9.0), 0.0))
        assertEquals(Pt(7.0, 9.0), SnapEngine.snapToGrid(Pt(7.0, 9.0), Double.NaN))
        assertEquals(Pt(-3.0, 2.0), SnapEngine.snapToGrid(Pt(-3.0, 2.0), Double.NEGATIVE_INFINITY))
    }

    // --- shapes ---

    @Test fun edgesAndCentresPullOntoEachOther() {
        val moving = Rect(102.0, 50.0, 10.0, 10.0) // left 102, centre 107, right 112
        val other = Rect(100.0, 0.0, 50.0, 40.0)   // left 100, centre 125, right 150
        // The moving left is 2 from the other's left — the best alignment within 5.
        assertEquals(Pt(-2.0, 0.0), SnapEngine.shapeDelta(moving, listOf(other), 5.0))
    }

    @Test fun eachAxisDecidesOnItsOwn() {
        val moving = Rect(102.0, 77.0, 10.0, 10.0) // y nowhere near anything
        val other = Rect(100.0, 0.0, 50.0, 40.0)
        val d = SnapEngine.shapeDelta(moving, listOf(other), 5.0)
        assertEquals(-2.0, d.x, 1e-9)
        assertEquals(0.0, d.y, 1e-9)
    }

    @Test fun theNearestAlignmentWinsNotTheFirstOtherMet() {
        val moving = Rect(96.0, 0.0, 10.0, 10.0) // x: 96, 101, 106
        val far = Rect(110.0, 0.0, 4.0, 4.0)     // x: 110, 112, 114 — best err 4 (right vs left)
        val near = Rect(100.0, 0.0, 4.0, 4.0)    // x: 100, 102, 104 — best err 1 (centre vs left)
        // The nearer other's left (100) beats the first other met (err 4): centre 101 moves −1.
        assertEquals(Pt(-1.0, 0.0), SnapEngine.shapeDelta(moving, listOf(far, near), 10.0))
    }

    @Test fun outOfThresholdMeansNoSnapAtAll() {
        val moving = Rect(112.0, 50.0, 10.0, 10.0) // nearest alignment 20 away (60 vs 40)
        val other = Rect(0.0, 0.0, 50.0, 40.0)
        assertEquals(Pt.ZERO, SnapEngine.shapeDelta(moving, listOf(other), 5.0))
    }

    @Test fun noOthersMeansNoSnap() {
        assertEquals(Pt.ZERO, SnapEngine.shapeDelta(Rect(0.0, 0.0, 5.0, 5.0), emptyList(), 100.0))
    }

    // --- rotation ---

    @Test fun rotationSnapsToTheNearestQuarterOfASixthTurn() {
        assertEquals(Math.toRadians(15.0), SnapEngine.snapAngle(Math.toRadians(12.0)), 1e-9)
        assertEquals(Math.toRadians(-15.0), SnapEngine.snapAngle(Math.toRadians(-18.0)), 1e-9)
        assertEquals(Math.toRadians(90.0), SnapEngine.snapAngle(Math.toRadians(88.0)), 1e-9)
    }

    @Test fun rotationOutsideTheToleranceKeepsItsAngle() {
        val theta = Math.toRadians(23.0)
        assertEquals(theta, SnapEngine.snapAngle(theta), 1e-12)
        assertEquals(theta, SnapEngine.snapAngle(theta, toleranceDeg = 5.0, stepDeg = 45.0), 1e-12)
    }

    @Test fun theWindingStaysPut() {
        // -170° is 20° from -180° and 40° from -165°: out of tolerance, unchanged; at -177° it snaps to -180°.
        assertEquals(Math.toRadians(-170.0), SnapEngine.snapAngle(Math.toRadians(-170.0)), 1e-12)
        assertEquals(Math.toRadians(-180.0), SnapEngine.snapAngle(Math.toRadians(-177.0)), 1e-9)
    }

    @Test fun aDegenerateStepLeavesTheAngleAlone() {
        assertEquals(0.7, SnapEngine.snapAngle(0.7, stepDeg = 0.0), 1e-12)
        assertTrue(SnapEngine.snapAngle(Double.NaN).isNaN())
    }
}
