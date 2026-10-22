package com.xnota.core.canvas

import com.xnota.core.geometry.Pt
import com.xnota.core.model.PageSize
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The fork's measure math: the distance between two points in the document's own units (content
 * px read as millimetres through the page DPI, exactly as [PageSize.pxToMm] does for the ruler),
 * the angle a run makes against the horizontal, and the live readout's number formatting. Pure
 * arithmetic; the HUD that shows it lives in the editors.
 */
object Measure {

    /** The straight-line distance between two points, in content px. */
    fun distancePx(a: Pt, b: Pt): Double = hypot(b.x - a.x, b.y - a.y)

    /** That distance read as millimetres at the document's [dpi]. */
    fun distanceMm(a: Pt, b: Pt, dpi: Int): Double = PageSize.pxToMm(distancePx(a, b), dpi)

    /**
     * The angle of the run a→b against the horizontal, in degrees, `(-180, 180]`: 0 = heading
     * +x, 90 = heading down the page's +y (screen coordinates), −90 = up. The caller picks the
     * reading that suits its readout.
     */
    fun angleDeg(a: Pt, b: Pt): Double {
        val deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
        return if (deg <= -180.0) 180.0 else deg
    }

    /** A length readout: millimetres to one decimal (the ruler's own precision). */
    fun readoutMm(mm: Double): String = String.format("%.1f", mm)

    /** An angle readout: whole degrees, halves away from zero (−12.5 reads −13, +12.5 reads 13). */
    fun readoutDeg(deg: Double): String {
        val r = if (deg < 0.0) -kotlin.math.floor(-deg + 0.5) else kotlin.math.floor(deg + 0.5)
        return "${r.toLong()}°"
    }

    /** A live readout of both: `12.3 mm · 45°`, with the angle only when the caller passes one. */
    fun readout(mm: Double, deg: Double?): String =
        if (deg == null) "${readoutMm(mm)} mm" else "${readoutMm(mm)} mm · ${readoutDeg(deg)}"
}
