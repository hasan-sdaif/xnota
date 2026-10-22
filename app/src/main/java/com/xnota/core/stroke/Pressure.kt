package com.xnota.core.stroke

import kotlin.math.pow

/**
 * Pressure shaping (the fork's ink-engine preferences): a **deadzone** that floors the light end,
 * a **gamma** that reshapes the response after the logistic S-curve, and a smoothing **strength**
 * that adds an EMA over the pressure channel alone.
 *
 * Deadzone and gamma are render-time transforms of the *stored raw* pressure, like the paper-aware
 * ink transform: nothing is written back to the stroke, so the note renders the same forever after.
 */
object Pressure {

    /** Highest deadzone the preference allows, as a fraction of full pressure. */
    const val DEADZONE_MAX = 0.20

    /** Gamma range the preference allows. Below 1 hardens the light end; above 1 softens it. */
    const val GAMMA_MIN = 0.5
    const val GAMMA_MAX = 2.0

    /** Highest pressure-smoothing strength the preference allows (percent). */
    const val SMOOTHING_MAX = 100.0

    /**
     * The deadzone: pressures below [fraction] map to 0 (a hovering nib leaves no ink), everything
     * above is rescaled to span the full range, so the pen's active range is not compressed.
     */
    fun applyDeadzone(pressure: Double, fraction: Double): Double {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0.0) return pressure.coerceIn(0.0, 1.0)
        val p = pressure.coerceIn(0.0, 1.0)
        if (f >= 1.0) return if (p >= 1.0) 1.0 else 0.0 // a full deadzone keeps only a hard press
        if (p <= f) return 0.0
        return ((p - f) / (1.0 - f)).coerceIn(0.0, 1.0)
    }

    /**
     * The gamma: `x^gamma` on the reshaped response, where x is already the logistic S-curve's
     * output in `[0, 1]`. `gamma == 1` is the identity; below 1 pushes values up (light presses
     * reach visible width sooner — "harder"), above 1 pushes them down (a softer, airier pen).
     */
    fun applyGamma(x: Double, gamma: Double): Double {
        val g = gamma.coerceIn(0.1, 10.0)
        val v = x.coerceIn(0.0, 1.0)
        return if (g == 1.0) v else v.pow(g)
    }

    /**
     * The extra pressure-only EMA's per-sample factor for a preference strength in `[0, 100]`:
     * 0 is the identity (the historical pipeline), 100 is a factor of 0.1 — the pressure channel
     * then takes ~10 samples to settle, which reads as a pen that eases into its weight.
     * The position channel is never touched by this.
     */
    fun smoothingAlpha(strengthPercent: Double): Double {
        val s = strengthPercent.coerceIn(0.0, 100.0)
        if (s <= 0.0) return 1.0
        return 1.0 - 0.9 * (s / 100.0)
    }

    /** One step of the pressure-only EMA. */
    fun smoothStep(prev: Double, cur: Double, strengthPercent: Double): Double {
        val a = smoothingAlpha(strengthPercent)
        if (a >= 1.0) return cur
        return a * cur + (1.0 - a) * prev
    }

    /** The whole-channel form of [smoothStep]; empty passes through. */
    fun smooth(values: DoubleArray, strengthPercent: Double): DoubleArray {
        if (values.isEmpty() || strengthPercent <= 0.0) return values
        val out = DoubleArray(values.size)
        out[0] = values[0]
        for (i in 1 until values.size) out[i] = smoothStep(out[i - 1], values[i], strengthPercent)
        return out
    }
}

/**
 * Deadzone + gamma + pressure-smoothing as one bundle the engine threads alongside the tool's
 * pressure-response curve. The default is the historical identity everywhere.
 */
data class PressureShape(
    /** Light-end deadzone as a fraction of full pressure, `[0, 0.20]`. */
    val deadzone: Double = 0.0,
    /** Response gamma applied after the logistic reshape, `[0.5, 2.0]`. */
    val gamma: Double = 1.0,
    /** Extra pressure-only EMA strength, `[0, 100]`. 0 leaves the channel as the arc-smoother left it. */
    val smoothing: Double = 0.0,
) {
    /** The raw pressure as the pipeline should read it: deadzone applied, clamped. */
    fun input(pressure: Double): Double = Pressure.applyDeadzone(pressure, deadzone)

    /** The logistic-reshaped value with this shape's gamma applied ([Pressure.applyGamma]). */
    fun reshaped(logistic: Double): Double = Pressure.applyGamma(logistic, gamma)

    companion object {
        val DEFAULT = PressureShape()
    }
}
