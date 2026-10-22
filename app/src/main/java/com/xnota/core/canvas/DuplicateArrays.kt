package com.xnota.core.canvas

import com.xnota.core.geometry.Affine
import com.xnota.core.geometry.Pt
import com.xnota.core.model.CanvasItem
import kotlin.math.max

/**
 * The fork's duplicate arrays: the selection menu's "Duplicate array…" — [radial] copies rotated
 * around a centre, [grid] copies laid out rows × columns with a gap. Pure transform planning over
 * the item model: the caller supplies the clone function (the controllers own the deep copy with
 * their text measurer), and each copy is baked through [CanvasItem.applyTransform] exactly the
 * way a rotation grip bakes its turn, so undo/redo and glue follow the same path they always do.
 */
object DuplicateArrays {

    /** The least count either array accepts; a lone copy is what plain duplicate already does. */
    const val MIN_COPIES = 2

    /** The most copies either array accepts, so a slip of the slider cannot wedge the note. */
    const val MAX_COPIES = 24

    /**
     * [copies] copies of [item] (the original NOT included — every copy is new) rotated about
     * [centre]: copy k sits at k·[spanDegrees]/copies turned through that same angle, so the set
     * fans out over [spanDegrees] (360° closes the ring). [clone] makes each independent copy
     * before the transform bakes into it.
     */
    fun radial(
        item: CanvasItem,
        centre: Pt,
        copies: Int,
        spanDegrees: Double,
        clone: (CanvasItem) -> CanvasItem,
    ): List<CanvasItem> {
        val n = copies.coerceIn(MIN_COPIES, MAX_COPIES)
        if (!spanDegrees.isFinite() || spanDegrees == 0.0) return emptyList()
        val step = Math.toRadians(spanDegrees) / n
        val out = ArrayList<CanvasItem>(n)
        for (k in 1..n) {
            val copy = clone(item)
            copy.applyTransform(Affine.rotateAbout(centre, step * k))
            out += copy
        }
        return out
    }

    /**
     * [rows] × [cols] copies of [item] laid out from its own top-left with [gapX]/[gapY] content px
     * between neighbouring copies' bounds (row 0 col 0 is the original's spot, so the grid grows
     * down-right and the caller keeps or deletes the original as it sees fit). [clone] makes each
     * independent copy before it is moved.
     */
    fun grid(
        item: CanvasItem,
        rows: Int,
        cols: Int,
        gapX: Double,
        gapY: Double,
        clone: (CanvasItem) -> CanvasItem,
    ): List<CanvasItem> {
        // Rows and columns clamp independently to 1..MAX: a 1×N or N×1 array is a legitimate
        // row/column of copies, so only the total count is bounded, not each dimension.
        val r = rows.coerceIn(1, MAX_COPIES)
        val c = cols.coerceIn(1, MAX_COPIES)
        if (r < 1 || c < 1) return emptyList()
        val gap = Pt(max(gapX, 0.0), max(gapY, 0.0))
        val cell = item.bounds().let { Pt(it.w + gap.x, it.h + gap.y) }
        val out = ArrayList<CanvasItem>(r * c - 1)
        for (row in 0 until r) {
            for (col in 0 until c) {
                if (row == 0 && col == 0) continue // the original's own slot
                val copy = clone(item)
                copy.translate(col * cell.x, row * cell.y)
                out += copy
            }
        }
        return out
    }
}
