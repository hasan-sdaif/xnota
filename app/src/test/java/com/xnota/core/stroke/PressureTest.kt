package com.xnota.core.stroke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pressure shaping trio: deadzone boundaries, gamma identity, pressure-only EMA. */
class PressureTest {

    // --- deadzone ---
    @Test fun deadzoneZeroIsIdentity() {
        for (p in listOf(0.0, 0.1, 0.5, 1.0)) {
            assertEquals(p, Pressure.applyDeadzone(p, 0.0), 1e-12)
        }
    }

    @Test fun deadzoneFloorsTheLightEnd() {
        val dz = 0.2
        assertEquals(0.0, Pressure.applyDeadzone(0.0, dz), 1e-12)
        assertEquals(0.0, Pressure.applyDeadzone(0.199, dz), 1e-12)
        assertEquals(0.0, Pressure.applyDeadzone(0.2, dz), 1e-12)
    }

    @Test fun deadzoneRescalesTheActiveRange() {
        val dz = 0.2
        // Above the floor the full input range maps onto the full output range.
        assertEquals(0.0, Pressure.applyDeadzone(0.200001, dz), 2e-6) // (0.200001-dz)/0.8 ≈ 1.25e-6
        assertEquals(1.0, Pressure.applyDeadzone(1.0, dz), 1e-12)
        assertEquals(0.5, Pressure.applyDeadzone(0.6, dz), 1e-12)
    }

    @Test fun deadzoneClampsOutOfRangeInput() {
        assertEquals(0.0, Pressure.applyDeadzone(-0.5, 0.2), 1e-12)
        assertEquals(1.0, Pressure.applyDeadzone(1.5, 0.2), 1e-12)
    }

    @Test fun deadzoneFullRangeCollapsesToZeroOrOne() {
        assertEquals(0.0, Pressure.applyDeadzone(0.5, 1.0), 1e-12)
        assertEquals(1.0, Pressure.applyDeadzone(1.0, 1.0), 1e-12)
    }

    // --- gamma ---
    @Test fun gammaOneIsIdentity() {
        for (x in listOf(0.0, 0.25, 0.5, 0.9, 1.0)) {
            assertEquals(x, Pressure.applyGamma(x, 1.0), 1e-12)
        }
    }

    @Test fun gammaBelowOneLiftsGammaAboveOneLowers() {
        assertTrue(Pressure.applyGamma(0.25, 0.5) > 0.25)
        assertTrue(Pressure.applyGamma(0.25, 2.0) < 0.25)
    }

    @Test fun gammaPreservesEndpointsAndMonotonicity() {
        assertEquals(0.0, Pressure.applyGamma(0.0, 0.6), 1e-12)
        assertEquals(1.0, Pressure.applyGamma(1.0, 0.6), 1e-12)
        val g = 0.7
        var prev = -1.0
        for (i in 0..20) {
            val v = Pressure.applyGamma(i / 20.0, g)
            assertTrue(v >= prev)
            prev = v
        }
    }

    // --- pressure-only EMA ---
    @Test fun smoothingZeroIsIdentity() {
        assertEquals(1.0, Pressure.smoothingAlpha(0.0), 1e-12)
        val values = doubleArrayOf(0.1, 0.9, 0.2, 0.7)
        assertEquals(values.toList(), Pressure.smooth(values, 0.0).toList())
    }

    @Test fun smoothingHundredIsHeavy() {
        assertEquals(0.1, Pressure.smoothingAlpha(100.0), 1e-12)
        val out = Pressure.smooth(doubleArrayOf(0.0, 1.0), 100.0)
        assertEquals(0.1, out[1], 1e-12)
    }

    @Test fun smoothingNeverOvershoots() {
        for (s in listOf(1.0, 25.0, 50.0, 99.0)) {
            val out = Pressure.smooth(doubleArrayOf(0.0, 1.0), s)
            assertTrue(out[1] in 0.0..1.0)
        }
    }

    @Test fun smoothingAlphaIsMonotone() {
        var prev = 2.0
        for (s in 0..100 step 5) {
            val a = Pressure.smoothingAlpha(s.toDouble())
            assertTrue(a <= prev)
            prev = a
        }
    }

    // --- the bundle ---
    @Test fun pressureShapeDefaultIsIdentity() {
        val shape = PressureShape.DEFAULT
        assertEquals(0.0, shape.deadzone, 0.0)
        assertEquals(1.0, shape.gamma, 0.0)
        assertEquals(0.0, shape.smoothing, 0.0)
        assertEquals(0.42, shape.input(0.42), 1e-12)
        assertEquals(0.42, shape.reshaped(0.42), 1e-12)
    }

