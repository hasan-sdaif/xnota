package com.xnota.core.model

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The fork's image crop math, pure and normalized: a crop is a sub-rect of the source picture
 * written as fractions of its width and height, so it rides an [com.xnota.core.model.ImageItem]
 * of any resolution unchanged. Clamping keeps every field honest, the aspect presets re-fit a
 * crop around its own centre, and the rounded-corner walk turns a radius into the outline points
 * a painter strokes (or clips to) — the geometry behind the crop overlay and the corner slider.
 */
object CropMath {

    /** A crop as fractions of the source picture: `[left, top]` plus `[width] × [height]`, all 0..1. */
    data class CropRect(val left: Double, val top: Double, val width: Double, val height: Double) {
        val right: Double get() = left + width
        val bottom: Double get() = top + height
    }

    /** The whole picture. */
    val FULL = CropRect(0.0, 0.0, 1.0, 1.0)

    /** The lockable aspect ratios; [FREE] keeps whatever the crop holds. */
    enum class Aspect(val ratio: Double?) {
        FREE(null),
        SQUARE(1.0),
        TV(4.0 / 3.0),
        WIDE(16.0 / 9.0),
        PHOTO(3.0 / 2.0),
    }

    /** [c] with every field finite and folded back inside the picture: a full-width or degenerate
     *  crop can never escape it. */
    fun clamp(c: CropRect): CropRect {
        fun f(v: Double) = if (v.isFinite()) v.coerceIn(0.0, 1.0) else 0.0
        val left = f(c.left)
        val top = f(c.top)
        val width = f(c.width).coerceAtMost(1.0 - left)
        val height = f(c.height).coerceAtMost(1.0 - top)
        return CropRect(left, top, width, height)
    }

    /** The crop's aspect as drawn (source pixels matter: a normalized square of a wide picture is wide). */
    fun aspect(c: CropRect, srcW: Int, srcH: Int): Double {
        val w = c.width * srcW.toDouble()
        val h = c.height * srcH.toDouble()
        return if (h == 0.0) Double.POSITIVE_INFINITY else w / h
    }

    /**
     * [c] re-fitted to [aspect]: the crop keeps its centre and whichever dimension already fits,
     * the other shrinks to the ratio. The result then slides — never shrinks — back inside the
     * picture, so a centre near an edge gives up the perfect centring, never the ratio.
     */
    fun fitToAspect(c: CropRect, aspect: Aspect, srcW: Int, srcH: Int): CropRect {
        val ratio = aspect.ratio ?: return clamp(c)
        val clamped = clamp(c)
        val pxW = clamped.width * srcW
        val pxH = clamped.height * srcH
        var w = clamped.width
        var h = clamped.height
        if (pxH <= 0.0 || pxW / pxH > ratio) {
            // Too wide: narrow to the ratio at this height (or the picture's width when height is 0).
            h = if (pxH > 0.0) clamped.height else (ratio * srcW / srcH).coerceAtMost(1.0)
            w = (h * srcH * ratio / srcW).coerceAtMost(1.0)
        } else {
            // Too tall: shorten to the ratio at this width.
            w = clamped.width
            h = (w * srcW / ratio / srcH).coerceAtMost(1.0)
        }
        val cx = clamped.left + clamped.width / 2.0
        val cy = clamped.top + clamped.height / 2.0
        w = w.coerceIn(0.0, 1.0)
        h = h.coerceIn(0.0, 1.0)
        val left = (cx - w / 2.0).coerceIn(0.0, 1.0 - w)
        val top = (cy - h / 2.0).coerceIn(0.0, 1.0 - h)
        return CropRect(left, top, w, h)
    }

    /**
     * The outline of [rect] with corners rounded to [radius] (content px): a closed polygon whose
     * straight runs carry the edges and whose corners are [CORNER_SIDES]-segment arcs. A radius
     * of 0 — or one that degenerates past half the shorter side, which clamps to it — returns the
     * plain rectangle. Points run clockwise from the top edge's left end (y down), so it closes.
     */
    fun roundedRect(rect: Rect, radius: Double): List<Pt> {
        val r = when {
            !radius.isFinite() || radius <= 0.0 -> 0.0
            else -> min(radius, min(rect.w, rect.h) / 2.0)
        }
        if (r <= 0.0) return listOf(rect.topLeft, Pt(rect.right, rect.top), Pt(rect.right, rect.bottom), Pt(rect.left, rect.bottom))
        val out = ArrayList<Pt>(1 + 4 * (CORNER_SIDES + 1))
        out += Pt(rect.left + r, rect.top)
        // Clockwise (y down), each corner's arc given by its own centre and screen angle span.
        fun arc(cx: Double, cy: Double, fromDeg: Double, toDeg: Double) {
            for (i in 0..CORNER_SIDES) {
                val a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / CORNER_SIDES)
                out += Pt(cx + r * cos(a), cy + r * sin(a))
            }
        }
        arc(rect.right - r, rect.top + r, 270.0, 360.0) // top-right
        arc(rect.right - r, rect.bottom - r, 0.0, 90.0) // bottom-right
        arc(rect.left + r, rect.bottom - r, 90.0, 180.0) // bottom-left
        arc(rect.left + r, rect.top + r, 180.0, 270.0) // top-left, closing back onto the start
        return out
    }

    /** How many sides each rounded corner's arc carries. */
    const val CORNER_SIDES = 4
}
