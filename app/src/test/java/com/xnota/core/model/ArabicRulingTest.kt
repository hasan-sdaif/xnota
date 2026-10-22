package com.xnota.core.model

import com.xnota.core.infinite.BackgroundPattern
import com.xnota.core.infinite.CanvasViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's Arabic calligraphy rulings — khatt (square grid, emphasized baseline every fourth
 * line), ruqaa (baselines alone, one every two cells), naskh (two-line measure: baseline + alif
 * height). These lock the family measure the paged painter, the GL shader and the bundled
 * templates all share.
 */
class ArabicRulingTest {

    private fun viewport(zoom: Double, sx: Double = 0.0, sy: Double = 0.0): CanvasViewport =
        CanvasViewport().apply {
            widthPx = 1000
            heightPx = 800
            this.zoom = zoom
            scrollX = sx
            scrollY = sy
        }

    @Test fun theArabicPatternIdsRoundTrip() {
        assertEquals(PagePattern.KHATT, PagePattern.fromId("khatt"))
        assertEquals(PagePattern.RUQAA, PagePattern.fromId("ruqaa"))
        assertEquals(PagePattern.NASKH, PagePattern.fromId("naskh"))
        assertEquals("khatt", PagePattern.KHATT.id)
        assertEquals("ruqaa", PagePattern.RUQAA.id)
        assertEquals("naskh", PagePattern.NASKH.id)
    }

    @Test fun theArabicTemplatesAreBuiltIn() {
        for (id in listOf("khatt", "ruqaa", "naskh")) {
            assertTrue("$id should be a built-in template", PageTemplates.isBuiltIn(id))
        }
    }

    @Test fun ruqaaSpacesItsBaselinesTwoCellsApart() {
        // Ruqaa writes fast, so its guides are baselines alone — the spacing control is the cell.
        val bg = com.xnota.core.infinite.CanvasBackground(
            PagePattern.RUQAA, com.xnota.core.model.Rgba(0, 0, 0, 64), 64.0,
        )
        val r = BackgroundPattern.resolve(bg, viewport(1.0))
        assertEquals(64.0 * PageStyle.RUQAA_CELLS, r.periodPx, 1e-9)
    }

    @Test fun aKhattCanvasCarriesTheEveryFourthLinePhase() {
        // The shader's heavy lattice is 4 × the period; the resolve must hand it that lattice's
        // own phase, which the base phase cannot recover (scroll mod period loses the count).
        val spacing = 64.0
        val zoom = 2.0
        val scroll = 1_000_003.0
        val bg = com.xnota.core.infinite.CanvasBackground(
            PagePattern.KHATT, com.xnota.core.model.Rgba(0, 0, 0, 64), spacing,
        )
        val r = BackgroundPattern.resolve(bg, viewport(zoom, scroll, scroll))
        val cell = spacing * BackgroundPattern.levelMultiplier(spacing, zoom)
        val quad = cell * PageStyle.KHATT_BASELINE_EVERY
        assertEquals(BackgroundPattern.phase(scroll, quad) * zoom, r.quadPhaseYPx, 1e-6)
        assertTrue("quad phase must stay inside its own lattice", r.quadPhaseYPx >= 0.0 && r.quadPhaseYPx < quad * zoom + 1e-9)
    }

    @Test fun theShaderHardcodedMeasuresMatchTheModelConstants() {
        // BackgroundShader bakes `period * 4.0` and `period * 0.35` into its GLSL; if these drift
        // from the model the paged painter and the infinite canvas would disagree.
        assertEquals(4.0, PageStyle.KHATT_BASELINE_EVERY.toDouble(), 0.0)
        assertEquals(0.35, PageStyle.NASKH_ALIF_FRACTION, 0.0)
        assertEquals(2.0, PageStyle.RUQAA_CELLS, 0.0)
    }

    @Test fun theEmphasizedKhattBaselineIsTripleWeight() {
        assertEquals(PageStyle.LINE_THICKNESS * 3.0, PageStyle.LINE_THICKNESS * PageStyle.KHATT_BASELINE_WIDTH, 1e-12)
    }
}
