package com.xnota.core.stroke

import com.xnota.core.model.Stroke
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Draw symmetry: the pure mirror/rotate arithmetic and the stroke echoes built on it. */
class SymmetryTest {

    private fun stroke(vararg pts: Pair<Double, Double>): Stroke = Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN)).apply {
        finished = false
        for ((x, y) in pts) addSample(com.xnota.core.stroke.Sample(x, y, 0.7, 0.0))
    }

    @Test fun mirrorsAndClamps() {
        assertEquals(9.0, Symmetry.mirrorX(1.0, 5.0), 1e-12)
        assertEquals(1.0, Symmetry.mirrorY(9.0, 5.0), 1e-12)
        assertEquals(2, Symmetry.segments(0))
        assertEquals(8, Symmetry.segments(99))
        assertEquals(5, Symmetry.segments(5))
    }

    @Test fun rotationMath() {
        // (1,0) rotated 90° about the origin lands on (0,1).
        val x = Symmetry.rotateX(1.0, 0.0, 0.0, 0.0, PI / 2)
        val y = Symmetry.rotateY(1.0, 0.0, 0.0, 0.0, PI / 2)
        assertEquals(0.0, x, 1e-12)
        assertEquals(1.0, y, 1e-12)
    }

    @Test fun verticalEchoesExcludeTheOriginal() {
        val s = stroke(10.0 to 50.0, 20.0 to 60.0)
        val echoes = Symmetry.mirrorStrokes(s, SymmetryMode.VERTICAL, 6, 100.0, 100.0)
        assertEquals(1, echoes.size)
        assertEquals(190.0, echoes[0].firstSample!!.x, 1e-9)
        assertEquals(50.0, echoes[0].firstSample!!.y, 1e-9)
    }

    @Test fun quadEchoesAreFourMinusOne() {
        val s = stroke(10.0 to 10.0, 30.0 to 40.0)
        val echoes = Symmetry.mirrorStrokes(s, SymmetryMode.QUAD, 6, 100.0, 100.0)
        assertEquals(3, echoes.size)
    }

    @Test fun radialEchoesIncludeTheOriginalOnce() {
        val s = stroke(60.0 to 50.0, 70.0 to 50.0)
        // 6 segments: the original sits at angle 0; the echoes are the other five.
        val echoes = Symmetry.mirrorStrokes(s, SymmetryMode.RADIAL, 6, 100.0, 100.0)
        assertEquals(5, echoes.size)
        // Every echo sits on the same circle about the centre as the original's first sample.
        val r = hypot(60.0 - 100.0, 50.0 - 100.0)
        for (e in echoes) {
            val p = e.firstSample!!
            assertEquals(r, hypot(p.x - 100.0, p.y - 100.0), 1e-6)
        }
    }

    @Test fun noneModeProducesNoEchoes() {
        val s = stroke(10.0 to 10.0)
        assertTrue(Symmetry.mirrorStrokes(s, SymmetryMode.NONE, 6, 100.0, 100.0).isEmpty())
    }

    @Test fun modeParsesAndFallsBack() {
        assertEquals(SymmetryMode.QUAD, SymmetryMode.fromId("quad"))
        assertEquals(SymmetryMode.NONE, SymmetryMode.fromId("bogus"))
        assertEquals(SymmetryMode.NONE, SymmetryMode.fromId(null))
        assertTrue(SymmetryMode.ids.containsAll(listOf("none", "vertical", "horizontal", "quad", "radial")))
    }

    @Test fun echoesKeepTheOriginalStyle() {
        val s = stroke(10.0 to 10.0, 12.0 to 14.0)
        for (e in Symmetry.mirrorStrokes(s, SymmetryMode.QUAD, 6, 100.0, 100.0)) {
            assertEquals(s.tool, e.tool)
            assertEquals(s.config.baseWidth, e.config.baseWidth, 1e-12)
            assertEquals(s.config.rgba, e.config.rgba)
        }
    }

    private fun hypot(x: Double, y: Double) = kotlin.math.hypot(x, y)
}
