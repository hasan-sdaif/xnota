package com.xnota.platform

import android.graphics.Path
import android.graphics.PathMeasure
import com.xnota.core.geometry.Pt
import com.xnota.core.vector.PathFlattener
import com.xnota.core.vector.VectorContour
import com.xnota.core.vector.VectorSeg

/**
 * Glyph outlines read back out of a platform `Path`, which below API 34 cannot be read verb by
 * verb. So it is walked with a `PathMeasure` at a fine step and then decimated: the walk gives
 * exact contour boundaries, which triangulation needs to tell a counter from a bowl, and the
 * decimation puts the straight parts back to two points each.
 */
internal object GlyphPaths {

    /** Every contour of [source], text [size] px tall, as a closed polyline. */
    fun contours(source: Path, size: Double): List<VectorContour> {
        val step = size / SAMPLES_PER_EM
        val tolerance = size / DECIMATE_DENOMINATOR
        val measure = PathMeasure(source, false)
        val out = ArrayList<VectorContour>()
        val at = FloatArray(2)
        var guard = MAX_CONTOURS
        do {
            val length = measure.length
            if (length > 1e-4) {
                val count = kotlin.math.ceil(length / step).toInt().coerceIn(2, MAX_SAMPLES)
                val pts = ArrayList<Pt>(count + 1)
                for (i in 0..count) {
                    val d = length * i / count
                    if (measure.getPosTan(d, at, null)) pts.add(Pt(at[0].toDouble(), at[1].toDouble()))
                }
                val thinned = PathFlattener.decimate(pts, tolerance)
                if (thinned.size >= 3) out.add(contourOf(thinned))
            }
        } while (measure.nextContour() && guard-- > 0)
        return out
    }

    private fun contourOf(points: List<Pt>): VectorContour {
        // A closed glyph contour comes back with its last point on its first; drop the repeat.
        val last = points.size - 1
        val ring = if (near(points[0], points[last])) points.subList(0, last) else points
        return VectorContour(ring[0], ring.drop(1).map { VectorSeg.Line(it) }, closed = true)
    }

    private fun near(a: Pt, b: Pt) = kotlin.math.abs(a.x - b.x) < 1e-4 && kotlin.math.abs(a.y - b.y) < 1e-4

    /** How finely a contour is walked, in samples per em of arc length. */
    private const val SAMPLES_PER_EM = 64.0

    /** Decimation tolerance as a fraction of the em: well under a pixel at any real zoom. */
    private const val DECIMATE_DENOMINATOR = 2000.0

    private const val MAX_SAMPLES = 4096
    private const val MAX_CONTOURS = 4096
}
