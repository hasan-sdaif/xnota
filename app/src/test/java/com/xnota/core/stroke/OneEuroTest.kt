package com.xnota.core.stroke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The one-euro filter's contract: steady at rest, faithful at speed, deterministic always. */
class OneEuroTest {

    private val cfg = StrokeFilter.DEFAULT

    private fun channel() = OneEuroChannel(cfg.freqHz, cfg.minCutoff, cfg.beta, cfg.dCutoff)

    // --- convergence ---
    @Test fun convergesToConstantSignal() {
        val f = channel()
        var out = 0.0
        for (i in 0 until 400) out = f.filter(5.0, i * 8.0)
        assertEquals(5.0, out, 1e-9)
    }

    @Test fun firstSamplePassesThrough() {
        assertEquals(2.5, channel().filter(2.5, 0.0), 0.0)
    }

    // --- jitter attenuation ---
    @Test fun attenuatesJitterVersusRaw() {
        val f = channel()
        var rawErr = 0.0
        var outErr = 0.0
        var out = 0.0
        val n = 600
        for (i in 0 until n) {
            val noise = if (i % 2 == 0) 1.0 else -1.0
            val x = 5.0 + noise
            out = f.filter(x, i * 8.0)
            rawErr += abs(noise)
            if (i >= 100) outErr += abs(out - 5.0)
        }
        // The raw signal spends its whole life 1 px off the mean; after settling, the filter's
        // residual is a fraction of that.
        // EMA-parity tuning (the default) halves alternating jitter, exactly as the arc-length
        // EMA does; a heavier one-euro would beat this bar only by lagging more than the EMA.
        assertTrue("filtered jitter $outErr should sit under half the raw $rawErr", outErr * 2 < rawErr)
    }

    // --- lag vs a heavy EMA ---
    @Test fun tracksRampWithLessLagThanHeavyEma() {
        // A constant-velocity ramp at 120 Hz. A heavy per-sample EMA trails it by a fixed gap;
        // the one-euro's cutoff rises with the measured speed and closes most of it.
        val n = 400
        val step = 0.1 // px per sample -> 12 px/s
        val f = channel()
        var ema = 0.0
        var oe = 0.0
        val alpha = 0.05
        for (i in 0 until n) {
            val x = i * step
            ema = alpha * x + (1.0 - alpha) * ema
            oe = f.filter(x, i * (1000.0 / 120.0))
        }
        val target = (n - 1) * step
        val lagEma = target - ema
        val lagOe = target - oe
        assertTrue("one-euro lag $lagOe should beat EMA lag $lagEma", lagOe < lagEma)
        // And it must still actually track (not sit at the start).
        assertTrue("one-euro should reach the ramp: $oe vs $target", abs(target - oe) < 2.0)
    }

    // --- un-timed streams fall back to the configured rate ---
    @Test fun untimedStreamStillFilters() {
        val f = channel()
        var out = 0.0
        for (i in 0 until 300) out = f.filter(if (i % 2 == 0) 0.0 else 2.0, 0.0)
        assertTrue(out in 0.0..2.0)
        val f2 = channel()
        var settled = 0.0
        for (i in 0 until 300) settled = f2.filter(1.0, 0.0)
        assertEquals(1.0, settled, 1e-9)
    }

    // --- determinism ---
    @Test fun deterministicAcrossRuns() {
        fun run(): List<Double> {
            val f = channel()
            return (0 until 100).map { f.filter(if (it % 3 == 0) 3.0 else 1.0, it * 8.0) }
        }
        assertEquals(run(), run())
    }

    // --- alpha math ---
    @Test fun alphaIsZeroForInfiniteCutoffTimeAndUnitForInfiniteCutoff() {
        assertEquals(1.0, OneEuroChannel.alpha(1e12, 0.008), 1e-6)
        assertEquals(0.0, OneEuroChannel.alpha(1e-12, 0.008), 1e-6)
    }

    @Test fun alphaMatchesExactDiscreteSolution() {
        // alpha = 1/(1+τ/dt) is the exact discrete one-pole 1 − e^(−dt/τ) to first order; the
        // relative gap between the two forms is r/2 with r = dt/τ, so at touch sampling rates
        // (r below ~0.2) they agree to within a few percent, and never diverge past a third.
        for ((hz, ms) in listOf(15.0 to 2.0, 60.0 to 2.0, 1.0 to 8.0, 15.0 to 8.0, 40.0 to 4.0)) {
            val a = OneEuroChannel.alphaMs(hz, ms)
            val exact = oneEuroExactAlpha(hz, ms)
            assertTrue(a <= exact)
            assertTrue(abs(a - exact) / exact < 0.35)
        }
        // Tight agreement where the sampling outruns the cutoff: r = dt*2*pi*15/2 ≈ 0.19.
        val a = OneEuroChannel.alphaMs(15.0, 2.0)
        val exact = oneEuroExactAlpha(15.0, 2.0)
        assertTrue(abs(a - exact) / exact < 0.12)
    }

    // --- mode parsing ---
    @Test fun strokeFilterModeParsesIdsAndFallsBack() {
        assertEquals(StrokeFilterMode.EMA, StrokeFilterMode.fromId("ema"))
        assertEquals(StrokeFilterMode.ONE_EURO, StrokeFilterMode.fromId("one_euro"))
        assertEquals(StrokeFilterMode.EMA, StrokeFilterMode.fromId("bogus"))
        assertEquals(StrokeFilterMode.EMA, StrokeFilterMode.fromId(null))
    }

    @Test fun defaultTuningReproducesEmaLagAtReferenceSpacing() {
        // The default cutoff was chosen so one frame at 120 Hz smooths about as strongly as the
        // arc-length EMA does at its reference 1.5 px spacing (factor 1 − e^-1 ≈ 0.63).
        val a = OneEuroChannel.alphaMs(StrokeFilter.DEFAULT_MIN_CUTOFF_HZ, 1000.0 / 120.0)
        assertTrue("default one-euro alpha $a should sit near the EMA's 0.63", a in 0.5..0.8)
    }
}
