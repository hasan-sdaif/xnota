package com.xnota.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which ways a drawing is mirrored inside its box: left to right ([x]), upside down ([y]), both or
 * neither. It mirrors about the box's centre lines, so the box itself stays put. A file names one
 * by [id], the way PowerPoint's flipH/flipV and ODF's mirror-horizontal/vertical store it.
 */
enum class Flip(val x: Boolean, val y: Boolean, val id: String) {
    NONE(false, false, ""),
    X(true, false, "x"),
    Y(false, true, "y"),
    XY(true, true, "xy");

    /** This flip with [other]'s axes mirrored once more. */
    fun toggled(other: Flip): Flip = of(x != other.x, y != other.y)

    /** The axes this flip shares with [other]. */
    fun and(other: Flip): Flip = of(x && other.x, y && other.y)

    /** [p] mirrored about [box]'s centre lines. */
    fun map(p: Pt, box: Rect): Pt = Pt(if (x) box.left + box.right - p.x else p.x, if (y) box.top + box.bottom - p.y else p.y)

    /** [r] mirrored about [box]'s centre lines. */
    fun map(r: Rect, box: Rect): Rect = if (this == NONE) r else Rect.fromPoints(map(r.topLeft, box), map(Pt(r.right, r.bottom), box))

    /** The direction [v] mirrored. */
    fun direction(v: Pt): Pt = Pt(if (x) -v.x else v.x, if (y) -v.y else v.y)

    companion object {
        fun of(x: Boolean, y: Boolean): Flip = when {
            x && y -> XY
            x -> X
            y -> Y
            else -> NONE
        }

        /** The flip a file names by [id]; none for an id this version does not know. */
        fun fromId(id: String?): Flip = entries.firstOrNull { it != NONE && it.id == id } ?: NONE

        /**
         * Where a frame turned by [turn] lands under the mirroring [t]: its new turn in (-PI, PI],
         * and the flip within the frame that makes up the mirror. A mirror reads as the frame
         * flipped left to right or as upside down and half a turn on; this takes whichever leaves
         * the frame nearer upright, so text in it stays readable.
         */
        fun across(t: Affine, turn: Double): Pair<Double, Flip> {
            val cs = cos(turn)
            val sn = sin(turn)
            val ux = Pt(t.a * cs + t.c * sn, t.b * cs + t.d * sn)
            val uy = Pt(-t.a * sn + t.c * cs, -t.b * sn + t.d * cs)
            val byHeight = folded(atan2(uy.y, uy.x) - PI / 2.0)
            return if (abs(byHeight) <= PI / 2.0 + 1e-9) byHeight to X else folded(atan2(ux.y, ux.x)) to Y
        }

        private fun folded(a: Double): Double {
            var r = a % (2.0 * PI)
            if (r <= -PI) r += 2.0 * PI
            if (r > PI) r -= 2.0 * PI
            return r
        }
    }
}
