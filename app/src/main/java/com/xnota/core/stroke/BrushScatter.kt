package com.xnota.core.stroke

import kotlin.math.hypot

/**
 * Brush scatter (the fork's per-tool `scatter` param, 0–100): a deterministic, seeded offset
 * applied to each raw sample **before** the engine smooths and tessellates it, so a single
 * stroke reads as many light passes — a spray, a dry felt tip, a shaded pencil.
 *
 * Same determinism contract as [StrokeGrain]: the offsets are a hash of a stroke-stable seed and
 * the sample index, never of draw-time randomness, so re-renders are pixel-identical. The first
 * sample is never scattered — the pen-down point is where the writer put it, and the eye lands
 * there first.
 *
 * Pure functions only; the engine adds [offset] to the raw samples as they enter the pipeline.
 */
object BrushScatter {

    /** Scatter radius at strength 100, in content px at the stroke's draw zoom. */
    const val MAX_RADIUS_PX = 6.0

    /** The radius in content px for a scatter slider value in `[0, 100]`, zoom-normalized. */
    fun radiusPx(strengthPercent: Double, smoothScale: Double): Double =
        (strengthPercent.coerceIn(0.0, 100.0) / 100.0) * MAX_RADIUS_PX * smoothScale.coerceAtLeast(0.0)

    /**
     * The scatter offset for sample [i]: a seeded unit vector scaled to at most [radius]. Sample 0
     * is pinned (zero offset), and the vector's direction is independent of its length so scaling
     * the strength moves points along their own rays rather than reshuffling them.
     */
    fun offset(seed: Long, i: Int, radius: Double): Pair<Double, Double> {
        if (radius <= 0.0 || i <= 0) return 0.0 to 0.0
        val angle = StrokeGrain.unit(seed, 2 * i) * 2.0 * Math.PI
        val r = radius * StrokeGrain.unit(seed, 2 * i + 1)
        return r * kotlin.math.cos(angle) to r * kotlin.math.sin(angle)
    }

    /**
     * The mean offset magnitude across the first [n] samples — the number the tuning UI could
     * quote, and the tests' handle for "stronger scatter moves samples farther on average".
     */
    fun meanMagnitude(seed: Long, n: Int, radius: Double): Double {
        if (n <= 1 || radius <= 0.0) return 0.0
        var sum = 0.0
        var count = 0
        for (i in 1 until n) {
            val (dx, dy) = offset(seed, i, radius)
            sum += hypot(dx, dy)
            count++
        }
        return if (count == 0) 0.0 else sum / count
    }
}
