package com.xnota.core.stroke

import com.xnota.core.model.Stroke

/**
 * Draw symmetry (the fork): every committed stroke is echoed through a mirror or a rotation
 * about the page centre, so a flourish drawn once lands on both sides of the page — the classic
 * symmetry guide of drawing apps.
 *
 * The transform happens at the interaction level, on **finished strokes**: the pen-up stroke is
 * duplicated with mirrored samples and the copies are committed with it as one undo unit. Nothing
 * changes in [StrokeEngine] or in the wet ribbon (the live stroke under the pen is always the
 * single original), so the engine's historical behaviour — and the wet-vs-batch conformance the
 * ribbon tests pin — is untouched.
 *
 * [SymmetryMode] is the persisted enum (`Preferences.symmetryMode`); the pure sample transforms
 * live in [Symmetry] so they are unit-testable without a canvas.
 */
enum class SymmetryMode(val id: String) {
    /** No symmetry: the stroke commits alone (the default, and the historical behaviour). */
    NONE("none"),

    /** Mirror across the vertical axis through the centre: `x → 2·axisX − x`. */
    VERTICAL("vertical"),

    /** Mirror across the horizontal axis through the centre: `y → 2·axisY − y`. */
    HORIZONTAL("horizontal"),

    /** Both axes: the stroke, its vertical mirror, its horizontal mirror, and the double mirror. */
    QUAD("quad"),

    /** [Symmetry.segments] rotated copies around the centre, spaced `2π / n`, including the original. */
    RADIAL("radial");

    companion object {
        fun fromId(id: String?): SymmetryMode = entries.firstOrNull { it.id == id } ?: NONE

        /** The valid persisted ids, for preference validation. */
        val ids: Set<String> = entries.map { it.id }.toSet()
    }
}

/** Pure mirror/rotate arithmetic and the stroke-level transforms built on it. */
object Symmetry {
    const val MIN_SEGMENTS = 2
    const val MAX_SEGMENTS = 8

    /** Clamp a segments count into the radial range 2..8. */
    fun segments(n: Int): Int = n.coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)

    /** Mirror an x across the vertical line `x = axisX`. */
    fun mirrorX(x: Double, axisX: Double): Double = 2.0 * axisX - x

    /** Mirror a y across the horizontal line `y = axisY`. */
    fun mirrorY(y: Double, axisY: Double): Double = 2.0 * axisY - y

    /** Rotate (x, y) by [angleRad] about (cx, cy): the x of the result. */
    fun rotateX(x: Double, y: Double, cx: Double, cy: Double, angleRad: Double): Double {
        val dx = x - cx
        val dy = y - cy
        val c = kotlin.math.cos(angleRad)
        val s = kotlin.math.sin(angleRad)
        return cx + dx * c - dy * s
    }

    /** Rotate (x, y) by [angleRad] about (cx, cy): the y of the result. */
    fun rotateY(x: Double, y: Double, cx: Double, cy: Double, angleRad: Double): Double {
        val dx = x - cx
        val dy = y - cy
        val c = kotlin.math.cos(angleRad)
        val s = kotlin.math.sin(angleRad)
        return cy + dx * s + dy * c
    }

    /** Rotate (x, y) by [angleRad] about (cx, cy), as a point. */
    fun rotate(p: Pair<Double, Double>, cx: Double, cy: Double, angleRad: Double): Pair<Double, Double> =
        rotateX(p.first, p.second, cx, cy, angleRad) to rotateY(p.first, p.second, cx, cy, angleRad)

    /**
     * The transformed copies of [samples] beyond the original — the echoes a stroke commits beside
     * itself. Empty for [SymmetryMode.NONE] (and for an empty stroke); one for each mirror, three
     * for the quad, and `n − 1` rotations for the radial mode. Pressure and timing always travel
     * with their sample: a mirror has exactly the ink of the original.
     */
    fun extraSamples(
        samples: List<Sample>,
        mode: SymmetryMode,
        segments: Int,
        axisX: Double,
        axisY: Double,
    ): List<List<Sample>> {
        if (mode == SymmetryMode.NONE || samples.isEmpty()) return emptyList()
        return when (mode) {
            SymmetryMode.NONE -> emptyList()
            SymmetryMode.VERTICAL -> listOf(samples.map { it.copy(x = mirrorX(it.x, axisX)) })
            SymmetryMode.HORIZONTAL -> listOf(samples.map { it.copy(y = mirrorY(it.y, axisY)) })
            SymmetryMode.QUAD -> listOf(
                samples.map { it.copy(x = mirrorX(it.x, axisX)) },
                samples.map { it.copy(y = mirrorY(it.y, axisY)) },
                samples.map { it.copy(x = mirrorX(it.x, axisX), y = mirrorY(it.y, axisY)) },
            )
            SymmetryMode.RADIAL -> {
                val n = segments(segments)
                (1 until n).map { k ->
                    val a = 2.0 * Math.PI * k / n
                    samples.map { s ->
                        s.copy(
                            x = rotateX(s.x, s.y, axisX, axisY, a),
                            y = rotateY(s.x, s.y, axisX, axisY, a),
                        )
                    }
                }
            }
        }
    }

    /**
     * The echo strokes for a finished [stroke]: one new [Stroke] per transformed copy, sharing the
     * original's tool, style snapshot and draw-time scales, so the mirror is indistinguishable from
     * ink the hand drew on the other side. The original is **not** included.
     */
    fun mirrorStrokes(
        stroke: Stroke,
        mode: SymmetryMode,
        segments: Int,
        axisX: Double,
        axisY: Double,
    ): List<Stroke> =
        extraSamples(stroke.samples, mode, segments, axisX, axisY).map { samples ->
            Stroke(stroke.tool, stroke.config, samples, stroke.speedScale, stroke.straight, stroke.smoothScale)
        }
}
