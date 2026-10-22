package com.xnota.core.shapes

import com.xnota.core.geometry.Pt
import com.xnota.core.model.FillPattern
import com.xnota.core.model.Rgba
import com.xnota.core.pal.Pen
import com.xnota.core.pal.Renderer
import kotlin.math.abs

/**
 * A shape's fill pattern as drawn geometry, already clipped to the shape's own outline and in page
 * space: the hatch lines to stroke and the dot lattice to fill, with the widths and colour they
 * take. One object serves every backend — the CPU painter strokes/fills it through a [Renderer],
 * the GL tessellator meshes the same runs — so the paged canvas, the PDF export and the infinite
 * canvas draw one pattern, not three approximations of it.
 *
 * Built from [FillPattern]'s rect math (the source of truth for the rhythm) by clipping each run to
 * the shape's flattened fill rings under even-odd, which is what keeps a hatch line inside a star's
 * concavities and out of a ring's hole without any renderer-side clip path.
 */
class FillPatternRuns(
    /** Hatch/cross-hatch segments to stroke, each already inside the shape. */
    val lines: List<Pair<Pt, Pt>>,
    /** The dot lattice's centres to fill. */
    val dots: List<Pt>,
    /** A dot's radius. */
    val dotRadius: Double,
    /** The hatch lines' width. */
    val lineWidth: Double,
    /** The pattern's ink — the shape's fill colour, adapted to the paper like every fill. */
    val color: Rgba,
) {
    /** The pattern drawn through [r]: hatch lines stroked, dots filled. */
    fun paint(r: Renderer) {
        val pen = Pen(color, width = lineWidth, cosmetic = false)
        for ((a, b) in lines) r.strokePolyline(listOf(a, b), pen)
        for (d in dots) r.fillCircle(d, dotRadius, color)
    }

    companion object {
        /** Hatch pitch as multiples of the stroke width, so a pattern keeps its density across pen sizes. */
        const val SPACING_WIDTHS = 4.0

        /** The least pitch a pattern takes, so a hairline shape still shows a rhythm. */
        const val MIN_SPACING = 8.0

        /** A hatch line's width as a share of the outline's — hatching reads best lighter than the edge. */
        const val LINE_FRACTION = 0.75

        /** The least hatch width; a pattern is not invisible on a hairline shape. */
        const val MIN_LINE = 1.0

        /** A dot's radius as a share of the stroke width. */
        const val DOT_FRACTION = 0.6

        /** The least dot radius. */
        const val MIN_DOT = 1.5

        /** The pitch a shape [strokeWidth] wide hatches at. */
        fun spacing(strokeWidth: Double): Double = (strokeWidth * SPACING_WIDTHS).coerceAtLeast(MIN_SPACING)
    }
}

/**
 * The runs of the segment a→b that lie inside [rings], clipped under even-odd: every crossing of
 * the outline flips inside/out, so a line through a star's point or a ring's hole comes back as
 * the pieces that are genuinely interior. A segment that never crosses is kept whole when its
 * start is inside and dropped when it is not.
 */
internal fun clipSegmentToRings(a: Pt, b: Pt, rings: List<List<Pt>>): List<Pair<Pt, Pt>> {
    if (rings.isEmpty()) return emptyList()
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (abs(dx) < 1e-12 && abs(dy) < 1e-12) return emptyList()
    val crossings = ArrayList<Double>(4)
    for (ring in rings) {
        val n = ring.size
        for (i in 0 until n) {
            val p = ring[i]
            val q = ring[(i + 1) % n]
            val ex = q.x - p.x
            val ey = q.y - p.y
            val denom = dx * ey - dy * ex
            if (abs(denom) < 1e-12) continue
            val t = ((p.x - a.x) * ey - (p.y - a.y) * ex) / denom
            if (t < 0.0 || t > 1.0) continue
            val u = ((p.x - a.x) * dy - (p.y - a.y) * dx) / denom
            if (u in 0.0..1.0) crossings += t
        }
    }
    fun at(t: Double) = Pt(a.x + dx * t, a.y + dy * t)
    if (crossings.isEmpty()) return if (pointInRings(a, rings)) listOf(a to b) else emptyList()
    crossings.sort()
    val out = ArrayList<Pair<Pt, Pt>>(crossings.size)
    var prev = 0.0
    var inside = pointInRings(a, rings)
    for (t in crossings) {
        if (inside && t - prev > 1e-9) out += at(prev) to at(t)
        inside = !inside
        prev = t
    }
    if (inside && 1.0 - prev > 1e-9) out += at(prev) to b
    return out
}

/** Whether [p] sits inside [rings] under even-odd (a half-open ray cast, robust to shared edges). */
internal fun pointInRings(p: Pt, rings: List<List<Pt>>): Boolean {
    var crossings = 0
    for (ring in rings) {
        val n = ring.size
        for (i in 0 until n) {
            val a = ring[i]
            val b = ring[(i + 1) % n]
            if ((a.y > p.y) != (b.y > p.y)) {
                val x = a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x)
                if (x > p.x) crossings++
            }
        }
    }
    return crossings % 2 == 1
}

/**
 * How a shape's style paints its [ShapePath]s, role by role. The paged renderers draw through
 * [fill] and [stroke]; the canvas mesher asks [fillColor] and [strokePen] the same questions, so
 * both canvases tint, dash and skip exactly the same paths.
 */
object ShapePainter {

    /**
     * The colour a [role] path fills with, given the shape's [fill] (null when it has none) and its
     * [outline] colour; null when the path is left unfilled.
     */
    fun fillColor(role: PathRole, fill: Rgba?, outline: Rgba): Rgba? = when (role) {
        PathRole.OUTLINE, PathRole.FILL_ONLY -> fill
        PathRole.SHADE_DARK -> fill?.let { mix(it, 0, SHADE_DARK) }
        PathRole.SHADE_LIGHT -> fill?.let { mix(it, 255, SHADE_LIGHT) }
        PathRole.HEAD_FILLED -> outline
        else -> null
    }

    /** The pen a [role] path strokes with, given the shape's own [pen]; null when it is not stroked. */
    fun strokePen(role: PathRole, pen: Pen): Pen? = when (role) {
        PathRole.OUTLINE, PathRole.STROKE_ONLY -> pen
        PathRole.HIDDEN ->
            if (pen.dashed) pen
            else pen.copy(dashed = true, dashOn = HIDDEN_ON + pen.width * 2.0, dashGap = HIDDEN_GAP + pen.width * 1.5)
        PathRole.HEAD, PathRole.HEAD_FILLED -> pen.copy(dashed = false)
        else -> null
    }

    fun fill(r: Renderer, paths: List<ShapePath>, fill: Rgba?, outline: Rgba) {
        for (p in paths) fillColor(p.role, fill, outline)?.let { r.fillPath(p.contours, it, p.fillRule) }
    }

    fun stroke(r: Renderer, paths: List<ShapePath>, pen: Pen) {
        for (p in paths) strokePen(p.role, pen)?.let { r.strokePath(p.contours, it) }
    }

    private fun mix(c: Rgba, toward: Int, t: Double): Rgba = Rgba(
        (c.r + (toward - c.r) * t).toInt().coerceIn(0, 255),
        (c.g + (toward - c.g) * t).toInt().coerceIn(0, 255),
        (c.b + (toward - c.b) * t).toInt().coerceIn(0, 255),
        c.a,
    )

    private const val SHADE_DARK = 0.25
    private const val SHADE_LIGHT = 0.35
    private const val HIDDEN_ON = 4.0
    private const val HIDDEN_GAP = 3.0
}
