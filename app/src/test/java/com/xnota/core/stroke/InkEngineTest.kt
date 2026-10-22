package com.xnota.core.stroke

import com.xnota.core.geometry.Pt
import com.xnota.core.model.Rgba
import com.xnota.core.tools.InkToolPresets
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Endpoint merging: the pure matcher and the sample fold. */
class StrokeJoinTest {

    private val tool = Tool.PEN
    private val color = Rgba(0, 0, 0, 255)

    private fun end(x: Double, y: Double) = Pt(x, y)

    @Test fun closeEndpointsSameStyleJoin() {
        assertTrue(
            StrokeJoin.canJoin(
                end(100.0, 50.0), tool, 3.0, color,
                end(108.0, 52.0), tool, 3.0, color,
            ),
        )
    }

    @Test fun farEndpointsDoNotJoin() {
        assertFalse(
            StrokeJoin.canJoin(
                end(0.0, 0.0), tool, 3.0, color,
                end(30.0, 0.0), tool, 3.0, color,
            ),
        )
    }

    @Test fun exactTouchJoins() {
        assertTrue(
            StrokeJoin.canJoin(
                end(10.0, 10.0), tool, 3.0, color,
                end(10.0, 10.0), tool, 3.0, color,
            ),
        )
    }

    @Test fun differentToolsDoNotJoin() {
        assertFalse(
            StrokeJoin.canJoin(
                end(0.0, 0.0), Tool.PEN, 3.0, color,
                end(1.0, 0.0), Tool.CALLIGRAPHY, 3.0, color,
            ),
        )
    }

    @Test fun widthWithinToleranceJoins() {
        // ±20% of the previous width: 3.0 accepts 2.4..3.6.
        assertTrue(
            StrokeJoin.canJoin(
                end(0.0, 0.0), tool, 3.0, color,
                end(1.0, 0.0), tool, 3.6, color,
            ),
        )
        assertFalse(
            StrokeJoin.canJoin(
                end(0.0, 0.0), tool, 3.0, color,
                end(1.0, 0.0), tool, 3.7, color,
            ),
        )
    }

    @Test fun differentColoursDoNotJoin() {
        // The joined stroke keeps the old stroke's style, so a colour change must refuse.
        assertFalse(
            StrokeJoin.canJoin(
                end(0.0, 0.0), tool, 3.0, color,
                end(1.0, 0.0), tool, 3.0, Rgba(255, 0, 0, 255),
            ),
        )
    }

    @Test fun nonStrokeToolsDoNotJoin() {
        assertFalse(
            StrokeJoin.canJoin(
                end(0.0, 0.0), Tool.ERASER, 24.0, color,
                end(1.0, 0.0), Tool.ERASER, 24.0, color,
            ),
        )
    }

    @Test fun customGapIsHonoured() {
        val args = { gap: Double ->
            StrokeJoin.canJoin(
                end(0.0, 0.0), tool, 3.0, color,
                end(20.0, 0.0), tool, 3.0, color,
                maxGapPx = gap,
            )
        }
        assertFalse(args(12.0))
        assertTrue(args(25.0))
    }

    // --- the fold ---

    private fun samples(vararg xs: Double): List<Sample> =
        xs.mapIndexed { i, x -> Sample(x, 0.0, 1.0) }

    @Test fun joinAppendsAllSamples() {
        val prev = samples(0.0, 5.0, 10.0)
        val next = samples(12.0, 16.0, 20.0)
        val joined = StrokeJoin.joinSamples(prev, next)
        assertEquals(6, joined.size)
        assertEquals(0.0, joined.first().x, 1e-12)
        assertEquals(20.0, joined.last().x, 1e-12)
    }

    @Test fun coincidentHeadSampleIsDropped() {
        val prev = samples(0.0, 5.0, 10.0)
        val next = samples(10.0, 15.0)
        val joined = StrokeJoin.joinSamples(prev, next)
        assertEquals(4, joined.size)
        assertEquals(15.0, joined.last().x, 1e-12)
    }

    @Test fun nearButDistinctHeadSampleIsKept() {
        val prev = samples(0.0, 5.0, 10.0)
        val next = samples(12.0, 15.0)
        assertEquals(5, StrokeJoin.joinSamples(prev, next).size)
    }

    @Test fun emptySidesPassThrough() {
        assertEquals(0, StrokeJoin.joinSamples(emptyList(), emptyList()).size)
        val a = samples(1.0)
        assertEquals(1, StrokeJoin.joinSamples(a, emptyList()).size)
        assertEquals(1, StrokeJoin.joinSamples(emptyList(), a).size)
    }

