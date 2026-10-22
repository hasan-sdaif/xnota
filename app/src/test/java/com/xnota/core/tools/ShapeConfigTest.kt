package com.xnota.core.tools

import com.xnota.core.geometry.Flip
import com.xnota.core.geometry.Pt
import com.xnota.core.history.RefillShapes
import com.xnota.core.model.ConnectorEnd
import com.xnota.core.model.DrawStyle
import com.xnota.core.model.Rgba
import com.xnota.core.model.Routing
import com.xnota.core.model.ShapeItem
import com.xnota.core.shapes.ConnectorPresets
import com.xnota.core.shapes.Head
import com.xnota.core.shapes.ShapeKind
import com.xnota.core.shapes.ShapePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeConfigTest {

    private val ink = Rgba(10, 20, 30)
    private val teal = Rgba(0, 128, 128)

    @Test fun everyChipHasItsOwnKeyAndAPage() {
        assertEquals(ShapePresets.all.size, ShapePresets.all.map { it.key }.toSet().size)
        assertTrue(ShapePresets.pages.all { it.presets.size <= ShapePresets.ROWS * ShapePresets.COLUMNS })
        assertSame(ShapePresets.byKey("circle"), ShapePresets.forKind(ShapeKind.CIRCLE))
        assertEquals("rectangle", ShapePresets.forKind(ShapeKind.CURVE).key)
    }

    /** The flip a drag from (100, 100) to [to] gives a shape of chip [key]. */
    private fun dragged(key: String, to: Pt): Flip {
        val c = ShapeConfig().choose(ShapePresets.byKey(key)!!)
        val s = c.startShape(Pt(100.0, 100.0), ink, 2.0)
        s.end = to
        c.followDrag(s)
        return s.flip
    }

    @Test fun aDragDownAndRightDrawsTheChip() {
        for (p in ShapePresets.all) assertEquals(p.key, Flip.NONE, dragged(p.key, Pt(180.0, 150.0)))
    }

    @Test fun aDragLeftOrUpTurnsWhatFacesAWay() {
        assertEquals("[ dragged left is ]", Flip.X, dragged("math_square_bracket", Pt(40.0, 160.0)))
        assertEquals("a bracket has no up or down", Flip.X, dragged("math_square_bracket", Pt(40.0, 20.0)))
        assertEquals(Flip.Y, dragged("arrow_up", Pt(160.0, 20.0)))
        assertEquals(Flip.X, dragged("arrow_left", Pt(20.0, 160.0)))
        assertEquals(Flip.XY, dragged("right_triangle", Pt(20.0, 20.0)))
        assertEquals(Flip.XY, dragged("callout_rect", Pt(20.0, 20.0)))
        assertEquals("a triangle turns over", Flip.Y, dragged("triangle", Pt(20.0, 20.0)))
        assertEquals("a parallelogram only leans the other way", Flip.X, dragged("parallelogram", Pt(20.0, 20.0)))
        assertEquals("a cube mirrors but stays seen from above", Flip.X, dragged("solid_cube", Pt(20.0, 20.0)))
    }

    @Test fun uprightShapesStayUpright() {
        for (key in listOf("heart", "star", "pentagon", "cloud", "burst", "flow_extract", "flow_merge", "flow_document", "solid_cone", "math_axes_2d", "rectangle", "line", "arrow")) {
            assertEquals(key, Flip.NONE, dragged(key, Pt(160.0, 20.0)))
        }
        assertEquals(Flip.NONE, dragged("math_axes_2d", Pt(20.0, 20.0)))
        assertEquals("a line follows the pen by its own ends", Flip.NONE, dragged("arrow", Pt(20.0, 20.0)))
    }

    @Test fun choosingAChipTakesItsHeadsAndCount() {
        val c = ShapeConfig(n = 7, headStart = Head.FILLED).choose(ShapePresets.byKey("arrow")!!)
        assertEquals("arrow", c.preset)
        assertSame(ShapeKind.ARROW, c.shape)
        assertEquals(Head.NONE, c.headStart)
        assertEquals(Head.OPEN, c.headEnd)
        assertEquals(0, c.n)
    }

    @Test fun anUnknownChipFallsBackToTheRectangle() {
        assertSame(ShapeKind.RECTANGLE, ShapeConfig(preset = "hexagram").shape)
    }

    @Test fun theFillTakesItsOwnColourAtItsOpacity() {
        val c = ShapeConfig(fill = true, fillAlpha = 0.5, fillColor = teal)
        assertEquals(Rgba(0, 128, 128, 127), c.fillFor(ShapeKind.RECTANGLE, ink))
        assertEquals(Rgba(10, 20, 30, 127), c.copy(fillColor = null).fillFor(ShapeKind.RECTANGLE, ink))
        assertNull(c.fillFor(ShapeKind.LINE, ink))
    }

    @Test fun aStartedShapeCarriesTheChipsSettings() {
        val arrow = ShapeConfig().choose(ShapePresets.byKey("arrow")!!).copy(headStart = Head.HOLLOW_DIAMOND)
            .startShape(Pt(1.0, 2.0), ink, 3.0)
        assertSame(ShapeKind.ARROW, arrow.shape)
        assertEquals(Head.HOLLOW_DIAMOND, arrow.headStart)
        assertEquals(Pt(1.0, 2.0), arrow.end)
        val tri = ShapeConfig(preset = "triangle", headEnd = Head.OPEN, fill = true).startShape(Pt.ZERO, ink, 3.0)
        assertEquals("a closed shape never carries heads", Head.NONE, tri.headEnd)
        assertEquals(0.5, tri.adj[0], 0.0)
        assertTrue(tri.fillRgba != null)
    }

    @Test fun recolouringTheOutlineLeavesAFillOfItsOwnAlone() {
        val matching = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, ink.copy(a = 64))
        val own = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, teal.copy(a = 64))
        val red = Rgba(200, 0, 0)
        DrawStyle(red, 2.0).applyTo(matching)
        DrawStyle(red, 2.0).applyTo(own)
        assertEquals(red.copy(a = 64), matching.fillRgba)
        assertEquals(teal.copy(a = 64), own.fillRgba)
    }

    @Test fun refillingKeepsEachFillsOpacityAndUndoes() {
        val a = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, ink.copy(a = 64))
        val b = ShapeItem(ShapeKind.ELLIPSE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0, teal.copy(a = 200))
        val bare = ShapeItem(ShapeKind.RECTANGLE, Pt.ZERO, Pt(1.0, 1.0), ink, 2.0)
        val entries = RefillShapes.of(listOf(a, b, bare), teal)
        assertEquals("only fills that change", 1, entries.size)
        val cmd = RefillShapes(entries)
        cmd.redo()
        assertEquals(teal.copy(a = 64), a.fillRgba)
        assertNull(bare.fillRgba)
        cmd.undo()
        assertEquals(ink.copy(a = 64), a.fillRgba)
        val matched = RefillShapes.of(listOf(b), null).single()
        assertEquals(ink.copy(a = 200), matched.after)
    }

    @Test fun theConnectorChipsAreTheAgreedTwoPages() {
        assertEquals(listOf(12, 12), ConnectorPresets.pages.map { it.presets.size })
        assertEquals(ConnectorPresets.all.size, ConnectorPresets.all.map { it.key }.toSet().size)
        assertTrue(ConnectorPresets.all.none { ShapePresets.byKey(it.key) != null })
        val uml = ConnectorPresets.umlPage.presets
        assertEquals(Head.HOLLOW_TRIANGLE, uml[1].headEnd)
        assertEquals(true, uml[2].dashed)
        assertEquals(Head.FILLED_DIAMOND, uml[5].headStart)
        assertEquals(Routing.ELBOW, uml[6].routing)
    }

    @Test fun choosingAConnectorTakesItsHeadsAndADashOnlyWhenItHasOne() {
        val realization = ShapeConfig().choose(ConnectorPresets.byKey("uml_realization")!!)
        assertEquals(Head.HOLLOW_TRIANGLE, realization.headEnd)
        assertTrue(realization.dashed)
        assertEquals(Routing.STRAIGHT, realization.connector!!.routing)
        val plain = realization.choose(ConnectorPresets.byKey("conn_elbow_arrow")!!)
        assertTrue("a plain chip leaves the dash alone", plain.dashed)
        assertEquals(Head.OPEN, plain.headEnd)
        assertNull(plain.choose(ShapePresets.byKey("arrow")!!).connector)
    }

    @Test fun aStartedConnectorCarriesTheConfigsStyle() {
        val cfg = ShapeConfig(neon = true).choose(ConnectorPresets.byKey("conn_curved_both")!!)
        val c = cfg.startConnector(ConnectorEnd(Pt(5.0, 6.0)), ink, 2.5)!!
        assertEquals(Routing.CURVED, c.routing)
        assertEquals(Head.OPEN, c.headStart)
        assertEquals(Head.OPEN, c.headEnd)
        assertEquals(Pt(5.0, 6.0), c.end.at)
        assertTrue(c.neon)
        assertEquals(2.5, c.strokeWidth, 0.0)
        assertNull(ShapeConfig().startConnector(ConnectorEnd(Pt.ZERO), ink, 2.0))
    }
}

