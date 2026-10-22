package com.xnota.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's named dash styles (B9): runs scaled by the stroke width, the period they repeat
 * over, and the phase advance that keeps one rhythm across a line drawn in pieces.
 */
class DashPatternsTest {

    @Test fun theFiveStylesRoundTripById() {
        for (p in DashPattern.entries) assertEquals(p, DashPattern.fromId(p.id))
        assertEquals(DashPattern.SOLID, DashPattern.fromId(null))
        assertEquals(DashPattern.SOLID, DashPattern.fromId("bogus"))
    }

    @Test fun solidHasNoRunsAndADegenerateAdvance() {
        assertTrue(DashPattern.SOLID.runs(4.0).isEmpty())
        assertEquals(0.0, DashPattern.SOLID.period(4.0), 1e-9)
        assertEquals(0.0, DashPattern.SOLID.advance(3.0, 100.0, 4.0), 1e-9)
        assertTrue(DashPattern.SOLID.isSolid)
    }

    @Test fun runsScaleWithTheStrokeWidth() {
        assertEquals(listOf(8.0, 6.0), DashPattern.DASH.runs(2.0).toList())
        assertEquals(listOf(16.0, 12.0), DashPattern.DASH.runs(4.0).toList())
        assertEquals(listOf(8.0, 4.0, 1.0, 4.0), DashPattern.DASH_DOT.runs(2.0).toList())
        assertEquals(listOf(16.0, 4.0), DashPattern.LONG.runs(2.0).toList())
        assertEquals(listOf(1.0, 5.0), DashPattern.DOT.runs(2.0).toList())
    }

    @Test fun aBrokenWidthFallsBackToTheUnit() {
        assertEquals(listOf(4.0, 3.0), DashPattern.DASH.runs(Double.NaN).toList())
        assertEquals(listOf(4.0, 3.0), DashPattern.DASH.runs(0.0).toList())
    }

    @Test fun thePeriodIsTheSumOfTheRuns() {
        for (p in DashPattern.entries) {
            assertEquals(p.runs(3.0).sum(), p.period(3.0), 1e-9)
        }
        assertEquals((4.0 + 2.0 + 0.5 + 2.0) * 3.5, DashPattern.DASH_DOT.period(3.5), 1e-9)
    }

    @Test fun advanceWrapsIntoThePeriod() {
        val w = 2.0
        val period = DashPattern.DASH.period(w)
        assertEquals(3.0, DashPattern.DASH.advance(1.0, 2.0, w), 1e-9)
        assertEquals(1.0, DashPattern.DASH.advance(period - 1.0, 2.0, w), 1e-9)
        val wrapped = DashPattern.DASH.advance(0.0, period * 3.5, w)
        assertTrue(wrapped in 0.0..period)
        assertEquals(period * 0.5, DashPattern.DASH.advance(0.0, period * 3.5, w), 1e-9)
    }

    @Test fun negativePhasesComeBackPositive() {
        val period = DashPattern.DASH.period(2.0)
        assertEquals(period - 1.0, DashPattern.DASH.advance(-1.0, 0.0, 2.0), 1e-9)
        val v = DashPattern.DASH.advance(-17.0, 1.0, 2.0)
        assertTrue(v >= 0.0 && v < period)
    }

    @Test fun everyRunListAlternatesOnOffWithAnEvenCount() {
        for (p in DashPattern.entries) {
            val runs = p.runs(5.0)
            assertTrue("${p.id} needs an even run count", runs.size % 2 == 0)
            for ((i, r) in runs.withIndex()) assertTrue("${p.id} run $i must be positive", r > 0.0)
        }
    }
}
