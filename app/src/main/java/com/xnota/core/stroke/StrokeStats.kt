package com.xnota.core.stroke

import kotlin.math.hypot

/**
 * Stroke statistics (the fork's debug overlay): samples/s, mean speed, mean pressure and length
 * of the last committed stroke — the numbers that explain *why* ink looks the way it does, since
 * every engine rule above keys off exactly these.
 *
 * Pure; the GL canvas's debug HUD (and anything else curious) reads a [Summary] per stroke.
 * Speeds are content px per ms along the raw samples; the duration is from the sample timestamps
 * and is 0 when the stroke carries none.
 */
object StrokeStats {

    data class Summary(
        val sampleCount: Int,
        /** Elapsed ms from the first to the last sample; 0 when the stroke carries no timing. */
        val durationMs: Double,
        /** Samples per second over [durationMs]; 0 when there is no timing to divide by. */
        val samplesPerSec: Double,
        /** Mean raw speed over the inter-sample steps, content px/ms. */
        val meanSpeedPxPerMs: Double,
        /** Mean raw pressure in `[0, 1]`. */
        val meanPressure: Double,
        /** Total raw travel, content px. */
        val lengthPx: Double,
    )

    fun of(samples: List<Sample>): Summary {
        val n = samples.size
        if (n == 0) return Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0)
        if (n == 1) return Summary(1, 0.0, 0.0, 0.0, samples[0].pressure.coerceIn(0.0, 1.0), 0.0)

        var length = 0.0
        var pressure = 0.0
        var travel = 0.0
        var time = 0.0
        for (i in 0 until n) {
            pressure += samples[i].pressure.coerceIn(0.0, 1.0)
            if (i > 0) {
                val step = hypot(samples[i].x - samples[i - 1].x, samples[i].y - samples[i - 1].y)
                length += step
                val dt = samples[i].t - samples[i - 1].t
                time += dt
                // Only steps that span real time contribute speed; a zero-dt duplicate would
                // otherwise read as infinite and poison the mean.
                if (dt > 0.0) travel += step
            }
        }
        val duration = (samples[n - 1].t - samples[0].t).coerceAtLeast(0.0)
        val meanSpeed = if (time > 0.0) travel / time else 0.0
        val rate = if (duration > 0.0) (n - 1) * 1000.0 / duration else 0.0
        return Summary(n, duration, rate, meanSpeed, pressure / n, length)
    }
}