    @Test fun bridgeLengthIsTheGap() {
        assertEquals(12.0, StrokeJoin.bridgePx(Pt(0.0, 0.0), Pt(12.0, 0.0)), 1e-12)
    }
}

/** Rapid strokes fold into one undo unit; deliberate pauses do not. */
class StrokeUndoCoalescerTest {

    @Test fun withinWindowCoalesces() {
        var now = 1_000L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        now += 100L
        assertTrue(c.shouldCoalesce(now)) // began 100 ms after the previous pen-up
    }

    @Test fun pastWindowDoesNotCoalesce() {
        var now = 1_000L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        now += 351L
        assertFalse(c.shouldCoalesce(now))
    }

    @Test fun windowBoundaryIsInclusive() {
        var now = 0L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        now = 350L
        assertTrue(c.shouldCoalesce(now))
        now = 351L
        assertFalse(c.shouldCoalesce(now))
    }

    @Test fun noPreviousStrokeNeverCoalesces() {
        val c = StrokeUndoCoalescer(clock = { 5L })
        assertFalse(c.shouldCoalesce(5L))
    }

    @Test fun strokeBeforeThePenUpNeverCoalesces() {
        var now = 1_000L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        assertFalse(c.shouldCoalesce(500L)) // began *before* the previous pen-up
    }

    @Test fun eachPenUpReopensTheWindow() {
        var now = 0L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        now += 100L
        c.notePenUp() // a second quick stroke committed
        now += 300L
        assertTrue(c.shouldCoalesce(now))
        now += 400L
        assertFalse(c.shouldCoalesce(now))
    }

    @Test fun resetForgetsTheWindow() {
        var now = 10L
        val c = StrokeUndoCoalescer(clock = { now })
        c.notePenUp()
        c.reset()
        assertFalse(c.shouldCoalesce(11L))
    }

    @Test fun defaultWindowIsTheSpecified350ms() {
        assertEquals(350L, StrokeUndoCoalescer.DEFAULT_WINDOW_MS)
    }

    @Test fun customWindowIsHonoured() {
        var now = 0L
        val c = StrokeUndoCoalescer(windowMs = 50L, clock = { now })
        c.notePenUp()
        now = 60L
        assertFalse(c.shouldCoalesce(now))
    }
}

/** The debug HUD's stroke readout. */
class StrokeStatsTest {

    @Test fun emptyAndSingleSamples() {
        assertEquals(0, StrokeStats.of(emptyList()).sampleCount)
        val one = StrokeStats.of(listOf(Sample(3.0, 4.0, 0.5)))
        assertEquals(1, one.sampleCount)
        assertEquals(0.5, one.meanPressure, 1e-12)
        assertEquals(0.0, one.lengthPx, 1e-12)
    }

    @Test fun constantVelocityLine() {
        // 10 samples, 5 px apart, every 10 ms: 0.5 px/ms, 45 px, 100 samples/s.
        val samples = (0 until 10).map { Sample(it * 5.0, 0.0, 0.5, it * 10.0) }
        val s = StrokeStats.of(samples)
        assertEquals(45.0, s.lengthPx, 1e-12)
        assertEquals(0.5, s.meanSpeedPxPerMs, 1e-12)
        assertEquals(90.0, s.durationMs, 1e-12)
        assertEquals(100.0, s.samplesPerSec, 1e-9)
        assertEquals(0.5, s.meanPressure, 1e-12)
    }

    @Test fun diagonalDistanceIsEuclidean() {
        val samples = listOf(Sample(0.0, 0.0, 1.0, 0.0), Sample(3.0, 4.0, 1.0, 10.0))
        assertEquals(5.0, StrokeStats.of(samples).lengthPx, 1e-12)
    }

    @Test fun untimedStrokeReportsNoRate() {
        val samples = (0 until 5).map { Sample(it * 2.0, 0.0, 0.8) }
        val s = StrokeStats.of(samples)
        assertEquals(0.0, s.samplesPerSec, 1e-12)
        assertEquals(0.0, s.durationMs, 1e-12)
        assertEquals(8.0, s.lengthPx, 1e-12)
        assertEquals(0.8, s.meanPressure, 1e-12)
    }

    @Test fun zeroDtDuplicatesDoNotPoisonTheSpeed() {
        val samples = listOf(
            Sample(0.0, 0.0, 1.0, 0.0),
            Sample(5.0, 0.0, 1.0, 0.0), // duplicate timestamp: excluded from the speed mean
            Sample(10.0, 0.0, 1.0, 10.0),
        )
        assertEquals(0.5, StrokeStats.of(samples).meanSpeedPxPerMs, 1e-12)
    }

