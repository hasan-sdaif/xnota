package com.xnota.core.stroke

import kotlin.math.hypot

/**
 * Input prediction (the fork's `ink_prediction` preference): extrapolate the smoothed pen tip one
 * frame ahead, and draw **only the wet ink** there, so the line reads as reaching the stylus a
 * frame sooner than the display pipeline can really show it. One frame is ~17 ms.
 *
 * The predicted tail is never part of the stroke: it is not fed back into the filter, not stored
 * in the samples, and [retract] is what the renderer's pen-up path calls to pull it back — after
 * which the committed geometry is exactly the geometry that would have been drawn with prediction
 * off, by construction. Zero net displacement at pen-up is the class's contract, and its test.
 *
 * The velocity estimate is an EMA over the per-sample velocity of the **smoothed** points (the
 * same points the ribbon draws), clamped in speed and in length: a mispredicted jump would be
 * worse than the latency it hides.
 *
 * Pure, deterministic and clock-free: the caller feeds each smoothed sample with its timestamp.
 */
class InputPredictor(
    /** How far ahead to extrapolate, in ms. One frame at 60 Hz. */
    val horizonMs: Double = FRAME_HORIZON_MS,
    /** Velocity clamp, px/ms: beyond this the estimate is deemed unreliable and frozen. */
    val maxSpeedPxPerMs: Double = MAX_SPEED_PX_PER_MS,
    /** Length clamp on the predicted offset, px: the tail can never be farther than this. */
    val maxTailPx: Double = MAX_TAIL_PX,
) {
    private var primed = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastTMs = 0.0
    private var velX = 0.0
    private var velY = 0.0

    /**
     * Feed one smoothed sample, [tMs] milliseconds after the stroke's first sample. The first
     * sample only primes the filter; the second gives the first velocity, so nothing is predicted
     * until two samples are in.
     */
    fun update(x: Double, y: Double, tMs: Double) {
        if (!primed) {
            primed = true
            lastX = x
            lastY = y
            lastTMs = tMs
            velX = 0.0
            velY = 0.0
            return
        }
        val dt = tMs - lastTMs
        if (dt > 0.0) {
            val vx = (x - lastX) / dt
            val vy = (y - lastY) / dt
            val speed = hypot(vx, vy)
            if (speed <= maxSpeedPxPerMs) {
                // An EMA over the velocity: a single slow sample mid-sweep must not snap the tip
                // back — that flicker is exactly what the predictor exists to remove.
                velX = VEL_ALPHA * vx + (1.0 - VEL_ALPHA) * velX
                velY = VEL_ALPHA * vy + (1.0 - VEL_ALPHA) * velY
            }
        }
        lastX = x
        lastY = y
        lastTMs = tMs
    }

    /**
     * The predicted tip's offset from the last smoothed point (the tail to draw), or null while
     * there is nothing to predict from. Never longer than [maxTailPx].
     */
    fun tail(): Pair<Double, Double>? {
        if (!primed) return null
        var dx = velX * horizonMs
        var dy = velY * horizonMs
        val len = hypot(dx, dy)
        if (len > maxTailPx && len > 0.0) {
            dx *= maxTailPx / len
            dy *= maxTailPx / len
        }
        return dx to dy
    }

    /**
     * Pen-up: hand back the tail that was showing (so the caller can confirm it drops it), then
     * forget everything. The next stroke starts from a clean filter. A second call — or a call
     * with nothing predicted — returns the zero tail, the guarantee that after a retract the
     * predictor contributes no displacement to anything.
     */
    fun retract(): Pair<Double, Double> {
        val t = tail() ?: ZERO_TAIL
        reset()
        return t
    }

    /** Forget all state without reporting a tail (an aborted stroke, a cancelled gesture). */
    fun reset() {
        primed = false
        velX = 0.0
        velY = 0.0
    }

    companion object {
        /** One frame at 60 Hz — the display's own latency is what the tail stands in for. */
        const val FRAME_HORIZON_MS = 17.0

        /** Faster than a thrown swipe; velocity readings past this are treated as noise. */
        const val MAX_SPEED_PX_PER_MS = 4.0

        /** The tail can never be longer than a couple of frames of fast travel. */
        const val MAX_TAIL_PX = 40.0

        /** Per-sample weight of a new velocity reading in the velocity EMA. */
        const val VEL_ALPHA = 0.4

        private val ZERO_TAIL = 0.0 to 0.0
    }
}
