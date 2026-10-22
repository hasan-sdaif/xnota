package com.xnota.core.stroke

/**
 * Adaptive smoothing (the fork's `adaptive_ink_smoothing` preference, on by default): scale the
 * arc-length low-pass's lag by the instantaneous speed, so **fast** strokes get *less* lag —
 * corners stay crisp where the EMA would round them — and **slow** strokes get *more* smoothing —
 * a deliberate line steadies instead of tracking every tremor.
 *
 * The speed is measured in content px per ms and normalized by the stroke's draw zoom
 * (÷ [smoothScale]), so the breakpoints are speeds *of the hand* rather than of the page: a
 * 0.3 px/ms dash reads the same whether it was drawn at 100% or 400%.
 *
 * Pure functions, so the multiplier curve is pinned by unit tests without a canvas.
 */
object AdaptiveSmoothing {

    /**
     * Gesture speed (px/ms, zoom-normalized) at and below which the lag is at its maximum: a pen
     * held still, lettering, careful shading. The units follow the speed pen's vocabulary
     * ([StrokeEngine.SPEED_HI] is a fast scribble at 0.6 dp/ms).
     */
    const val SLOW_PX_PER_MS = 0.05

    /** Gesture speed at and above which the lag is at its minimum: a fast slash or underline. */
    const val FAST_PX_PER_MS = 0.5

    /** The lag multiplier's floor — the least lag a fast stroke ever gets. */
    const val MIN_FACTOR = 0.6

    /** The lag multiplier's ceiling — the most smoothing a slow stroke ever gets. */
    const val MAX_FACTOR = 1.4

    /**
     * The lag multiplier for an instantaneous speed: [MAX_FACTOR] at rest, easing to
     * [MIN_FACTOR] by [FAST_PX_PER_MS]. Monotone non-increasing, always inside
     * `[MIN_FACTOR, MAX_FACTOR]`. A speed of 1.0 with default breakpoints sits in the fast band,
     * matching the intuition that most of a letter's body is drawn faster than its serifs.
     */
    fun lagFactor(speedPxPerMs: Double): Double {
        val v = if (speedPxPerMs <= 0.0) 0.0 else speedPxPerMs
        val t = ((v - SLOW_PX_PER_MS) / (FAST_PX_PER_MS - SLOW_PX_PER_MS)).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t) // smoothstep: no kink where slow hands off to fast
        return MAX_FACTOR - (MAX_FACTOR - MIN_FACTOR) * eased
    }

    /**
     * The multiplier for one arc step: speed = raw travel [stepPx] over [dtMs], normalized by
     * [smoothScale] (the stroke's draw zoom, as `1/zoom` capped at 1 — see
     * [com.xnota.core.model.Stroke.smoothScale]). A degenerate dt falls back to the reference
     * spacing, where the multiplier is whatever [lagFactor] says about ordinary writing speed.
     */
    fun stepFactor(stepPx: Double, dtMs: Double, smoothScale: Double): Double {
        val dt = if (dtMs > 0.0) dtMs else 1000.0 / StrokeFilter.DEFAULT_FREQ_HZ
        val zoom = if (smoothScale > 0.0) smoothScale else 1.0
        return lagFactor(stepPx / zoom / dt)
    }
}
