package com.xnota.core.stroke

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * The classic one-euro filter (Casiez, Roussel & Vogel, CHI 2012): a low-pass on the signal whose
 * cutoff rises with the measured speed, so slow input is steadied hard and fast input is tracked
 * with little lag. The fork's alternative to the arc-length EMA ([StrokeEngine.emaByArc]) behind
 * the `stroke_filter` preference: a "smarter" jitter remover whose tuning is in **Hz** rather than
 * in px of trailing lag.
 *
 * One channel of one value. A stroke filters its x, y and pressure through three independent
 * instances, as the EMA pipeline smooths those channels independently too.
 *
 * Pure and deterministic: same samples in, same samples out, whatever the thread or the render
 * pass, which is what lets the live ribbon ([WetRibbon]) carry the filter incrementally and the
 * committed stroke rebuild the identical curve through [StrokeEngine.build].
 */
class OneEuroChannel(
    /** Assumed sample rate when a sample stream carries no timestamps; also the fallback dt. */
    val freqHz: Double = StrokeFilter.DEFAULT_FREQ_HZ,
    /** Cutoff (Hz) at rest. Lower = steadier slow input and more lag on slow motion. */
    val minCutoff: Double = StrokeFilter.DEFAULT_MIN_CUTOFF_HZ,
    /** Cutoff gained per unit of speed (per second, in the channel's own units). Higher = the
     *  filter "lets go" sooner when the pen moves. */
    val beta: Double = StrokeFilter.DEFAULT_BETA,
    /** Cutoff (Hz) of the low-pass on the speed estimate itself. */
    val dCutoff: Double = StrokeFilter.DEFAULT_D_CUTOFF_HZ,
) {
    private var primed = false
    private var hatX = 0.0
    private var hatDx = 0.0
    private var lastRaw = 0.0
    private var lastTMs = 0.0

    /**
     * Filter one sample taken [tMs] after the stroke's first sample. The first value passes
     * through unchanged (there is nothing yet to filter against). A stream that carries no
     * timing (every [tMs] equal) is filtered at [freqHz], exactly as the paper's fixed-rate form.
     */
    fun filter(x: Double, tMs: Double): Double {
        if (!primed) {
            primed = true
            hatX = x
            hatDx = 0.0
            lastRaw = x
            lastTMs = tMs
            return x
        }
        val dt = if (tMs > lastTMs) (tMs - lastTMs) / 1000.0 else 1.0 / freqHz
        lastTMs = tMs
        // The speed estimate is filtered first, then sets this sample's cutoff.
        val dx = (x - lastRaw) / dt
        val aD = alpha(dCutoff, dt)
        hatDx = aD * dx + (1.0 - aD) * hatDx
        val cutoff = minCutoff + beta * abs(hatDx)
        val a = alpha(cutoff, dt)
        hatX = a * x + (1.0 - a) * hatX
        lastRaw = x
        return hatX
    }

    companion object {
        /**
         * The one-euro's smoothing factor for a first-order low-pass of [cutoff] Hz over a step of
         * [dt] seconds: `1 / (1 + τ/dt)` with `τ = 1 / (2π·cutoff)`. Pure so the tests can pin it.
         */
        fun alpha(cutoffHz: Double, dtSec: Double): Double {
            val tau = 1.0 / (2.0 * Math.PI * max(cutoffHz, 1e-9))
            return 1.0 / (1.0 + tau / max(dtSec, 1e-9))
        }

        /** [alpha] over a step quoted in milliseconds, the unit the callers actually hold. */
        fun alphaMs(cutoffHz: Double, dtMs: Double): Double = alpha(cutoffHz, dtMs / 1000.0)

        /**
         * The closed-form response of [alpha] that an exponential decay must match — kept as the
         * tests' cross-check that the constant-signal limit is exact (`alpha(∞·dt) → 1`).
         */
        fun convergesToInput(cutoffHz: Double, dtMs: Double): Boolean = alphaMs(cutoffHz, dtMs) > 0.0
    }
}

/** Re-export shape so engine call sites read `OneEuro.alpha` without the channel class. */
internal val oneEuroAlpha = OneEuroChannel.Companion::alpha

/**
 * The excluded middle of `exp` for [alpha]'s derivation, kept for documentation: the smoothing
 * factor is the exact discrete solution of `dy/dt = (x - y)/τ`, i.e. `1 - e^(-dt/τ)`.
 */
internal fun oneEuroExactAlpha(cutoffHz: Double, dtMs: Double): Double {
    val tau = 1.0 / (2.0 * Math.PI * max(cutoffHz, 1e-9))
    return 1.0 - exp(-max(dtMs / 1000.0, 1e-9) / tau)
}