    @Test fun pressureIsClampedIntoUnitRange() {
        val s = StrokeStats.of(listOf(Sample(0.0, 0.0, 1.7), Sample(1.0, 0.0, -0.2)))
        assertEquals(0.5, s.meanPressure, 1e-12) // 1.7 -> 1.0, -0.2 -> 0.0; mean 0.5
    }
}

/** Arc-length subdivision: chords bounded, counts bounded, endpoints exact. */
class StrokeDensifyTest {

    private class FlatRibbon(private val xs: DoubleArray, private val ws: DoubleArray) : RibbonPoints {
        override val pointCount get() = xs.size
        override fun cx(i: Int): Double = xs[i]
        override fun cy(i: Int): Double = 0.0
        override fun hw(i: Int): Double = ws[i]
        override val hasRails get() = false
        override fun leftX(i: Int) = 0.0
        override fun leftY(i: Int) = 0.0
        override fun rightX(i: Int) = 0.0
        override fun rightY(i: Int) = 0.0
    }

    @Test fun chordBoundHoldsAfterDensify() {
        val g = FlatRibbon(doubleArrayOf(0.0, 10.0, 25.0, 60.0), doubleArrayOf(1.0, 1.0, 1.0, 1.0))
        val out = StrokeDensify.densify(g, 0, 4, 3.0)
        for (i in 0 until out.pointCount - 1) {
            val chord = kotlin.math.hypot(out.cx(i + 1) - out.cx(i), out.cy(i + 1) - out.cy(i))
            assertTrue("chord $chord over the cap at $i", chord <= 3.0 + 1e-9)
        }
    }

    @Test fun endpointsArePreservedExactly() {
        val g = FlatRibbon(doubleArrayOf(0.0, 10.0, 25.0), doubleArrayOf(1.0, 2.0, 3.0))
        val out = StrokeDensify.densify(g, 0, 3, 2.0)
        assertEquals(0.0, out.cx(0), 0.0)
        assertEquals(25.0, out.cx(out.pointCount - 1), 0.0)
        assertEquals(1.0, out.hw(0), 0.0)
        assertEquals(3.0, out.hw(out.pointCount - 1), 0.0)
    }

    @Test fun countIsBounded() {
        val xs = DoubleArray(5) { it * 12.0 }
        val g = FlatRibbon(xs, DoubleArray(5) { 1.0 })
        val out = StrokeDensify.densify(g, 0, 5, 3.0)
        // Every original chord of 12 px splits into 4: (5-1)*4 pieces + the final point.
        assertTrue(out.pointCount <= 4 * 4 + 1)
        assertTrue(out.pointCount >= 5)
    }

    @Test fun alreadyFinePathIsCopied() {
        val xs = doubleArrayOf(0.0, 1.0, 2.0, 3.0)
        val g = FlatRibbon(xs, doubleArrayOf(1.0, 1.0, 1.0, 1.0))
        assertFalse(StrokeDensify.needsDensify(g, 0, 4, 3.0))
        val out = StrokeDensify.densify(g, 0, 4, 3.0)
        assertEquals(4, out.pointCount)
    }

    @Test fun widthsAreInterpolated() {
        val g = FlatRibbon(doubleArrayOf(0.0, 10.0), doubleArrayOf(1.0, 3.0))
        val out = StrokeDensify.densify(g, 0, 2, 5.0)
        // The 10 px chord splits in two at t=0.5: width 2.0 there.
        assertEquals(2.0, out.hw(1), 1e-12)
    }

    @Test fun railsExistOnTheDensifiedRibbon() {
        val g = FlatRibbon(doubleArrayOf(0.0, 10.0), doubleArrayOf(1.0, 1.0))
        val out = StrokeDensify.densify(g, 0, 2, 4.0)
        assertTrue(out.hasRails)
        // Rails are centre ± normal·hw; along +x the normal is -y, so left = centre + hw in y.
        for (i in 0 until out.pointCount) {
            assertEquals(out.cy(i) + out.hw(i), out.leftY(i), 1e-9)
            assertEquals(out.cy(i) - out.hw(i), out.rightY(i), 1e-9)
        }
    }

    @Test fun segmentCountIsCeilOfRatio() {
        assertEquals(1, StrokeDensify.segments(0.0, 3.0))
        assertEquals(1, StrokeDensify.segments(2.9, 3.0))
        assertEquals(1, StrokeDensify.segments(3.0, 3.0)) // exactly max fits in one piece
        assertEquals(4, StrokeDensify.segments(12.0, 3.0))
    }

