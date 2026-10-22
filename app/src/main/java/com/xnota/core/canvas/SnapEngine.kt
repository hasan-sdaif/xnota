package com.xnota.core.canvas

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round

/**
 * Magnetic snapping for the selection gestures (the fork's canvas power): the grid, other items'
 * edges and centres, and quarter-turns of rotation. Pure point math on content/page space — the
 * controllers decide which space they snap in and apply the returned delta at their commit points.
 *
 * Every function is *advisory*: it returns the snap it found within its threshold and leaves the
 * caller free to ignore it, so the toggles in the View menu gate exactly one call site each.
 */
object SnapEngine {

    /** How far a rotation may sit from a step and still snap onto it, in degrees. */
    const val ROTATE_TOLERANCE_DEG = 5.0

    /** The rotation step the rotate grip snaps to, in degrees. */
    const val ROTATE_STEP_DEG = 15.0

    /**
     * The nearest grid node to [p] on a lattice of [step] anchored at the origin. A non-finite or
     * non-positive step returns [p] unchanged, so a broken setting disables the grid rather than
     * exploding the drag.
     */
    fun snapToGrid(p: Pt, step: Double): Pt {
        if (!step.isFinite() || step <= 0.0) return p
        // The + 0.0 folds a -0.0 back onto 0.0, so "already on the node" stays exactly zero.
        return Pt(round(p.x / step) * step + 0.0, round(p.y / step) * step + 0.0)
    }

    /** The delta that carries [p] onto its nearest grid node ([Pt.ZERO] when it is already on one). */
    fun gridDelta(p: Pt, step: Double): Pt = snapToGrid(p, step) - p

    /**
     * The best magnetic delta for a [moving] rect against [others], each axis on its own: the
     * moving rect's left/right/centre may align with any other's left/right/centre, and the
     * smallest alignment error within [threshold] wins that axis. Axes with no candidate within
     * the threshold stay at zero, so a drag can lock onto a vertical edge while still moving
     * freely up and down — which is what makes the pull feel magnetic rather than sticky.
     *
     * Self-intersection is the caller's business: pass [others] without the moving item's own rect.
     */
    fun shapeDelta(moving: Rect, others: List<Rect>, threshold: Double): Pt {
        var bestX = 0.0
        var bestXErr = threshold + 1.0
        var bestY = 0.0
        var bestYErr = threshold + 1.0
        val mX = listOf(moving.left, moving.centerX, moving.right)
        val mY = listOf(moving.top, moving.centerY, moving.bottom)
        for (o in others) {
            for (ov in listOf(o.left, o.centerX, o.right)) {
                for (mv in mX) {
                    val err = abs(mv - ov)
                    if (err < bestXErr) {
                        bestXErr = err
                        bestX = ov - mv
                    }
                }
            }
            for (ov in listOf(o.top, o.centerY, o.bottom)) {
                for (mv in mY) {
                    val err = abs(mv - ov)
                    if (err < bestYErr) {
                        bestYErr = err
                        bestY = ov - mv
                    }
                }
            }
        }
        if (bestXErr > threshold) bestX = 0.0
        if (bestYErr > threshold) bestY = 0.0
        return Pt(bestX, bestY)
    }

    /**
     * [theta] snapped to the nearest [stepDeg] multiple when it sits within [toleranceDeg] of one,
     * else [theta] unchanged. Any winding is folded first, so −170° snaps to −165°, not to 15°.
     */
    fun snapAngle(theta: Double, toleranceDeg: Double = ROTATE_TOLERANCE_DEG, stepDeg: Double = ROTATE_STEP_DEG): Double {
        if (!theta.isFinite() || stepDeg <= 0.0) return theta
        val step = stepDeg * PI / 180.0
        val nearest = round(theta / step) * step
        val tol = toleranceDeg * PI / 180.0
        return if (abs(theta - nearest) <= tol) nearest else theta
    }
}
