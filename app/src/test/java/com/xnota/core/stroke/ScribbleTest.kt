package com.xnota.core.stroke

import com.xnota.core.geometry.Geometry
import com.xnota.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic scribbles and handwriting exercising [Scribble]. */
class ScribbleTest {

    private val rnd = Random(7)

    private fun jit(amp: Double) = (rnd.nextDouble() * 2.0 - 1.0) * amp

    /** [corners] joined by straight runs sampled every [step], each point nudged by [noise]. */
    private fun polyline(corners: List<Pt>, step: Double = 1.5, noise: Double = 0.4): List<Pt> {
        val out = ArrayList<Pt>()
        for (k in 1 until corners.size) {
            val a = corners[k - 1]
            val b = corners[k]
            val n = (a.distanceTo(b) / step).toInt().coerceAtLeast(1)
            for (i in 0 until n) {
                val t = i.toDouble() / n
                out.add(Pt(a.x + (b.x - a.x) * t + jit(noise), a.y + (b.y - a.y) * t + jit(noise)))
            }
        }
        out.add(corners.last())
        return out
    }

    /** A zigzag of [passes] strokes [h] tall, each one [advance] further along. */
    private fun zigzag(passes: Int, h: Double, advance: Double, x0: Double = 0.0, y0: Double = 0.0): List<Pt> =
        polyline((0..passes).map { Pt(x0 + it * advance, y0 + if (it % 2 == 0) h else 0.0) })

    /** Back and forth along x across [w], drifting [drift] down each pass. */
    private fun scrub(passes: Int, w: Double, drift: Double): List<Pt> =
        polyline((0..passes).map { Pt(if (it % 2 == 0) 0.0 else w, it * drift) })

    /** An arc about ([cx], [cy]) from [a0] to [a1] radians, sampled every [step]. */
    private fun arc(cx: Double, cy: Double, r: Double, a0: Double, a1: Double, step: Double = 1.5): List<Pt> {
        val n = (kotlin.math.abs(a1 - a0) * r / step).toInt().coerceAtLeast(2)
        return (0..n).map { val a = a0 + (a1 - a0) * it / n; Pt(cx + r * cos(a), cy + r * sin(a)) }
    }

    /** Cursive m humps, y up: a stem, a round arch over the top, and a cusp back up at the bottom. */
    private fun cursiveHumps(humps: Int, h: Double, r: Double): List<Pt> {
        val out = ArrayList<Pt>()
        var x = 0.0
        for (k in 0 until humps) {
            out += polyline(listOf(Pt(x, 0.0), Pt(x, h - r)), noise = 0.0)
            out += arc(x + r, h - r, r, PI, 0.0)
            out += polyline(listOf(Pt(x + 2 * r, h - r), Pt(x + 2 * r, 0.0)), noise = 0.0)
            x += 2 * r
        }
        return out
    }

    /** Cursive l loops, y up: up the right of a narrow loop, over, down the left, then a curve into the next. */
    private fun cursiveLoops(loops: Int, h: Double, w: Double): List<Pt> {
        val out = ArrayList<Pt>()
        var x = 0.0
        val r = w / 2
        for (k in 0 until loops) {
            out += polyline(listOf(Pt(x, 0.0), Pt(x + w, h - r)), noise = 0.0)
            out += arc(x + r, h - r, r, 0.0, PI)
            out += polyline(listOf(Pt(x, h - r), Pt(x, r)), noise = 0.0)
            out += arc(x + r, r, r, PI, 2 * PI)
            x += w
        }
        return out
    }

    private fun circles(laps: Int, r: Double): List<Pt> =
        arc(0.0, 0.0, r, 0.0, 2 * PI * laps)

    private fun touches(line: List<Pt>, x: Double, y: Double, r: Double): Boolean {
        val p = Pt(x, y)
        for (i in 1 until line.size) if (Geometry.distancePointToSegment(p, line[i - 1], line[i]) <= r) return true
        return false
    }

    private fun taken(path: List<Pt>, candidates: List<List<Pt>>, radius: Double = 3.0): List<List<Pt>> {
        val passes = Scribble.passes(path, 1.0)
        assertNotNull(passes)
        return Scribble.taken(passes!!, radius, candidates) { c, x, y, r -> touches(c, x, y, r) }
    }

    // --- what reads as a scribble ---

    @Test fun tightZigzagIsAScribble() {
        assertNotNull(Scribble.passes(zigzag(10, 40.0, 8.0), 1.0))
    }

