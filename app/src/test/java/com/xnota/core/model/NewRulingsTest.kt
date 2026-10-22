package com.xnota.core.model

import com.xnota.core.FakeRenderer
import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import com.xnota.core.template.TemplateEval
import com.xnota.core.template.TemplatePage
import com.xnota.core.template.TemplateValues
import com.xnota.format.TemplateReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.tan

/**
 * The fork's canvas-power rulings (B9): isometric (30° triangles), staff (five-line staves) and
 * Cornell (cue column + summary band) — paged-only patterns (see the [PagePattern] doc), locked
 * here the way [ArabicRulingTest] locks the Arabic family: the ids, the built-in template keys,
 * the painter's line structure, and the bundled templates that draw the same thing.
 */
class NewRulingsTest {

    private val ink = Rgba(10, 20, 30, 255)
    private val page = Rect(0.0, 0.0, 100.0, 100.0)

    private fun painted(pattern: PagePattern, spacing: Double, region: Rect = page): FakeRenderer =
        FakeRenderer().also { paintPagePattern(it, pattern, ink, spacing, page, region) }

    private fun bundled(name: String) = TemplateReader.read(File("src/main/assets/templates/$name.xtemplate").readText())

    // --- the family table ---

    @Test fun theNewPatternIdsRoundTrip() {
        assertEquals(PagePattern.ISOMETRIC, PagePattern.fromId("isometric"))
        assertEquals(PagePattern.STAFF, PagePattern.fromId("staff"))
        assertEquals(PagePattern.CORNELL, PagePattern.fromId("cornell"))
        assertEquals(null, PagePattern.fromId("bogus"))
    }

    @Test fun theNewTemplatesAreBuiltIn() {
        for (id in listOf("isometric", "staff", "cornell")) {
            assertTrue("$id should be a built-in template", PageTemplates.isBuiltIn(id))
        }
    }

    @Test fun aStaffIsFiveLinesInSixCells() {
        assertEquals(5, PageStyle.STAFF_LINES)
        assertEquals(6.0, PageStyle.STAFF_CELLS, 0.0)
        assertEquals(30.0, PageStyle.ISOMETRIC_ANGLE_DEG, 0.0)
    }

    // --- the painter ---

    @Test fun thePainterDrawsIsometricTriangles() {
        val r = painted(PagePattern.ISOMETRIC, 20.0)
        val segs = r.segments
        // Two families at ±30°, each pitched spacing·sin60° = 17.32 across a 100×100 page:
        // the +30° family spans 100·(sin30+cos30) = 136.6 of normal distance → 7 crossing lines
        // (the corner-grazing one draws nothing), likewise mirrored → 14 segments.
        assertEquals(14, segs.size)
        for ((a, b) in segs) {
            assertEquals(tan(Math.toRadians(30.0)), abs((b.y - a.y) / (b.x - a.x)), 1e-9)
        }
        // Both tilts are present.
        val slopes = segs.map { (a, b) -> (b.y - a.y) / (b.x - a.x) }.toSet()
        assertTrue(slopes.any { it > 0 } && slopes.any { it < 0 })
    }

    @Test fun theIsometricLatticeHoldsAcrossSubRegions() {
        val whole = painted(PagePattern.ISOMETRIC, 20.0).segments
        val slice = painted(PagePattern.ISOMETRIC, 20.0, region = Rect(50.0, 50.0, 50.0, 50.0)).segments
        // The slice's lines are the whole's lines cut to the slice: same slopes, all inside.
        assertTrue(slice.isNotEmpty())
        fun inside(p: Pt, r: Rect) = p.x >= r.left - 1e-9 && p.x <= r.right + 1e-9 &&
            p.y >= r.top - 1e-9 && p.y <= r.bottom + 1e-9
        for ((a, b) in slice) {
            assertTrue("($a) inside", inside(a, Rect(50.0, 50.0, 50.0, 50.0)))
            assertTrue("($b) inside", inside(b, Rect(50.0, 50.0, 50.0, 50.0)))
            val slope = (b.y - a.y) / (b.x - a.x)
            assertTrue(whole.any { (c, d) -> abs((d.y - c.y) / (d.x - c.x) - slope) < 1e-9 })
        }
    }

