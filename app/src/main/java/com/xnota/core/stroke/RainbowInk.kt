package com.xnota.core.stroke

import com.xnota.core.model.Rgba

/**
 * The rainbow brush (the fork's Tool.RAINBOW): the hue rotates along the stroke's arc length —
 * `hue = base + arc · 0.15°/px (mod 360)` — so a line drawn in one motion sweeps a full turn
 * roughly every 2.4 cm of travel.
 *
 * The stored stroke colour is only the **base**; the rotation is a render-time read, so a
 * rainbow stroke re-renders identically forever and a theme's ink transform can still re-tone the
 * base. HSV conversion is hand-rolled here (pure Kotlin, no android.graphics) so the progression
 * is unit-testable on the JVM.
 */
object RainbowInk {

    /** Hue degrees gained per content px of arc. */
    const val DEG_PER_PX = 0.15

    /**
     * The hue [arcPx] along a stroke whose base hue is [baseHueDeg], wrapped into `[0, 360)`.
     * Pure in both arguments; negative arcs wrap the same way.
     */
    fun hueAt(baseHueDeg: Double, arcPx: Double): Double {
        val h = (baseHueDeg + arcPx * DEG_PER_PX) % 360.0
        return if (h < 0.0) h + 360.0 else h
    }

    /**
     * The chunk length the renderers step the hue in: shorter than the eye's colour-resolution
     * along a line, long enough that a chunked ribbon is a handful of parts, not hundreds.
     */
    const val CHUNK_PX = 24.0

    /**
     * How many leading ribbon points each rendered chunk spans, so the hue advances one step per
     * chunk whatever the sample spacing. Returns the list of `(from, count)` runs covering
     * `[0, n)` with consecutive runs overlapping by one point (the seam disc is shared, exactly
     * like the wet-cache runs).
     */
    fun chunks(xs: DoubleArray, ys: DoubleArray, chunkPx: Double = CHUNK_PX): List<Pair<Int, Int>> {
        val n = xs.size
        if (n == 0) return emptyList()
        if (n == 1) return listOf(0 to 1)
        val out = ArrayList<Pair<Int, Int>>(n / 4 + 2)
        var from = 0
        var arc = 0.0
        var i = 1
        while (i < n) {
            arc += kotlin.math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            if (arc >= chunkPx) {
                out.add(from to (i - from + 1))
                from = i
                arc = 0.0
            }
            i++
        }
        if (from < n) out.add(from to (n - from))
        return out
    }

    // --- pure HSV, no platform colour space ---

    /** RGB in `[0, 1]` for a hue in degrees, saturation and value in `[0, 1]`. */
    fun hsvToRgb(hDeg: Double, s: Double, v: Double): Triple<Double, Double, Double> {
        val ss = s.coerceIn(0.0, 1.0)
        val vv = v.coerceIn(0.0, 1.0)
        if (ss <= 0.0) return Triple(vv, vv, vv)
        val h = ((hDeg % 360.0) + 360.0) % 360.0 / 60.0
        val i = h.toInt().coerceIn(0, 5)
        val f = h - i
        val p = vv * (1.0 - ss)
        val q = vv * (1.0 - ss * f)
        val t = vv * (1.0 - ss * (1.0 - f))
        return when (i) {
            0 -> Triple(vv, t, p)
            1 -> Triple(q, vv, p)
            2 -> Triple(p, vv, t)
            3 -> Triple(p, q, vv)
            4 -> Triple(t, p, vv)
            else -> Triple(vv, p, q)
        }
    }

    /** HSV triple for an RGB triple in `[0, 1]`; hue in degrees. */
    fun rgbToHsv(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val rr = r.coerceIn(0.0, 1.0)
        val gg = g.coerceIn(0.0, 1.0)
        val bb = b.coerceIn(0.0, 1.0)
        val max = maxOf(rr, gg, bb)
        val min = minOf(rr, gg, bb)
        val delta = max - min
        val v = max
        val s = if (max <= 0.0) 0.0 else delta / max
        if (delta <= 0.0) return Triple(0.0, s, v)
        val h = when (max) {
            rr -> 60.0 * (((gg - bb) / delta) % 6.0)
            gg -> 60.0 * ((bb - rr) / delta + 2.0)
            else -> 60.0 * ((rr - gg) / delta + 4.0)
        }
        return Triple(((h % 360.0) + 360.0) % 360.0, s, v)
    }

    /**
     * The colour a rainbow stroke draws at [arcPx]: the base colour's hue rotated by the arc,
     * with the base's own saturation and value (and alpha) preserved. Opaque bases stay opaque.
     */
    fun colorAt(base: Rgba, arcPx: Double): Rgba {
        val (h0, s, v) = rgbToHsv(base.r / 255.0, base.g / 255.0, base.b / 255.0)
        val h = hueAt(h0, arcPx)
        val (r, g, b) = hsvToRgb(h, s, v)
        return Rgba(
            (r * 255.0 + 0.5).toInt().coerceIn(0, 255),
            (g * 255.0 + 0.5).toInt().coerceIn(0, 255),
            (b * 255.0 + 0.5).toInt().coerceIn(0, 255),
            base.a,
        )
    }
}
