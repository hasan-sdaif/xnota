package com.xnota.core.geometry

/**
 * Which way an align op lines a selection up: each item's bbox edge or centre against the
 * target's (the selection's own bounds, or the page when a single item is selected).
 */
enum class AlignMode {
    LEFT,
    RIGHT,
    CENTER_H,
    TOP,
    BOTTOM,
    CENTER_V,
}

/**
 * Pure arithmetic for aligning and distributing a selection (spec-adjacent).
 *
 * Both ops are stated over axis-aligned bounding boxes in whatever one space the caller
 * measures them in — page space, content space, canvas space — and hand back per-item
 * `(dx, dy)` translations in that same space, so the caller stays in charge of how a
 * delta reaches the model (a page-space vector, rotated per page, on the paged canvas).
 *
 * Pure Kotlin, no model dependency, so the arithmetic is unit-testable on its own.
 */
object AlignOps {

    /**
     * Per-item deltas that line each bbox's edge or centre up with [target]'s, per [mode].
     * The selection's own collective bounds are the target when the caller passes them; a lone
     * item then aligns to the page (the caller passes the page rect), which is the one case a
     * selection's own bounds could not express.
     */
    fun alignDeltas(bounds: List<Rect>, target: Rect, mode: AlignMode): List<Pt> = bounds.map { b ->
        when (mode) {
            AlignMode.LEFT -> Pt(target.left - b.left, 0.0)
            AlignMode.RIGHT -> Pt(target.right - b.right, 0.0)
            AlignMode.CENTER_H -> Pt(target.centerX - b.centerX, 0.0)
            AlignMode.TOP -> Pt(0.0, target.top - b.top)
            AlignMode.BOTTOM -> Pt(0.0, target.bottom - b.bottom)
            AlignMode.CENTER_V -> Pt(0.0, target.centerY - b.centerY)
        }
    }

    /**
     * Per-item deltas that equalize the gaps between the bboxes along one axis. The outermost
     * items stay put and the ones between spread so every gap between neighbours is the same
     * width — the empty space is shared out, not the centres forced apart. Null when fewer than
     * three items: with two there is one gap and nothing to equalize.
     *
     * Overlapping items distribute with a negative shared gap, which is still the even spread.
     */
    fun distributeDeltas(bounds: List<Rect>, vertical: Boolean): List<Pt>? {
        if (bounds.size < 3) return null
        // Index order, not sorted order, is what the caller's items come back in.
        val order = bounds.indices.sortedBy { if (vertical) bounds[it].top else bounds[it].left }
        val first = order.first()
        val last = order.last()
        val start = if (vertical) bounds[first].top else bounds[first].left
        val end = if (vertical) bounds[last].bottom else bounds[last].right
        val span = end - start
        val sizes = order.sumOf { if (vertical) bounds[it].h else bounds[it].w }
        val gap = (span - sizes) / (order.size - 1)
        val deltas = PtArray(bounds.size)
        var edge = start
        for (i in order) {
            val b = bounds[i]
            val at = if (vertical) b.top else b.left
            val d = edge - at
            deltas.set(i, if (vertical) Pt(0.0, d) else Pt(d, 0.0))
            edge += (if (vertical) b.h else b.w) + gap
        }
        return deltas.toList()
    }

    /** A fixed-size mutable point array, so distribute fills slots in index order after sorting. */
    private class PtArray(n: Int) {
        private val xs = DoubleArray(n)
        private val ys = DoubleArray(n)

        fun set(i: Int, p: Pt) {
            xs[i] = p.x
            ys[i] = p.y
        }

        fun toList(): List<Pt> = xs.indices.map { Pt(xs[it], ys[it]) }
    }
}