    @Test fun zigzagThatTravelsAsItGoesIsAScribble() {
        assertNotNull(Scribble.passes(zigzag(10, 40.0, 24.0), 1.0))
    }

    @Test fun scrubBackAndForthIsAScribble() {
        assertNotNull(Scribble.passes(scrub(8, 120.0, 4.0), 1.0))
    }

    @Test fun roundedZigzagIsAScribble() {
        val out = ArrayList<Pt>()
        for (k in 0 until 8) {
            val x = k * 10.0
            if (k % 2 == 0) {
                out += polyline(listOf(Pt(x, 5.0), Pt(x, 35.0)), noise = 0.0)
                out += arc(x + 5, 35.0, 5.0, PI, 0.0)
            } else {
                out += polyline(listOf(Pt(x, 35.0), Pt(x, 5.0)), noise = 0.0)
                out += arc(x + 5, 5.0, 5.0, PI, 2 * PI)
            }
        }
        assertNotNull(Scribble.passes(out, 1.0))
    }

    @Test fun aScribbleReadsTheSameAtAnyZoom() {
        val small = zigzag(10, 40.0, 8.0).map { Pt(it.x / 8.0, it.y / 8.0) }
        assertNotNull(Scribble.passes(small, 8.0))
        assertNull(Scribble.passes(small, 0.05))
    }

    // --- what stays ink ---

    @Test fun capitalWIsNot() {
        assertNull(Scribble.passes(zigzag(4, 40.0, 10.0), 1.0))
    }

    @Test fun looseZigzagIsNot() {
        assertNull(Scribble.passes(zigzag(10, 40.0, 40.0), 1.0))
    }

    @Test fun cursiveHumpsAreNot() {
        assertNull(Scribble.passes(cursiveHumps(4, 40.0, 10.0), 1.0))
        assertNull(Scribble.passes(cursiveHumps(5, 30.0, 6.0), 1.0))
    }

    @Test fun cursiveLoopsAreNot() {
        assertNull(Scribble.passes(cursiveLoops(5, 60.0, 14.0), 1.0))
    }

    @Test fun circlesAreNot() {
        assertNull(Scribble.passes(circles(3, 30.0), 1.0))
    }

    @Test fun aLineIsNot() {
        assertNull(Scribble.passes(polyline(listOf(Pt(0.0, 0.0), Pt(300.0, 20.0))), 1.0))
    }

    @Test fun jitterIsNot() {
        assertNull(Scribble.passes(zigzag(12, 3.0, 1.0), 1.0))
    }

    // --- what a scribble takes ---

    @Test fun takesInkItCrosses() {
        val word = listOf(Pt(-5.0, 20.0), Pt(90.0, 20.0))
        assertEquals(listOf(word), taken(zigzag(10, 40.0, 8.0), listOf(word)))
    }

    @Test fun leavesInkOutOfReach() {
        val word = listOf(Pt(-5.0, 20.0), Pt(90.0, 20.0))
        val far = listOf(Pt(0.0, 200.0), Pt(90.0, 200.0))
        assertEquals(listOf(word), taken(zigzag(10, 40.0, 8.0), listOf(word, far)))
    }

    @Test fun takesInkOnlyGrazedOnceItErases() {
        val word = listOf(Pt(-5.0, 20.0), Pt(90.0, 20.0))
        val above = listOf(Pt(-5.0, 41.5), Pt(90.0, 41.5))
        assertEquals(listOf(word, above), taken(zigzag(10, 40.0, 8.0), listOf(word, above)))
    }

    @Test fun hatchingInsideAnOutlineErasesNothing() {
        val top = listOf(Pt(-5.0, 41.5), Pt(90.0, 41.5))
        val bottom = listOf(Pt(-5.0, -1.5), Pt(90.0, -1.5))
        assertTrue(taken(zigzag(10, 40.0, 8.0), listOf(top, bottom)).isEmpty())
    }

    @Test fun aScribbleOverNothingErasesNothing() {
        assertTrue(taken(zigzag(10, 40.0, 8.0), emptyList()).isEmpty())
    }

    @Test fun aSmallMarkCrossedByFewPassesStays() {
        val dot = listOf(Pt(4.0, 20.0), Pt(5.0, 21.0))
        assertTrue(taken(zigzag(10, 40.0, 8.0), listOf(dot)).isEmpty())
    }
}
