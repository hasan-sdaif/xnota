package com.xnota.core.model

import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's image crop math (B9): normalized crop clamping, the aspect presets' re-fit around
 * the crop's own centre, and the rounded-corner walk — the geometry the crop overlay and the
 * corner-radius slider stand on.
 */
class CropMathTest {

    // --- clamping ---

    @Test fun clampFoldsEverythingBackInsideThePicture() {
        assertEquals(CropMath.FULL, CropMath.clamp(CropMath.CropRect(0.0, 0.0, 1.0, 1.0)))
        // An oversized width gives up at the right edge, not past it.
        assertEquals(CropMath.CropRect(0.2, 0.0, 0.8, 1.0), CropMath.clamp(CropMath.CropRect(0.2, 0.0, 5.0, 1.0)))
        assertEquals(CropMath.CropRect(0.0, 0.3, 1.0, 0.7), CropMath.clamp(CropMath.CropRect(0.0, 0.3, 1.0, 9.0)))
        // Non-finite fields land at zero.
        assertEquals(CropMath.CropRect(0.0, 0.0, 0.5, 0.5), CropMath.clamp(CropMath.CropRect(Double.NaN, 0.0, 0.5, 0.5)))
        assertEquals(CropMath.CropRect(0.1, 0.0, 0.5, 0.5), CropMath.clamp(CropMath.CropRect(0.1, Double.NaN, 0.5, 0.5)))
    }

    @Test fun clampKeepsANegativeOriginInsideToo() {
        val c = CropMath.clamp(CropMath.CropRect(-0.2, -0.1, 0.5, 0.5))
        assertEquals(0.0, c.left, 1e-9)
        assertEquals(0.0, c.top, 1e-9)
        assertEquals(0.5, c.width, 1e-9)
    }

    // --- aspect ---

    @Test fun theAspectAccountsForTheSourcePixels() {
        // A normalized square of a 2000×1000 picture is 2:1 wide, not square.
        assertEquals(2.0, CropMath.aspect(CropMath.CropRect(0.0, 0.0, 0.5, 0.5), 2000, 1000), 1e-9)
        assertEquals(1.0, CropMath.aspect(CropMath.CropRect(0.0, 0.0, 0.5, 0.5), 1000, 1000), 1e-9)
    }

    @Test fun freeKeepsWhateverTheCropHolds() {
        val c = CropMath.CropRect(0.1, 0.1, 0.5, 0.3)
        assertEquals(c, CropMath.fitToAspect(c, CropMath.Aspect.FREE, 1000, 1000))
    }

    @Test fun fittingASquareShrinksTheTooWideDimensionAroundTheCentre() {
        val c = CropMath.CropRect(0.0, 0.0, 1.0, 0.5) // 2:1 on a 1000×1000 source
        val fitted = CropMath.fitToAspect(c, CropMath.Aspect.SQUARE, 1000, 1000)
        assertEquals(0.25, fitted.left, 1e-9)
        assertEquals(0.5, fitted.width, 1e-9)
        assertEquals(0.5, fitted.height, 1e-9)
        assertEquals(0.0, fitted.top, 1e-9)
    }

    @Test fun fittingNarrowsTheTooTallDimensionToo() {
        val c = CropMath.CropRect(0.25, 0.0, 0.5, 1.0) // 1:2 tall
        val fitted = CropMath.fitToAspect(c, CropMath.Aspect.SQUARE, 1000, 1000)
        assertEquals(0.25, fitted.left, 1e-9)
        assertEquals(0.5, fitted.width, 1e-9)
        assertEquals(0.25, fitted.top, 1e-9)
        assertEquals(0.5, fitted.height, 1e-9)
    }