    @Test fun degenerateInputsPassThrough() {
        val g = FlatRibbon(doubleArrayOf(0.0), doubleArrayOf(1.0))
        assertEquals(1, StrokeDensify.densify(g, 0, 1, 3.0).pointCount)
    }
}

/** The rainbow hue progression and the pure HSV pair it rides on. */
class RainbowInkTest {

    @Test fun hueAdvancesAlongTheArc() {
        assertEquals(10.0, RainbowInk.hueAt(10.0, 0.0), 1e-12)
        assertEquals(11.5, RainbowInk.hueAt(10.0, 10.0), 1e-12) // 0.15 deg/px
        assertEquals(25.0, RainbowInk.hueAt(10.0, 100.0), 1e-12)
    }

    @Test fun hueWrapsMod360() {
        assertEquals(0.0, RainbowInk.hueAt(0.0, 360.0 / 0.15), 1e-9)
        val wrapped = RainbowInk.hueAt(350.0, 100.0) // +15 -> 365 -> 5
        assertEquals(5.0, wrapped, 1e-9)
    }

    @Test fun negativeArcWrapsToo() {
        assertEquals(350.0, RainbowInk.hueAt(0.0, -10.0 / RainbowInk.DEG_PER_PX), 1e-6) // -10 deg -> 350
    }

    @Test fun hsvConversionsRoundTrip() {
        val triples = listOf(
            Triple(0.0, 1.0, 1.0),
            Triple(120.0, 0.5, 0.8),
            Triple(241.3, 0.77, 0.42),
            Triple(359.9, 0.2, 0.9),
        )
        for ((h, s, v) in triples) {
            val (r, g, b) = RainbowInk.hsvToRgb(h, s, v)
            val (h2, s2, v2) = RainbowInk.rgbToHsv(r, g, b)
            assertTrue("hue $h -> $h2", kotlin.math.abs(h - h2) < 1e-6 || (h < 1e-6 && h2 > 359.9))
            assertTrue(kotlin.math.abs(s - s2) < 1e-9)
            assertTrue(kotlin.math.abs(v - v2) < 1e-9)
        }
    }

    @Test fun pureColoursConvertExactly() {
        assertEquals(Triple(1.0, 0.0, 0.0), RainbowInk.hsvToRgb(0.0, 1.0, 1.0))
        assertEquals(Triple(0.0, 1.0, 0.0), RainbowInk.hsvToRgb(120.0, 1.0, 1.0))
        assertEquals(Triple(0.0, 0.0, 1.0), RainbowInk.hsvToRgb(240.0, 1.0, 1.0))
        assertEquals(Triple(1.0, 1.0, 1.0), RainbowInk.hsvToRgb(0.0, 0.0, 1.0))
    }

    @Test fun colorAtKeepsAlphaAndTones() {
        val base = Rgba(255, 0, 0, 255) // pure red, hue 0
        val mid = RainbowInk.colorAt(base, 120.0 / RainbowInk.DEG_PER_PX) // +120 deg
        assertEquals(255, mid.a)
        assertEquals(0, mid.r) // green now
        assertEquals(255, mid.g)
        assertEquals(0, mid.b)
    }

    @Test fun baseColourReturnsAtZeroArc() {
        val base = Rgba(10, 20, 30, 200)
        assertEquals(base, RainbowInk.colorAt(base, 0.0))
    }

    @Test fun chunksCoverTheStrokeAndOverlapByOne() {
        val xs = (0 until 100).map { it * 3.0 }.toDoubleArray() // 3 px spacing
        val ys = DoubleArray(100)
        val chunks = RainbowInk.chunks(xs, ys, chunkPx = 24.0)
        // Coverage: the first chunk starts at 0, the last ends at n, consecutive overlap by one.
        assertEquals(0, chunks.first().first)
        var covered = 0
        for (i in chunks.indices) {
            val (from, count) = chunks[i]
            if (i > 0) assertEquals(chunks[i - 1].first + chunks[i - 1].second - 1, from)
            covered += count - if (i > 0) 1 else 0
        }
        assertEquals(100, covered)
        assertEquals(100, chunks.last().first + chunks.last().second)
    }

    @Test fun tinyInputsGiveOneChunk() {
        assertEquals(listOf(0 to 1), RainbowInk.chunks(doubleArrayOf(4.0), doubleArrayOf(0.0)))
        assertEquals(1, RainbowInk.chunks(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 0.0)).size)
    }
}

