package com.xnota.core.model

/**
 * The fork's named dash styles for an outline: each a run list of alternating on/off lengths in
 * **content px, scaled by the stroke width**, so a pattern keeps its look across pen sizes — the
 * geometry behind the dash/dot/dash-dot chips.
 *
 * A run list is the honest shape of the pattern: a dash-dot needs four runs, which no single
 * (on, off) pair can express. The restyle wiring that only owns a single pair (the shape's
 * `dashLength`/`dashGap`) maps a chosen pattern onto its dominant run; this table is the source
 * of truth for the lengths themselves.
 */
enum class DashPattern(val id: String) {

    /** A plain solid line — no runs at all. */
    SOLID("solid"),

    /** Classic dashes: moderate on, a full width off. */
    DASH("dash"),

    /** Round-cap dots: a near-zero on with a wide off. */
    DOT("dot"),

    /** A dash followed by a dot, the classic surveyor's line. */
    DASH_DOT("dash_dot"),

    /** Long statement dashes with tight gaps. */
    LONG("long");

    /** The alternating on/off runs for a line [width] wide, in content px; empty for [SOLID]. */
    fun runs(width: Double): DoubleArray {
        val w = if (width.isFinite() && width > 0.0) width else 1.0
        return when (this) {
            SOLID -> DoubleArray(0)
            DASH -> doubleArrayOf(4.0 * w, 3.0 * w)
            DOT -> doubleArrayOf(0.5 * w, 2.5 * w)
            DASH_DOT -> doubleArrayOf(4.0 * w, 2.0 * w, 0.5 * w, 2.0 * w)
            LONG -> doubleArrayOf(8.0 * w, 2.0 * w)
        }
    }

    /** The pattern's period — the distance after which the rhythm repeats; 0 for [SOLID]. */
    fun period(width: Double): Double = runs(width).sum()

    /** True when this pattern draws an unbroken line. */
    val isSolid: Boolean get() = this == SOLID

    /**
     * The phase advanced by [distance] along a line [width] wide, wrapped into `[0, period)` —
     * what a line drawn in pieces (wet cache handoff, a connector split round its label) needs to
     * keep one rhythm across the seam. Solid has no rhythm to keep: always 0.
     */
    fun advance(phase: Double, distance: Double, width: Double): Double {
        val p = period(width)
        if (p <= 0.0) return 0.0
        val v = (phase + distance) % p
        return if (v < 0.0) v + p else v
    }

    companion object {
        fun fromId(id: String?): DashPattern = entries.firstOrNull { it.id == id } ?: SOLID
    }
}
