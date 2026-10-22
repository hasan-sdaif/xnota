package com.xnota.core.stroke

/**
 * Which low-pass the ink pipeline smooths position and pressure through (the fork's
 * `stroke_filter` preference).
 *
 *  - [EMA] is the historical arc-length exponential ([StrokeEngine.emaByArc]): the lag is a fixed
 *    *distance*, so the ink feels the same however fast the hand moves. The default.
 *  - [ONE_EURO] swaps in the one-euro filter ([OneEuroChannel]): the lag is a fixed *time*, and it
 *    shrinks as the pen speeds up, which some hands read as more faithful at speed.
 */
enum class StrokeFilterMode(val id: String) {
    EMA("ema"),
    ONE_EURO("one_euro");

    companion object {
        fun fromId(id: String?): StrokeFilterMode = entries.firstOrNull { it.id == id } ?: EMA
    }
}

/**
 * Tuning for whichever filter [mode] picks. The one-euro fields are quoted at the values that
 * match the EMA's lag at the reference spacing, so switching the mode changes the *character* of
 * the smoothing rather than the amount of it.
 */
data class StrokeFilter(
    val mode: StrokeFilterMode = StrokeFilterMode.EMA,
    /** Assumed sample rate for un-timed streams, and the one-euro's fallback dt. */
    val freqHz: Double = DEFAULT_FREQ_HZ,
    val minCutoff: Double = DEFAULT_MIN_CUTOFF_HZ,
    val beta: Double = DEFAULT_BETA,
    val dCutoff: Double = DEFAULT_D_CUTOFF_HZ,
) {
    companion object {
        /** A typical stylus report rate; a finger stream reads about half. */
        const val DEFAULT_FREQ_HZ = 120.0

        /**
         * Tuned against the EMA at its reference lag: at 120 Hz and the reference 1.5 px spacing,
         * the EMA's per-sample factor is `1 - e^-1 ≈ 0.63`, which a one-euro cutoff of ~32.8 Hz
         * reproduces (`alpha = 1/(1 + tau/Te)`, `tau = 1/(2*pi*fc)`). Lower reads as
         * heavier/steadier; raise it if ink trails the pen.
         */
        const val DEFAULT_MIN_CUTOFF_HZ = 32.8

        /** Speed adaptivity: cutoff gained per unit/second of measured speed. */
        const val DEFAULT_BETA = 0.05

        /** The speed estimate's own low-pass; the paper's 1.0 works across report rates. */
        const val DEFAULT_D_CUTOFF_HZ = 1.0

        val DEFAULT = StrokeFilter()
    }
}