    @Test fun pressureShapeComposesDeadzoneThenGamma() {
        val shape = PressureShape(deadzone = 0.2, gamma = 2.0)
        // Input side: 0.6 -> 0.5 after the deadzone.
        assertEquals(0.5, shape.input(0.6), 1e-12)
        // Output side: gamma applied to the logistic's result, not to the raw pressure.
        assertEquals(0.25, shape.reshaped(0.5), 1e-12)
    }
}

/** Speed-scaled lag: fast ink crisper, slow ink steadier, always bounded. */
class AdaptiveSmoothingTest {

    @Test fun fastSegmentLagsLessThanSlow() {
        val fast = AdaptiveSmoothing.lagFactor(1.0)
        val slow = AdaptiveSmoothing.lagFactor(0.01)
        assertTrue("fast $fast should lag less than slow $slow", fast < slow)
    }

    @Test fun factorsStayWithinBounds() {
        for (v in listOf(0.0, 0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0, 100.0, -3.0)) {
            val f = AdaptiveSmoothing.lagFactor(v)
            assertTrue(f >= AdaptiveSmoothing.MIN_FACTOR)
            assertTrue(f <= AdaptiveSmoothing.MAX_FACTOR)
        }
    }

    @Test fun monotoneNonIncreasing() {
        var prev = Double.MAX_VALUE
        var v = 0.0
        while (v <= 1.0) {
            val f = AdaptiveSmoothing.lagFactor(v)
            assertTrue(f <= prev + 1e-12)
            prev = f
            v += 0.01
        }
    }

    @Test fun endpointsHitTheBounds() {
        assertEquals(AdaptiveSmoothing.MAX_FACTOR, AdaptiveSmoothing.lagFactor(0.0), 1e-12)
        assertEquals(AdaptiveSmoothing.MIN_FACTOR, AdaptiveSmoothing.lagFactor(0.5), 1e-12)
        assertEquals(AdaptiveSmoothing.MIN_FACTOR, AdaptiveSmoothing.lagFactor(10.0), 1e-12)
    }

    @Test fun stepFactorNormalizesByDrawZoom() {
        // The same physical speed at 4x zoom (smoothScale 0.25) reads as the same gesture speed.
        val direct = AdaptiveSmoothing.stepFactor(4.0, 10.0, 1.0)   // 0.4 px/ms
        val zoomed = AdaptiveSmoothing.stepFactor(1.0, 10.0, 0.25)  // 1/4 the page px, same hand
        assertEquals(direct, zoomed, 1e-12)
    }

    @Test fun degenerateDtFallsBackToTheReferenceRate() {
        val zero = AdaptiveSmoothing.stepFactor(4.0, 0.0, 1.0)
        val ref = AdaptiveSmoothing.stepFactor(4.0, 1000.0 / 120.0, 1.0)
        assertEquals(ref, zero, 1e-12)
    }

    @Test fun adaptiveFastAndSlowPathsDiffer() {
        // The engine-level promise in one place: a synthetic fast segment's lag multiplier is
        // smaller than a slow one's, whatever the exact speeds.
        fun path(speed: Double) = (0..50).map { AdaptiveSmoothing.lagFactor(speed) }
        assertTrue(path(0.8).average() < path(0.02).average())
    }
}

/** The wet-ink predictor: toward the next sample while drawing, zero tail after retract. */
class InputPredictorTest {

    private fun feedConstantVelocity(p: InputPredictor, vPxPerMs: Double, n: Int, dtMs: Double = 8.0) {
        for (i in 0 until n) p.update(i * vPxPerMs * dtMs, 0.0, i * dtMs)
    }

    @Test fun predictionMovesTowardTheNextRealSample() {
        val p = InputPredictor()
        val v = 0.5 // px/ms
        val dt = 17.0 // exactly one frame: the next real sample lands where the horizon points
        for (i in 0 until 30) p.update(i * v * dt, 0.0, i * dt)
        val lastX = 29 * v * dt
        val nextX = 30 * v * dt
        val (dx, _) = p.tail()!!
        val predictedX = lastX + dx
        assertTrue(
            "predicted $predictedX should be nearer the next sample $nextX than $lastX was",
            abs(nextX - predictedX) < abs(nextX - lastX),
        )
        // And with dt == horizon the prediction should actually land on it.
        assertTrue(abs(nextX - predictedX) < 1.0)
    }

