package com.xnota.core.canvas

import com.xnota.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fork's measure math (B9): distances in the document's own millimetres through the page DPI
 * (the same conversion [com.xnota.core.model.PageSize] runs for the ruler), the angle against the
 * horizontal, and the readout formatting.
 */
class MeasureTest {

    @Test fun distanceIsPythagoras() {
        assertEquals(5.0, Measure.distancePx(Pt(0.0, 0.0), Pt(3.0, 4.0)), 1e-9)
        assertEquals(5.0, Measure.distancePx(Pt(3.0, 4.0), Pt(0.0, 0.0)), 1e-9)
        assertEquals(0.0, Measure.distancePx(Pt(1.0, 1.0), Pt(1.0, 1.0)), 1e-9)
    }

    @Test fun millimetresRunThroughThePageDpi() {
        // 150 dpi: 1 inch = 150 px = 25.4 mm, so 300 px = 50.8 mm — exactly PageSize's conversion.
        assertEquals(25.4, Measure.distanceMm(Pt(0.0, 0.0), Pt(150.0, 0.0), 150), 1e-9)
        assertEquals(50.8, Measure.distanceMm(Pt(0.0, 0.0), Pt(300.0, 0.0), 150), 1e-9)
        // And it is the ruler's own arithmetic, not a parallel one.
        assertEquals(
            com.xnota.core.model.PageSize.pxToMm(1234.0, 150),
            Measure.distanceMm(Pt(0.0, 0.0), Pt(1234.0, 0.0), 150),
            1e-12,
        )
    }

    @Test fun theAngleRunsAgainstTheHorizontal() {
        assertEquals(0.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(10.0, 0.0)), 1e-9)
        assertEquals(90.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(0.0, 10.0)), 1e-9) // y down = down-screen
        assertEquals(-90.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(0.0, -10.0)), 1e-9)
        assertEquals(45.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(10.0, 10.0)), 1e-9)
        assertEquals(180.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(-10.0, 0.0)), 1e-9)
        assertEquals(-135.0, Measure.angleDeg(Pt(0.0, 0.0), Pt(-10.0, -10.0)), 1e-9)
    }

    @Test fun theReadoutKeepsItsPrecisions() {
        assertEquals("12.3", Measure.readoutMm(12.34))
        assertEquals("12.4", Measure.readoutMm(12.35))
        assertEquals("0.0", Measure.readoutMm(0.049))
        assertEquals("45°", Measure.readoutDeg(44.6))
        assertEquals("-13°", Measure.readoutDeg(-12.5))
        assertEquals("12.3 mm · 45°", Measure.readout(12.34, 44.6))
        assertEquals("12.3 mm", Measure.readout(12.34, null))
    }
}