    @Test fun thePresetRatiosAreWhatTheySay() {
        assertEquals(1.0, CropMath.Aspect.SQUARE.ratio!!, 1e-12)
        assertEquals(4.0 / 3.0, CropMath.Aspect.TV.ratio!!, 1e-12)
        assertEquals(16.0 / 9.0, CropMath.Aspect.WIDE.ratio!!, 1e-12)
        assertEquals(3.0 / 2.0, CropMath.Aspect.PHOTO.ratio!!, 1e-12)
        // 16:9 on a wide crop keeps the height and widens no further than the picture.
        val fitted = CropMath.fitToAspect(CropMath.FULL, CropMath.Aspect.WIDE, 1600, 1000)
        assertEquals(1600.0 / 900.0, CropMath.aspect(fitted, 1600, 1000), 1e-9)
    }

    @Test fun aCentreNearTheEdgeGivesUpCentringNotTheRatio() {
        // A degenerate height under a square lock re-fits to the whole picture's square rather
        // than pushing the crop past the edge: the clamp wins, the ratio survives.
        val sliver = CropMath.CropRect(0.9, 0.0, 0.1, 0.0) // centre x 0.95, no height to keep
        val fitted = CropMath.fitToAspect(sliver, CropMath.Aspect.SQUARE, 1000, 1000)
        assertEquals(1.0, CropMath.aspect(fitted, 1000, 1000), 1e-9)
        assertTrue(fitted.right <= 1.0 + 1e-9)
        assertTrue(fitted.left >= 0.0 - 1e-9)
        assertEquals(CropMath.FULL, fitted)
    }

    // --- rounded corners ---

    @Test fun aZeroRadiusIsThePlainRectangle() {
        val rect = Rect(0.0, 0.0, 10.0, 6.0)
        val pts = CropMath.roundedRect(rect, 0.0)
        assertEquals(listOf(rect.topLeft, Pt(10.0, 0.0), Pt(10.0, 6.0), Pt(0.0, 6.0)), pts)
    }

    @Test fun anOversizedRadiusClampsToHalfTheShorterSide() {
        val rect = Rect(0.0, 0.0, 10.0, 6.0)
        val pts = CropMath.roundedRect(rect, 99.0)
        // r = 3 (half of 6): a stadium. The walk starts one r in from the top-left corner, the
        // sharp corners are gone, and the left/right extremes sit at the vertical middle.
        assertEquals(Pt(3.0, 0.0), pts.first())
        assertTrue(pts.any { it.distanceTo(Pt(10.0, 3.0)) < 1e-9 })
        assertTrue(pts.any { it.distanceTo(Pt(0.0, 3.0)) < 1e-9 })
        for (sharp in listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 6.0), Pt(0.0, 6.0))) {
            assertTrue("the sharp corner $sharp must be gone", sharp !in pts)
        }
        for (p in pts) assertTrue("($p) inside", rect.contains(p))
    }

    @Test fun theWalkClosesAndRunsOnceRound() {
        val rect = Rect(1.0, 2.0, 20.0, 10.0)
        val pts = CropMath.roundedRect(rect, 3.0)
        assertEquals(1 + 4 * (CropMath.CORNER_SIDES + 1), pts.size)
        // The last arc's final point is the walk's own start point (up to the arcs' float dust).
        assertEquals(pts.first().x, pts.last().x, 1e-9)
        assertEquals(pts.first().y, pts.last().y, 1e-9)
    }

    @Test fun theCornersAreArcsOfTheRadius() {
        val rect = Rect(0.0, 0.0, 10.0, 10.0)
        val r = 2.0
        val pts = CropMath.roundedRect(rect, r)
        // The top-right arc's centre is (8, 2); every one of its points sits r away.
        val arc = pts.subList(1, 1 + CropMath.CORNER_SIDES + 1)
        for (p in arc) assertEquals(r, p.distanceTo(Pt(10.0 - r, r)), 1e-9)
        // Its first point rides the top edge, its last the right edge.
        assertEquals(10.0 - r, arc.first().x, 1e-9)
        assertEquals(0.0, arc.first().y, 1e-9)
        assertEquals(10.0, arc.last().x, 1e-9)
        assertEquals(r, arc.last().y, 1e-9)
    }
}
