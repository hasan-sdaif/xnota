package com.xnota.core.stroke

/**
 * Ink roughness (the fork's per-tool `roughness` param, 0–100): deterministic, seeded jitter of
 * the ink's edge — the half-width breathes by a fraction of a pixel to a couple of pixels, which
 * reads as pencil grain or dry-marker drag rather than as a wobble.
 *
 * The jitter is a hash of a **stable seed derived from the stroke's own data** plus the sample
 * index, so it is identical on every re-render, every zoom, every reload and every export —
 * nothing is random at draw time. Two strokes that start at the same point with the same width
 * share a grain pattern, which no one can see but the tests can rely on.
 *
 * Pure functions only; the engine multiplies its half-widths by [widthFactor].
 */
object StrokeGrain {

    /** Width jitter amplitude at roughness 100, in content px at the stroke's draw zoom. */
    const val MAX_AMPLITUDE_PX = 1.5

    /**
     * The amplitude in content px for a roughness slider value in `[0, 100]`, scaled by the
     * stroke's [smoothScale] so the grain is a property of the *gesture* like every other
     * arc-length constant — pencil grain drawn at 4x zoom is the same size on screen.
     */
    fun amplitudePx(roughnessPercent: Double, smoothScale: Double): Double =
        (roughnessPercent.coerceIn(0.0, 100.0) / 100.0) * MAX_AMPLITUDE_PX * smoothScale.coerceAtLeast(0.0)

    /**
     * One jitter value in `[-amp, +amp]`, a pure function of ([seed], [i]): the same stroke
     * renders the same grain forever. `amp <= 0` is exactly zero — roughness off is off, with no
     * rounding residue.
     */
    fun jitter(seed: Long, i: Int, amp: Double): Double {
        if (amp <= 0.0) return 0.0
        return (unit(seed, i) * 2.0 - 1.0) * amp
    }

    /**
     * The half-width multiplier at sample [i]: `1 + jitter/2`, kept positive. Jittering the width
     * (rather than the centreline) means hit-testing and bounds move by at most the amplitude,
     * while the rendered edge reads as grain on both render paths — the swept disc of the paged
     * canvas and the rails of the GL tessellation both read the half-width.
     */
    fun widthFactor(seed: Long, i: Int, amp: Double): Double {
        val f = 1.0 + jitter(seed, i, amp) * 0.5
        return if (f > 0.05) f else 0.05
    }

    /**
     * A stable seed from the stroke's own first sample and style: the pen-down point and the
     * width are enough to scatter patterns across strokes while staying constant for one stroke
     * from its first sample (so the live ribbon and the committed rebuild agree — the "last
     * sample" a fuller hash would use is not known while the pen is down).
     */
    fun seedOf(x0: Double, y0: Double, baseWidth: Double, smoothScale: Double): Long {
        var h = 1125899906842597L // FNV-ish basis in long space
        h = mix(h, x0); h = mix(h, y0); h = mix(h, baseWidth); h = mix(h, smoothScale)
        return h
    }

    /**
     * A second stable seed for the scatter channel, so scatter offsets and width grain never
     * correlate (they would read as a repeated motif if they did).
     */
    fun scatterSeedOf(x0: Double, y0: Double, baseWidth: Double, smoothScale: Double): Long {
        var h = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15 as a signed long
        h = mix(h, x0); h = mix(h, y0); h = mix(h, baseWidth); h = mix(h, smoothScale)
        return h
    }

    /** A uniform double in `[0, 1)` from the (seed, index) hash. */
    fun unit(seed: Long, i: Int): Double =
        (hash(seed, i) ushr 11) * (1.0 / (1L shl 53))

    /** SplitMix-style finalizer over the seed and index together. */
    fun hash(seed: Long, i: Int): Long {
        val golden = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15 as a signed long
        var z = seed + (i.toLong() * golden)
        z = (z xor (z ushr 30)) * -0x40A7B892F83BD5BFL // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x2B58BFA6D392DCDDL // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    private fun mix(h: Long, v: Double): Long {
        val bits = java.lang.Double.doubleToLongBits(v)
        return (h xor bits) * 0x100000001B3L
    }
}