    @Test fun thePainterDrawsFiveLineStaves() {
        val r = painted(PagePattern.STAFF, 10.0)
        val ys = r.segments.map { it.first.y }.sorted()
        // Staves at 0 and 60 (a third would start at 120): their lines at 0..40 and 60..100, the
        // page's own edges skipped — 10, 20, 30, 40 then 60, 70, 80, 90.
        assertEquals(listOf(10.0, 20.0, 30.0, 40.0, 60.0, 70.0, 80.0, 90.0), ys)
        // Every line runs the page's width.
        for ((a, b) in r.segments) {
            assertEquals(0.0, a.x, 1e-9)
            assertEquals(100.0, b.x, 1e-9)
            assertEquals(a.y, b.y, 1e-9)
        }
    }

    @Test fun thePainterDrawsTheCornellLayout() {
        val r = painted(PagePattern.CORNELL, 10.0)
        assertEquals(2, r.segments.size)
        val v = r.segments.first { it.first.x == it.second.x }
        val h = r.segments.first { it.first.y == it.second.y }
        assertEquals(30.0, v.first.x, 1e-9) // the cue column at 30% across
        assertEquals(0.0, v.first.y, 1e-9)
        assertEquals(100.0, v.second.y, 1e-9)
        assertEquals(75.0, h.first.y, 1e-9) // the summary band below 75% down
        assertEquals(0.0, h.first.x, 1e-9)
        assertEquals(100.0, h.second.x, 1e-9)
    }

    // --- the bundled templates draw the same thing ---

    private fun eval(name: String, spacingMm: Double? = null, w: Double = 100.0, h: Double = 100.0) =
        TemplateEval().evaluate(
            bundled(name),
            TemplatePage(
                w, h, Rect(0.0, 0.0, w, h), ink, ink,
                TemplateValues(if (spacingMm != null) mapOf("spacing" to spacingMm) else emptyMap()),
            ),
        )

    private fun strokes(out: com.xnota.core.template.TemplateOutput) =
        out.layers.flatMap { it.prims }.filterIsInstance<com.xnota.core.template.TPrim.Stroke>()

    @Test fun theIsometricTemplateDrawsBothTilts() {
        val out = eval("isometric", spacingMm = 10.0)
        val lines = strokes(out)
        assertTrue("needs a real grid, got ${lines.size}", lines.size >= 10)
        for (s in lines) {
            assertEquals(tan(Math.toRadians(30.0)), abs((s.pts[3] - s.pts[1]) / (s.pts[2] - s.pts[0])), 1e-6)
        }
        val slopes = lines.map { s -> (s.pts[3] - s.pts[1]) / (s.pts[2] - s.pts[0]) }
        assertTrue(slopes.any { it > 0 } && slopes.any { it < 0 })
    }

    @Test fun theStaffTemplateGroupsItsLinesIntoStaves() {
        val spacing = 2.0
        val out = eval("staff", spacingMm = spacing)
        val ys = strokes(out).map { it.pts[1] }.sorted()
        assertTrue("needs real staves, got ${ys.size}", ys.size >= 20)
        val gaps = ys.zipWithNext { a, b -> b - a }
        // Inside a stave the lines sit `spacing` apart; between staves, twice that (one clear cell).
        for (g in gaps) assertTrue("gap $g is neither line nor air", abs(g - spacing) < 1e-6 || abs(g - 2 * spacing) < 1e-6)
        assertTrue("needs the clear air between staves", gaps.count { abs(it - 2 * spacing) < 1e-6 } >= 4)
    }

    @Test fun theCornellTemplateIsTheTwoLayoutLines() {
        val out = eval("cornell", w = 210.0, h = 297.0)
        val lines = strokes(out)
        assertEquals(2, lines.size)
        val v = lines.first { abs(it.pts[2] - it.pts[0]) < 1e-9 } // vertical: x1 == x2
        val h = lines.first { abs(it.pts[3] - it.pts[1]) < 1e-9 }
        assertEquals(210.0 * 0.30, v.pts[0], 1e-6)
        assertEquals(297.0 * 0.75, h.pts[1], 1e-6)
    }
}
