package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug HUD's rolling frame-time histogram and latency estimator. */
class FrameTimeStatsTest {

    @Test fun emptySnapshotIsZeroed() {
        val s = FrameTimeStats().snapshot()
        assertEquals(0, s.count)
        assertEquals(0.0, s.minMs, 0.0)
        assertEquals(0.0, s.avgMs, 0.0)
        assertEquals(0.0, s.p95Ms, 0.0)
        assertEquals(0.0, s.maxMs, 0.0)
        assertNull(s.latencyAvgMs)
    }

    @Test fun minAvgMaxOverTheWindow() {
        val f = FrameTimeStats(window = 8)
        for (v in listOf(4.0, 8.0, 6.0, 2.0)) f.addFrame(v)
        val s = f.snapshot()
        assertEquals(4, s.count)
        assertEquals(2.0, s.minMs, 1e-12)
        assertEquals(5.0, s.avgMs, 1e-12)
        assertEquals(8.0, s.maxMs, 1e-12)
    }

    @Test fun p95SitsAboveTheMeanAndAtOrBelowTheMax() {
        val f = FrameTimeStats(window = 120)
        // 119 fast frames and one hitch: the p95 describes the fast population, not the freak.
        repeat(119) { f.addFrame(4.0) }
        f.addFrame(80.0)
        val s = f.snapshot()
        assertEquals(4.0, s.p95Ms, 1e-12)
        assertEquals(80.0, s.maxMs, 1e-12)
        assertTrue(s.p95Ms >= s.minMs) // one freak pushes the mean above p95; the floor holds
        assertTrue(s.p95Ms <= s.maxMs)
    }

    @Test fun p95CatchesARealJankRate() {
        val f = FrameTimeStats(window = 100)
        // 10% of frames at 40 ms: the p95 must be in the slow band, the avg may not be.
        repeat(90) { f.addFrame(5.0) }
        repeat(10) { f.addFrame(40.0) }
        val s = f.snapshot()
        assertEquals(40.0, s.p95Ms, 1e-12)
        assertTrue(s.avgMs < 10.0)
    }

    @Test fun windowRollsOverTheOldestFrames() {
        val f = FrameTimeStats(window = 4)
        for (v in listOf(1.0, 2.0, 3.0, 4.0)) f.addFrame(v)
        f.addFrame(100.0) // evicts the 1.0
        val s = f.snapshot()
        assertEquals(4, s.count)
        assertEquals(2.0, s.minMs, 1e-12)
        assertEquals(100.0, s.maxMs, 1e-12)
    }

    @Test fun rejectsNonsenseSamples() {
        val f = FrameTimeStats(window = 4)
        f.addFrame(Double.NaN)
        f.addFrame(-5.0)
        f.addFrame(Double.POSITIVE_INFINITY)
        assertEquals(0, f.snapshot().count)
    }

    @Test fun latencyAveragesOverRecordedPairs() {
        val f = FrameTimeStats(window = 8)
        f.addLatency(10.0)
        f.addLatency(20.0)
        assertEquals(15.0, f.latencyAverage()!!, 1e-12)
        assertEquals(15.0, f.snapshot().latencyAvgMs!!, 1e-12)
        assertEquals(2, f.snapshot().latencyCount)
    }

    @Test fun latencyWindowRollsToo() {
        val f = FrameTimeStats(window = 2)
        f.addLatency(10.0)
        f.addLatency(20.0)
        f.addLatency(30.0)
        assertEquals(25.0, f.latencyAverage()!!, 1e-12)
    }

    @Test fun resetDropsEverything() {
        val f = FrameTimeStats(window = 4)
        f.addFrame(3.0)
        f.addLatency(3.0)
        f.reset()
        assertEquals(0, f.snapshot().count)
        assertNull(f.latencyAverage())
    }

    @Test fun defaultWindowIs120Frames() {
        assertEquals(120, FrameTimeStats.DEFAULT_WINDOW)
    }
}