    @Test fun tailIsNullBeforeTwoSamples() {
        val p = InputPredictor()
        assertNull(p.tail())
        p.update(1.0, 1.0, 0.0)
        val t = p.tail()
        // After a single sample the velocity is still zero: the tail is zero-length or nothing.
        assertTrue(t == null || (t.first == 0.0 && t.second == 0.0))
    }

    @Test fun retractYieldsZeroNetDisplacementAtPenUp() {
        val p = InputPredictor()
        feedConstantVelocity(p, 0.8, 40)
        // The renderer was showing the tail; pen-up retracts it and reports what it dropped.
        val dropped = p.retract()
        // Nothing left: the predictor now contributes no displacement to anything.
        assertNull(p.tail())
        val second = p.retract()
        assertEquals(0.0, second.first, 0.0)
        assertEquals(0.0, second.second, 0.0)
        // Net: exactly one tail was ever handed out, and it is the one that was dropped — so the
        // committed stroke (which never contained it) is the whole drawn ink.
        assertTrue(dropped.first != 0.0 || dropped.second != 0.0)
    }

    @Test fun retractResetsForTheNextStroke() {
        val p = InputPredictor()
        feedConstantVelocity(p, 0.8, 40)
        p.retract()
        // The next stroke primes from scratch: its first sample cannot inherit the old velocity.
        p.update(100.0, 100.0, 0.0)
        val t = p.tail()
        assertTrue(t == null || (t.first == 0.0 && t.second == 0.0))
    }

    @Test fun stillPenPredictsNothing() {
        val p = InputPredictor()
        for (i in 0 until 50) p.update(10.0, 10.0, i * 8.0)
        val (dx, dy) = p.tail()!!
        assertEquals(0.0, dx, 1e-9)
        assertEquals(0.0, dy, 1e-9)
    }

    @Test fun tailIsClampedInLength() {
        val p = InputPredictor(maxTailPx = 10.0)
        feedConstantVelocity(p, 4.0, 40) // 4 px/ms × 17 ms = 68 px wanted
        val (dx, dy) = p.tail()!!
        assertTrue(kotlin.math.hypot(dx, dy) <= 10.0 + 1e-9)
    }

    @Test fun velocitySpikeDoesNotYankTheTail() {
        val p = InputPredictor()
        feedConstantVelocity(p, 0.2, 40)
        // One wild sample mid-stroke (a digitizer glitch): the EMA keeps the tail sane.
        p.update(300.0, 0.0, 41 * 8.0)
        val (dx, dy) = p.tail()!!
        assertTrue(kotlin.math.hypot(dx, dy) < p.maxTailPx)
    }

    @Test fun clampedSpeedFreezesTheEstimate() {
        val p = InputPredictor()
        feedConstantVelocity(p, InputPredictor.MAX_SPEED_PX_PER_MS * 3, 40)
        val (dx, _) = p.tail()!!
        // The velocity never entered the estimate, so the tail stays at zero.
        assertEquals(0.0, dx, 1e-9)
    }
}

/** Deterministic, bounded, zero-at-zero ink grain. */
class StrokeGrainTest {

    @Test fun deterministicAcrossCalls() {
        assertEquals(StrokeGrain.jitter(42L, 7, 1.0), StrokeGrain.jitter(42L, 7, 1.0), 0.0)
        assertEquals(StrokeGrain.widthFactor(42L, 7, 1.0), StrokeGrain.widthFactor(42L, 7, 1.0), 0.0)
    }

    @Test fun boundedWithinAmplitude() {
        for (i in 0 until 500) {
            val j = StrokeGrain.jitter(99L, i, 1.2)
            assertTrue(j in -1.2..1.2)
        }
    }

    @Test fun zeroAtZeroAmplitude() {
        assertEquals(0.0, StrokeGrain.jitter(42L, 5, 0.0), 0.0)
        assertEquals(1.0, StrokeGrain.widthFactor(42L, 5, 0.0), 0.0)
    }

    @Test fun widthFactorStaysPositive() {
        for (i in 0 until 500) {
            assertTrue(StrokeGrain.widthFactor(7L, i, 5.0) > 0.0)
        }
    }

    @Test fun variesAcrossIndices() {
        // A constant "jitter" would be a drift, not a grain.
        val values = (0 until 64).map { StrokeGrain.jitter(123L, it, 1.0) }.distinct()
        assertTrue(values.size > 32)
    }