/** The engine-options bundle and its render-time install. */
class InkEngineOptionsTest {

    @Test fun defaultsAreTheHistoricalIdentity() {
        val o = InkEngineOptions.DEFAULT
        assertEquals(StrokeFilterMode.EMA, o.filter.mode)
        assertFalse(o.adaptive)
        assertEquals(0.0, o.grain, 0.0)
        assertEquals(0.0, o.scatter, 0.0)
        assertEquals(NibAngle.FOLLOW_TRAVEL, o.nibAngleLockDeg, 0.0)
        assertEquals(PressureShape.DEFAULT, o.pressure)
    }

    @Test fun optionsForComposesGlobalsWithPerStrokeFields() {
        EngineTuning.filter = StrokeFilter(mode = StrokeFilterMode.ONE_EURO)
        EngineTuning.adaptiveSmoothing = false
        EngineTuning.pressure = PressureShape(deadzone = 0.1, gamma = 1.5, smoothing = 20.0)
        EngineTuning.nibAngleLockDeg = 90.0
        try {
            val o = EngineTuning.optionsFor(grain = 40.0, grainSeed = 1L, scatter = 10.0, scatterSeed = 2L)
            assertEquals(StrokeFilterMode.ONE_EURO, o.filter.mode)
            assertFalse(o.adaptive)
            assertEquals(0.1, o.pressure.deadzone, 0.0)
            assertEquals(1.5, o.pressure.gamma, 0.0)
            assertEquals(90.0, o.nibAngleLockDeg, 0.0)
            assertEquals(40.0, o.grain, 0.0)
            assertEquals(1L, o.grainSeed)
            assertEquals(10.0, o.scatter, 0.0)
            assertEquals(2L, o.scatterSeed)
        } finally {
            EngineTuning.filter = StrokeFilter.DEFAULT
            EngineTuning.adaptiveSmoothing = true
            EngineTuning.pressure = PressureShape.DEFAULT
            EngineTuning.nibAngleLockDeg = NibAngle.FOLLOW_TRAVEL
        }
    }

    @Test fun defaultTuningIsProductionDefault() {
        // The production default is the historical ink pipeline: EMA filter, adaptive smoothing
        // OFF (the pref opts in), pressure shape identity, nib following travel. Byte-for-byte
        // the pre-fork ribbon until the owner flips a switch.
        assertFalse(EngineTuning.adaptiveSmoothing)
        assertTrue(EngineTuning.prediction)
        assertEquals(StrokeFilterMode.EMA, EngineTuning.filter.mode)
        assertEquals(PressureShape.DEFAULT, EngineTuning.pressure)
        assertEquals(com.xnota.core.stroke.NibAngle.FOLLOW_TRAVEL, EngineTuning.nibAngleLockDeg, 1e-12)
    }
}

/** The new pens' presets: pure composition of engine-facing knobs. */
class InkToolPresetsTest {

    @Test fun neonUnderglowIsWiderAndTranslucent() {
        val (width, alpha) = InkToolPresets.neonUnderglow(4.0)
        assertEquals(4.0 * 2.6, width, 1e-12)
        assertEquals(0.30, alpha, 1e-12)
        assertTrue(width > 4.0)
        assertTrue(alpha in 0.0..1.0)
    }

    @Test fun neonPresetGlows() {
        val c = InkToolPresets.neon()
        assertTrue(c.neon)
        assertTrue(c.neonStrength in 0.0..1.0)
        assertTrue(c.pressureEnabled)
    }

    @Test fun rainbowPresetIsAnOrdinaryPenByStoredFields() {
        val c = InkToolPresets.rainbow()
        assertFalse(c.neon)
        assertEquals(0.0, c.roughness, 0.0)
        assertEquals(0.0, c.scatter, 0.0)
        assertTrue(c.pressureEnabled)
    }

    @Test fun pencilPresetIsFixedWidthAndGrainy() {
        val c = InkToolPresets.pencil()
        assertFalse(c.pressureEnabled)
        assertEquals(1.0, c.pressureMinFactor, 1e-12)
        assertEquals(InkToolPresets.PENCIL_ROUGHNESS, c.roughness, 0.0)
        assertTrue(c.roughness > 50.0)
    }

    @Test fun presetsMatchTheToolDefaults() {
        assertEquals(InkToolPresets.neon(), ToolDefaults.configFor(Tool.NEON))
        assertEquals(InkToolPresets.rainbow(), ToolDefaults.configFor(Tool.RAINBOW))
        assertEquals(InkToolPresets.pencil(), ToolDefaults.configFor(Tool.PENCIL))
    }
}
