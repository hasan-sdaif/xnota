package com.xnota.core.stroke

import com.xnota.core.geometry.Pt
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scribble to erase: tells a scribble-out from ink, and finds what it covers.
 *
 * A scribble is a run of passes back and forth with a sharp turn between each. Handwriting has
 * passes too (an m, a w, a cursive l), but its turns alternate sharp and round: a cusp then an arch,
 * a loop's top then the curve into the next letter. Requiring nearly every turn to be sharp is what
 * keeps writing out, and requiring more passes than an M or a W has keeps capitals out.
 *
 * Being a scribble is not enough to erase. Most of its passes have to cross something in their
 * middle, away from the turns, so hatching inside an outline (which only meets the outline where it
 * turns back) stays ink.
 *
 * Every length is judged in dp, so a scribble reads the same at any zoom.
 */
object Scribble {
    /** Spacing of the resampled path, in dp. */
    private const val STEP_DP = 1.0

    /** A pass ends once the pen has come back this share of it toward where the pass began. */
    private const val RETREAT_FRAC = 0.3

    /** But never on a smaller wobble than this, in dp. */
    private const val RETREAT_MIN_DP = 4.0

    /** How nearly along a pass the pen has to keep heading to carry its far end on (cos 25°). */
    private const val AHEAD_COS = 0.9

    /** Passes it takes to be a scribble: an M or a W has four. */
    private const val MIN_PASSES = 5

    /** Median pass, in dp, below which it is a jitter rather than a scribble. */
    private const val MIN_PASS_DP = 8.0

    /** A turn is sharp when the passes either side of it are this close together, in degrees. */
    private const val SHARP_DEG = 75.0

    /** How far from a turn its opening is measured, as a share of the shorter pass. */
    private const val OPENING_AT = 0.3

    /** Share of the turns that have to be sharp. */
    private const val SHARP_SHARE = 0.75

    /** The share of each pass, at either end, that belongs to its turns rather than its middle. */
    private const val TURN_FRAC = 0.2

    /** Share of the passes that have to cross something in their middle. */
    private const val CROSSING_SHARE = 0.5

    /** Reach past the scribble's own ink within which it counts as touching, in dp. */
    const val TOUCH_DP = 2.0

    /** A scribble's path resampled every [step] content units, and the indices its passes turn at. */
    class Passes(val points: List<Pt>, val step: Double, val turns: IntArray) {
        val count: Int get() = turns.size - 1
    }

    /** The passes of [path] (content space) when it reads as a scribble, else null. */
    fun passes(path: List<Pt>, dpPerUnit: Double): Passes? {
        if (path.size < 2 || dpPerUnit <= 0.0) return null
        val step = STEP_DP / dpPerUnit
        val q = resample(path, step)
        if (q.size < MIN_PASSES * 2) return null
        val turns = turnsOf(q, dpPerUnit)
        val count = turns.size - 1
        if (count < MIN_PASSES) return null
        val lengths = (0 until count).map { turns[it + 1] - turns[it] }.sorted()
        if (lengths[count / 2] * STEP_DP < MIN_PASS_DP) return null
        var sharp = 0
        for (k in 1 until turns.size - 1) if (opening(q, turns[k - 1], turns[k], turns[k + 1]) <= SHARP_DEG) sharp++
        if (sharp < ceil((count - 1) * SHARP_SHARE)) return null
        return Passes(q, step, turns)
    }

