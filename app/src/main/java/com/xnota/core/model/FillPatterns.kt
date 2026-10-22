package com.xnota.core.model

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The fork's shape fill patterns: what a closed shape's interior draws besides a flat colour —
 * hatch lines at an angle, a cross-hatch (both diagonals), a dot lattice, or nothing at all
 * ([BLANK], which is a deliberate "no fill" distinct from [SOLID]'s "flat colour").
 *
 * Pure rect math: the lines/dots a painter draws *inside [rect]* are computed here, so the CPU
 * painter, the PDF export and any future GL hatch agree on the geometry. The lines are returned
 * as clipped segment endpoints — a painter strokes them as-is, with no clip of its own, which is
 * what an unclipped PDF backend needs.
 */
enum class FillPattern(val id: String) {

    SOLID("solid"),
    HATCH("hatch"),
    CROSS_HATCH("cross_hatch"),
    DOTS("dots"),
    BLANK("blank");

    /** True when this pattern is any kind of drawn fill (everything but [BLANK]). */
    val drawsArea: Boolean get() = this != BLANK

    /**
     * The hatch lines this pattern draws inside [rect] with a [spacing] pitch (content px, between
     * neighbouring lines of one family): one family at [angleDeg] for [HATCH], both diagonals for
     * [CROSS_HATCH], empty for the rest. Each pair is a segment clipped to the rect.
     */
    fun hatchLines(rect: Rect, spacing: Double, angleDeg: Double = 45.0): List<Pair<Pt, Pt>> {
        if (this != HATCH && this != CROSS_HATCH) return emptyList()
        if (!spacing.isFinite() || spacing <= 0.0 || rect.w <= 0.0 || rect.h <= 0.0) return emptyList()
        val out = ArrayList<Pair<Pt, Pt>>()
        hatchFamily(rect, spacing, angleDeg, out)
        if (this == CROSS_HATCH) hatchFamily(rect, spacing, angleDeg + 90.0, out)
        return out
    }

    /** The dot lattice this pattern draws inside [rect] at [spacing] pitch; empty for the rest. */
    fun dotGrid(rect: Rect, spacing: Double): List<Pt> {
        if (this != DOTS) return emptyList()
        if (!spacing.isFinite() || spacing <= 0.0 || rect.w <= 0.0 || rect.h <= 0.0) return emptyList()
        val out = ArrayList<Pt>()
        var y = ceil(rect.top / spacing) * spacing
        while (y <= rect.bottom) {
            var x = ceil(rect.left / spacing) * spacing
            while (x <= rect.right) {
                out += Pt(x, y)
                x += spacing
            }
            y += spacing
        }
        return out
    }

    companion object {
        fun fromId(id: String?): FillPattern = entries.firstOrNull { it.id == id } ?: SOLID

        /**
         * One family of parallel lines at [angleDeg] (0 = horizontal, y down, degrees
         * counter-clockwise on screen) with a perpendicular pitch of [spacing], clipped to [rect].
         * The family is anchored to the coordinate origin's lattice, so any sub-rect of a larger
         * area computes exactly the same lines — which is what lets a fill and a page ruling share
         * this walk without drifting.
         */
        private fun hatchFamily(rect: Rect, spacing: Double, angleDeg: Double, out: MutableList<Pair<Pt, Pt>>) {
            val theta = Math.toRadians(angleDeg)
            val d = Pt(cos(theta), -sin(theta)) // along the line
            val n = Pt(sin(theta), cos(theta)) // across, unit normal
            val reach = hypot(rect.w, rect.h)
            // The normal coordinates of the rect's corners bound the offsets the family needs.
            val corners = listOf(
                rect.left * n.x + rect.top * n.y,
                rect.right * n.x + rect.top * n.y,
                rect.left * n.x + rect.bottom * n.y,
                rect.right * n.x + rect.bottom * n.y,
            )
            val kMin = floor(corners.min() / spacing).toLong()
            val kMax = ceil(corners.max() / spacing).toLong()
            for (k in kMin..kMax) {
                val off = k * spacing
                val base = n * off
                val a = base - d * reach
                val b = base + d * reach
                clipSegmentToRect(a, b, rect)?.let { out += it }
            }
        }

        /** The segment a→b clipped to [rect] (Liang–Barsky), or null when it misses — or merely
         *  grazes a corner, which a hatch line through a rect's corner does and which is no line
         *  worth drawing. */
        private fun clipSegmentToRect(a: Pt, b: Pt, rect: Rect): Pair<Pt, Pt>? {
            var t0 = 0.0
            var t1 = 1.0
            val dx = b.x - a.x
            val dy = b.y - a.y
            for ((p, q) in listOf(-dx to a.x - rect.left, dx to rect.right - a.x, -dy to a.y - rect.top, dy to rect.bottom - a.y)) {
                if (p == 0.0) {
                    if (q < 0.0) return null
                } else {
                    val r = q / p
                    if (p < 0.0) {
                        if (r > t1) return null
                        if (r > t0) t0 = r
                    } else {
                        if (r < t0) return null
                        if (r < t1) t1 = r
                    }
                }
            }
            if (t1 - t0 <= 1e-12) return null
            return Pt(a.x + t0 * dx, a.y + t0 * dy) to Pt(a.x + t1 * dx, a.y + t1 * dy)
        }
    }
}
