package com.xnota.core.stroke

import com.xnota.core.geometry.Pt
import com.xnota.core.model.Rgba
import com.xnota.core.tools.Tool

/**
 * Stroke endpoint merging (the fork's `join_strokes` preference, off by default): when a new
 * stroke's **start** lands within N px of the previous stroke's **end**, and the two agree on tool,
 * width (±20%) and colour, the new stroke is silently appended to the old one instead of becoming
 * its own item — the writing reads as one continuous line, one selection, one undo.
 *
 * The matcher is pure geometry and style comparison, so it is fully unit-tested without a canvas;
 * the commit paths (paged [com.xnota.canvas.InteractionController], infinite
 * [com.xnota.ui.InfiniteEditor]) only ask "can these join?" and fold the samples when yes.
 */
object StrokeJoin {

    /** Default joining distance, content px at 100% zoom. */
    const val DEFAULT_GAP_PX = 12.0

    /** Joinable width agreement: the new width within this fraction of the old one. */
    const val WIDTH_TOLERANCE = 0.20

    /**
     * Whether a stroke starting at [nextStart] joins a stroke that ended at [prevEnd]: close
     * enough in space, the same tool, width within [widthTolerance] of the previous width, and —
     * the fork's own guard, because the joined stroke keeps the *old* stroke's style — the same
     * ink colour. A red line must never silently absorb a blue one.
     */
    fun canJoin(
        prevEnd: Pt,
        prevTool: Tool,
        prevWidth: Double,
        prevColor: Rgba,
        nextStart: Pt,
        nextTool: Tool,
        nextWidth: Double,
        nextColor: Rgba,
        maxGapPx: Double = DEFAULT_GAP_PX,
        widthTolerance: Double = WIDTH_TOLERANCE,
    ): Boolean {
        if (prevTool != nextTool) return false
        if (prevTool == Tool.ERASER || !prevTool.isStroke) return false
        if (prevColor != nextColor) return false
        val w = if (prevWidth > 0.0) prevWidth else 1.0
        if (kotlin.math.abs(nextWidth - prevWidth) > widthTolerance * w) return false
        return prevEnd.distanceTo(nextStart) <= maxGapPx.coerceAtLeast(0.0)
    }

    /**
     * The joined sample list: the previous stroke's samples with the new stroke's appended. The
     * new stroke's first sample is dropped when it sits within half a pixel of the previous end —
     * a coincident pair buys nothing and costs a degenerate arc step — otherwise the small hop it
     * represents becomes part of the line, which is what "joining" means visually.
     */
    fun joinSamples(prev: List<Sample>, next: List<Sample>): List<Sample> {
        if (prev.isEmpty()) return next
        if (next.isEmpty()) return prev
        val last = prev.last()
        val first = next.first()
        val head = if (Pt(last.x, last.y).distanceTo(Pt(first.x, first.y)) <= 0.5) next.drop(1) else next
        if (head.isEmpty()) return prev
        return prev + head
    }

    /**
     * The distance the join will open as a straight chord — the visual cost of joining two
     * strokes that do not exactly touch. The commit path can refuse a join whose bridge would
     * read as a stray line (the caller caps this by [canJoin]'s `maxGapPx` already).
     */
    fun bridgePx(prevEnd: Pt, nextStart: Pt): Double = prevEnd.distanceTo(nextStart)
}