    /**
     * The [candidates] a scribble takes: every one [touches] finds within [radius] of its path, or
     * none when too few of its passes cross anything in their middle.
     */
    fun <T> taken(passes: Passes, radius: Double, candidates: List<T>, touches: (T, Double, Double, Double) -> Boolean): List<T> {
        if (candidates.isEmpty()) return emptyList()
        val q = passes.points
        val turns = passes.turns
        val middleOf = IntArray(q.size) { -1 }
        for (k in 0 until passes.count) {
            val a = turns[k]
            val b = turns[k + 1]
            val cut = ((b - a) * TURN_FRAC).roundToInt()
            for (i in a + cut..b - cut) middleOf[i] = k
        }
        val stride = max(1, floor(radius / 2.0 / passes.step).toInt())
        val crossed = BooleanArray(passes.count)
        val out = ArrayList<T>()
        for (c in candidates) {
            var hit = false
            var i = 0
            while (true) {
                val p = q[i]
                if (touches(c, p.x, p.y, radius)) {
                    hit = true
                    if (middleOf[i] >= 0) crossed[middleOf[i]] = true
                }
                if (i == q.lastIndex) break
                i = min(i + stride, q.lastIndex)
            }
            if (hit) out.add(c)
        }
        return if (crossed.count { it } >= ceil(passes.count * CROSSING_SHARE)) out else emptyList()
    }

    /** [path] walked at even [step]s, its ends kept. */
    private fun resample(path: List<Pt>, step: Double): List<Pt> {
        val out = ArrayList<Pt>()
        out.add(path[0])
        var px = path[0].x
        var py = path[0].y
        var carry = 0.0
        for (i in 1 until path.size) {
            val nx = path[i].x
            val ny = path[i].y
            var seg = hypot(nx - px, ny - py)
            while (carry + seg >= step) {
                val t = (step - carry) / seg
                px += (nx - px) * t
                py += (ny - py) * t
                out.add(Pt(px, py))
                seg = hypot(nx - px, ny - py)
                carry = 0.0
            }
            carry += seg
            px = nx
            py = ny
        }
        if (carry > 0.0) out.add(path.last())
        return out
    }

    /**
     * Where [q] turns back: the far end of each pass, plus its two ends. Coming back is measured
     * along the pass, not toward where it began, so a zigzag that travels as it goes still turns.
     */
    private fun turnsOf(q: List<Pt>, dpPerUnit: Double): IntArray {
        val turns = ArrayList<Int>()
        turns.add(0)
        var anchor = 0
        var far = 0
        var farD = 0.0
        for (i in 1 until q.size) {
            val o = q[anchor]
            val f = q[far]
            val d = q[i].distanceTo(o) * dpPerUnit
            val along = if (far == anchor) d else ((q[i].x - o.x) * (f.x - o.x) + (q[i].y - o.y) * (f.y - o.y)) / f.distanceTo(o) * dpPerUnit
            // Only a pen still heading the pass's way carries its far end on; one coming back down
            // the next pass of a travelling zigzag can end up further away without going further.
            if (d >= farD && (farD < RETREAT_MIN_DP || along >= AHEAD_COS * d)) {
                far = i
                farD = d
                continue
            }
            if (farD - along < max(RETREAT_MIN_DP, RETREAT_FRAC * farD)) continue
            turns.add(far)
            anchor = far
            farD = 0.0
            for (j in anchor + 1..i) {
                val dj = q[j].distanceTo(q[anchor]) * dpPerUnit
                if (dj >= farD) {
                    far = j
                    farD = dj
                }
            }
        }
        if (turns.last() != q.lastIndex) turns.add(q.lastIndex)
        return turns.toIntArray()
    }

    /** The angle, in degrees, the passes either side of the turn at [at] open to, a little way out. */
    private fun opening(q: List<Pt>, from: Int, at: Int, to: Int): Double {
        val off = max(1, (min(at - from, to - at) * OPENING_AT).roundToInt())
        val o = q[at]
        val a = q[at - off]
        val b = q[at + off]
        val ax = a.x - o.x
        val ay = a.y - o.y
        val bx = b.x - o.x
        val by = b.y - o.y
        val n = hypot(ax, ay) * hypot(bx, by)
        if (n <= 0.0) return 0.0
        return acos(((ax * bx + ay * by) / n).coerceIn(-1.0, 1.0)) * 180.0 / PI
    }
}