    @Test fun seedsDifferAcrossStrokesButHoldWithinOne() {
        val a = StrokeGrain.seedOf(10.0, 20.0, 3.0, 1.0)
        val b = StrokeGrain.seedOf(10.0, 20.0, 3.0, 1.0)
        val c = StrokeGrain.seedOf(11.0, 20.0, 3.0, 1.0)
        assertEquals(a, b)
        assertTrue(a != c)
        assertTrue(StrokeGrain.scatterSeedOf(10.0, 20.0, 3.0, 1.0) != StrokeGrain.seedOf(10.0, 20.0, 3.0, 1.0))
    }

    @Test fun amplitudeScalesWithRoughnessAndZoom() {
        val low = StrokeGrain.amplitudePx(25.0, 1.0)
        val high = StrokeGrain.amplitudePx(100.0, 1.0)
        val zoomed = StrokeGrain.amplitudePx(100.0, 0.25)
        assertTrue(low < high)
        assertEquals(StrokeGrain.MAX_AMPLITUDE_PX, high, 1e-12)
        assertEquals(StrokeGrain.MAX_AMPLITUDE_PX * 0.25, zoomed, 1e-12)
    }
}

/** Deterministic, bounded, first-sample-pinned brush scatter. */
class BrushScatterTest {

    @Test fun deterministicAcrossCalls() {
        assertEquals(BrushScatter.offset(42L, 3, 2.0), BrushScatter.offset(42L, 3, 2.0))
    }

    @Test fun firstSampleIsNeverScattered() {
        assertEquals(0.0 to 0.0, BrushScatter.offset(42L, 0, 5.0))
    }

    @Test fun zeroAtZeroStrength() {
        assertEquals(0.0 to 0.0, BrushScatter.offset(42L, 5, 0.0))
    }

    @Test fun boundedWithinRadius() {
        for (i in 1 until 500) {
            val (dx, dy) = BrushScatter.offset(7L, i, 3.0)
            assertTrue(kotlin.math.hypot(dx, dy) <= 3.0 + 1e-9)
        }
    }

    @Test fun meanMagnitudeGrowsWithStrength() {
        val seed = 5L
        val low = BrushScatter.meanMagnitude(seed, 200, 1.0)
        val high = BrushScatter.meanMagnitude(seed, 200, 4.0)
        assertTrue(low < high)
        // A uniform disc of radius r has mean magnitude 2r/3; allow generous slack for a small
        // sample but require it to be in the right band.
        assertTrue(high in 1.0..4.0)
    }

    @Test fun directionIndependentOfLength() {
        val (x1, y1) = BrushScatter.offset(9L, 4, 1.0)
        val (x4, y4) = BrushScatter.offset(9L, 4, 4.0)
        if (kotlin.math.hypot(x1, y1) > 1e-6) {
            assertEquals(x1, x4 / 4.0, 1e-9)
            assertEquals(y1, y4 / 4.0, 1e-9)
        }
    }
}

/** The nib-angle mixing: −1 follows travel, a lock holds sin(lock°). */
class NibAngleTest {

    @Test fun followTravelPassesTheTangentThrough() {
        assertEquals(0.7, NibAngle.directionTerm(0.7, NibAngle.FOLLOW_TRAVEL), 1e-12)
        assertEquals(-1.0, NibAngle.directionTerm(-1.0, -1.0), 1e-12)
    }

    @Test fun lockHoldsTheFixedWorldAngle() {
        assertEquals(0.0, NibAngle.directionTerm(0.9, 0.0), 1e-9) // thin edge to +x
        assertEquals(1.0, NibAngle.directionTerm(0.0, 90.0), 1e-9) // broad face down-screen
        assertEquals(-1.0, NibAngle.directionTerm(1.0, 270.0), 1e-9)
        assertEquals(kotlin.math.sin(Math.toRadians(45.0)), NibAngle.directionTerm(-0.5, 45.0), 1e-9)
    }

    @Test fun lockIgnoresTheTravel() {
        for (ty in listOf(-1.0, -0.3, 0.0, 0.5, 1.0)) {
            assertEquals(NibAngle.directionTerm(ty, 90.0), NibAngle.directionTerm(0.0, 90.0), 1e-12)
        }
    }

    @Test fun lockWrapsPast360() {
        assertEquals(NibAngle.directionTerm(0.0, 90.0), NibAngle.directionTerm(0.0, 450.0), 1e-9)
    }
}
