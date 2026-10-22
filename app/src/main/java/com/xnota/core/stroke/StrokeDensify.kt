package com.xnota.core.stroke

/**
 * Velocity-aware tessellation LOD (the fork): a fast hand outruns the capture gate and leaves
 * long chords between the kept samples, and a straight quad over a long chord facets a curve the
 * eye reads as polygonal. [densify] subdivides such chords by arc length — linearly, so the
 * subdivided polyline is the same line, only sampled finely enough that the swept-disc
 * tessellation rounds it instead of faceting it.
 *
 * Pure and deterministic; the GL mesher feeds any ribbon whose longest chord exceeds the cap
 * through [densify] and tessellates the result instead.
 */
object StrokeDensify {

    /**
     * Longest chord (content px) the GL mesher lets a committed stroke keep before densifying.
     * Aligned with the pen-up reducer's own cap ([StrokeSimplify.MAX_GAP]) so only strokes the
     * reducer could not help — a pen already sparser than the cap — pay for subdivision.
     */
    const val MAX_CHORD_PX = 3.0

    /**
     * Whether [g]'s points `[from, from + count)` carry a chord worth subdividing — the cheap
     * gate the mesher runs before allocating anything.
     */
    fun needsDensify(g: RibbonPoints, from: Int, count: Int, maxChord: Double = MAX_CHORD_PX): Boolean {
        if (count < 2 || maxChord <= 0.0) return false
        val end = from + count
        for (i in from until end - 1) {
            if (hypotOf(g, i, i + 1) > maxChord) return true
        }
        return false
    }

    /**
     * The ribbon's `[from, from + count)` points, linearly subdivided so no chord exceeds
     * [maxChord]. Endpoints are kept exactly; widths are interpolated along with positions; the
     * rails are rebuilt from the local tangents so the result is a first-class [RibbonPoints]
     * the tessellator consumes unchanged. A count < 2 or an already-fine path returns a ribbon
     * that simply copies the input.
     */
    fun densify(g: RibbonPoints, from: Int, count: Int, maxChord: Double = MAX_CHORD_PX): RibbonPoints {
        if (count < 2 || maxChord <= 0.0 || !needsDensify(g, from, count, maxChord)) {
            return CopyRibbon(g, from, count)
        }
        val end = from + count
        val xs = ArrayList<Double>(count)
        val ys = ArrayList<Double>(count)
        val ws = ArrayList<Double>(count)
        for (i in from until end - 1) {
            val x0 = g.cx(i)
            val y0 = g.cy(i)
            val w0 = g.hw(i)
            val steps = segments(hypotOf(g, i, i + 1), maxChord)
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                xs.add(x0 + (g.cx(i + 1) - x0) * t)
                ys.add(y0 + (g.cy(i + 1) - y0) * t)
                ws.add(w0 + (g.hw(i + 1) - w0) * t)
            }
        }
        xs.add(g.cx(end - 1))
        ys.add(g.cy(end - 1))
        ws.add(g.hw(end - 1))
        return ArrayRibbon(xs.toDoubleArray(), ys.toDoubleArray(), ws.toDoubleArray())
    }

    /**
     * How many pieces a chord of [len] needs so each stays within [maxChord]: `ceil(len / max)`
     * with degenerate lengths collapsing to one (the point is still emitted once).
     */
    fun segments(len: Double, maxChord: Double): Int {
        if (len <= 0.0 || maxChord <= 0.0) return 1
        return kotlin.math.ceil(len / maxChord).toInt().coerceAtLeast(1)
    }

    private fun hypotOf(g: RibbonPoints, a: Int, b: Int): Double =
        kotlin.math.hypot(g.cx(b) - g.cx(a), g.cy(b) - g.cy(a))

    /** A verbatim view of `[from, from + count)` — the degenerate case of [densify]. */
    private class CopyRibbon(private val g: RibbonPoints, private val from: Int, private val count: Int) :
        RibbonPoints {
        override val pointCount get() = count
        override fun cx(i: Int): Double = g.cx(from + i)
        override fun cy(i: Int): Double = g.cy(from + i)
        override fun hw(i: Int): Double = g.hw(from + i)
        override val hasRails get() = false
        override fun leftX(i: Int): Double = g.leftX(from + i)
        override fun leftY(i: Int): Double = g.leftY(from + i)
        override fun rightX(i: Int): Double = g.rightX(from + i)
        override fun rightY(i: Int): Double = g.rightY(from + i)
    }

    /**
     * A ribbon built from centreline + half-width arrays, with the rails derived from the local
     * tangents — the same normals [StrokeEngine.writePoint] lays the rails by, so the subdivided
     * body meets the disc sweep of the original.
     */
    class ArrayRibbon(
        private val xs: DoubleArray,
        private val ys: DoubleArray,
        private val ws: DoubleArray,
    ) : RibbonPoints {
        override val pointCount get() = xs.size
        override fun cx(i: Int): Double = xs[i]
        override fun cy(i: Int): Double = ys[i]
        override fun hw(i: Int): Double = ws[i]

        override val hasRails get() = pointCount >= 2

        override fun leftX(i: Int): Double = cx(i) + normalX(i) * hw(i)
        override fun leftY(i: Int): Double = cy(i) + normalY(i) * hw(i)
        override fun rightX(i: Int): Double = cx(i) - normalX(i) * hw(i)
        override fun rightY(i: Int): Double = cy(i) - normalY(i) * hw(i)

        private fun tangent(i: Int): Pair<Double, Double> {
            val n = pointCount
            val a = if (i <= 0) 0 else i - 1
            val b = if (i >= n - 1) n - 1 else i + 1
            val dx = xs[b] - xs[a]
            val dy = ys[b] - ys[a]
            val len = kotlin.math.hypot(dx, dy)
            return if (len < 1e-9) 1.0 to 0.0 else dx / len to dy / len
        }

        private fun normalX(i: Int): Double = -tangent(i).second
        private fun normalY(i: Int): Double = tangent(i).first
    }
}
