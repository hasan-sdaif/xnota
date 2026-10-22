package com.xnota.core.model

import com.xnota.core.stroke.Sample
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.6.0 crash, pinned: [Stroke.appendJoined] used to carry the first stroke's timing array
 * over unchanged while publishing a.n + b.n samples — and a finished stroke's storage is exactly
 * its own count, so the first reader that walked 0 until n (the renderer's timing unpack, the
 * autosave loop, any tAt/sampleAt past the old end) read index == length and took the app down
 * mid-gesture: `ArrayIndexOutOfBoundsException: length=142; index=142`.
 *
 * These lock the joined channel at n long, the clock restart the doc comment always promised,
 * and the load hardening that reads a short channel as "no time" instead of throwing.
 */
class StrokeJoinTimingTest {

    private fun timed(count: Int, t0: Double = 0.0, dx: Double = 2.0): List<Sample> =
        (0 until count).map { i -> Sample(10.0 + i * dx, 20.0 + i * 1.0, 0.5, t0 + i * 5.0) }

    private fun pen(): Stroke = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN))

    /** The reported crash verbatim: 142 timed samples joined with 30, then read end to end. */
    @Test fun joinedTimedStrokeSurvivesFullRead() {
        val a = pen()
        a.setSamples(timed(142)) // exact-size storage, like the pen-up reduction leaves
        val b = pen()
        b.setSamples(timed(30, t0 = 700.0))
        a.appendJoined(b)
        assertEquals(172, a.sampleCount)
        // Every reader that walks 0 until n: the samples view (what the save loop iterates),
        // the per-index accessors, and the ribbon rebuild the invalidated cache triggers.
        assertEquals(172, a.samples.size)
        a.samples.forEach { assertNotNull(it) }
        for (i in 0 until a.sampleCount) {
            a.tAt(i)
            a.sampleAt(i)
        }
        assertNotNull(a.geometry())
    }

    /** The same crash through the live path: drawn strokes, trimmed at pen-up, then joined. */
    @Test fun liveDrawnTrimmedJoinSurvivesFullRead() {
        val a = pen()
        a.finished = false
        for (s in timed(200)) a.addSample(s)
        a.finished = true
        a.trimToSize() // the storage is now exactly 200 long — the short-tuple setup
        val b = pen()
        b.finished = false
        for (s in timed(64, t0 = 1000.0)) b.addSample(s)
        b.finished = true
        b.trimToSize()
        a.appendJoined(b)
        assertEquals(264, a.sampleCount)
        assertEquals(264, a.samples.toList().size)
        a.samples.forEach { assertNotNull(it) }
        assertNotNull(a.geometry())
    }

    /** The joined clock restarts for the appended half, exactly as the doc comment promises. */
    @Test fun joinedTimingRestartsTheClockForTheAppendedHalf() {
        val a = pen()
        a.setSamples(timed(5)) // t = 0, 5, 10, 15, 20
        val b = pen()
        b.setSamples(timed(4, t0 = 700.0)) // t = 700, 705, 710, 715
        a.appendJoined(b)
        assertEquals(9, a.sampleCount)
        assertEquals(20.0, a.tAt(4), 1e-6) // a's own last sample keeps its time
        assertEquals(700.0, a.tAt(5), 1e-6) // b's first sample restarts the clock
        assertEquals(715.0, a.tAt(8), 1e-6)
    }

    /** A timed stroke joined with an untimed one keeps a channel n long; the appended half
     *  reads "no time" (0.0) rather than reaching past the old array. */
    @Test fun timedJoinedUntimedKeepsChannelLongEnough() {
        val a = pen()
        a.setSamples(timed(10))
        val b = pen()
        b.setSamples(timed(6).map { Sample(it.x, it.y, it.pressure, 0.0) })
        a.appendJoined(b)
        assertEquals(16, a.sampleCount)
        assertEquals(45.0, a.tAt(9), 1e-6)
        assertEquals(0.0, a.tAt(10), 1e-9)
        assertEquals(0.0, a.tAt(15), 1e-9)
        assertEquals(16, a.samples.toList().size)
    }

    /** An untimed pair stays untimed: joining allocates nothing, exactly as drawing does. */
    @Test fun untimedJoinStaysUntimed() {
        val a = pen()
        a.setSamples(timed(4).map { Sample(it.x, it.y, it.pressure, 0.0) })
        val b = pen()
        b.setSamples(timed(3).map { Sample(it.x, it.y, it.pressure, 0.0) })
        a.appendJoined(b)
        assertEquals(7, a.sampleCount)
        assertEquals(0.0, a.tAt(6), 1e-9)
        assertEquals(7, a.samples.toList().size)
    }

    /** A joined stroke joins a third time (the long writing session): the channel survives
     *  a second append exactly as it survived the first. */
    @Test fun secondJoinStillSurvivesFullRead() {
        val a = pen()
        a.setSamples(timed(100))
        a.appendJoined(pen().also { it.setSamples(timed(50, t0 = 500.0)) })
        a.appendJoined(pen().also { it.setSamples(timed(80, t0 = 900.0)) })
        assertEquals(230, a.sampleCount)
        assertEquals(230, a.samples.toList().size)
        a.samples.forEach { assertNotNull(it) }
        assertNotNull(a.geometry())
    }

    /** The corrupt-file hardening: a timing channel shorter than the sample count (a file this
     *  fix predates, or a hand-edited one) loads and renders as untimed past its end instead of
     *  crashing the app on open. */
    @Test fun shortTimingChannelOnLoadReadsAsUntimedPastItsEnd() {
        val s = pen()
        val xs = DoubleArray(5) { 10.0 + it * 2.0 }
        val ys = DoubleArray(5) { 20.0 + it }
        val ps = DoubleArray(5) { 0.5 }
        val shortTs = doubleArrayOf(1.0, 6.0) // two entries for five samples
        s.setSamples(xs, ys, ps, shortTs, 5)
        assertEquals(5, s.sampleCount)
        assertEquals(1.0, s.tAt(0), 1e-6)
        assertEquals(6.0, s.tAt(1), 1e-6)
        assertEquals(0.0, s.tAt(2), 1e-9)
        assertEquals(0.0, s.tAt(4), 1e-9)
        assertEquals(5, s.samples.toList().size)
        assertNotNull(s.geometry())
    }

    /** Untimed channels load exactly as before — no allocation, no behaviour change. */
    @Test fun untimedChannelLoadIsUnchanged() {
        val s = pen()
        s.setSamples(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0), doubleArrayOf(0.5, 0.5, 0.5), null, 3)
        assertEquals(3, s.sampleCount)
        assertEquals(0.0, s.tAt(2), 1e-9)
        assertTrue(s.samples.all { it.t == 0.0 })
        assertNull(s.samples.firstOrNull()?.let { if (it.t == 0.0) null else it })
    }
}
