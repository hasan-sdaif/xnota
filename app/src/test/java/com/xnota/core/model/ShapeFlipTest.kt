package com.xnota.core.model

import com.xnota.core.FakeTextMeasurer
import com.xnota.core.geometry.Affine
import com.xnota.core.geometry.Flip
import com.xnota.core.geometry.Pt
import com.xnota.core.shapes.ArrowKinds
import com.xnota.core.shapes.FlowchartKinds
import com.xnota.core.shapes.ShapeKind
import com.xnota.core.shapes.SolidMathKinds
import com.xnota.core.vector.VectorSeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class ShapeFlipTest {

    private val ink = Rgba(1, 2, 3, 255)

    private fun shape(kind: ShapeKind) = ShapeItem(kind, Pt(10.0, 20.0), Pt(130.0, 100.0), ink, 2.0)

    /** Every point a shape's paths name: contour starts, segment ends and curve handles. */
    private fun points(s: ShapeItem): List<Pt> = s.paths().flatMap { p ->
        p.contours.flatMap { c ->
            listOf(c.start) + c.segments.flatMap { seg ->
                when (seg) {
                    is VectorSeg.Line -> listOf(seg.end)
                    is VectorSeg.Cubic -> listOf(seg.c1, seg.c2, seg.end)
                }
            }
        }
    }

    private fun assertSamePoints(want: List<Pt>, got: List<Pt>, what: String) {
        assertEquals("$what count", want.size, got.size)
        for (w in want) assertTrue("$what missing $w", got.any { it.distanceTo(w) < 1e-6 })
    }

    private fun mirrors(s: ShapeItem) = listOf(
        Affine.scaleAbout(s.box.center, -1.0, 1.0),
        Affine.scaleAbout(Pt(400.0, -70.0), 1.0, -1.0),
    )

    @Test fun everyBoxKindDrawsItsMirrorImage() {
        for (k in ShapeKind.all.filter { !it.isEndpointShape && !it.isFree }) {
            val plain = shape(k)
            for (f in listOf(Flip.X, Flip.Y, Flip.XY)) {
                val flipped = shape(k).also { it.flip = f }
                assertSamePoints(points(plain).map { f.map(it, plain.box) }, points(flipped), "$k $f")
            }
        }
    }

    @Test fun knobsSitAndDragInTheMirroredDrawing() {
        for (k in ShapeKind.all.filter { it.knobs.isNotEmpty() }) {
            val plain = shape(k)
            val flipped = shape(k).also { it.flip = Flip.XY }
            assertSamePoints(plain.knobDots().map { Flip.XY.map(it, plain.box) }, flipped.knobDots(), "$k knobs")
            val to = Pt(40.0, 50.0)
            plain.dragKnob(0, to)
            flipped.dragKnob(0, Flip.XY.map(to, flipped.box))
            for (i in plain.adj.indices) assertEquals("$k knob $i", plain.adj[i], flipped.adj[i], 1e-9)
            assertEquals("$k box", plain.box.x, flipped.box.x, 1e-9)
            assertEquals("$k box", plain.box.h, flipped.box.h, 1e-9)
        }
    }

    @Test fun gluePointsAndTheirWaysOutMirror() {
        val plain = shape(ShapeKind.RIGHT_TRIANGLE)
        val flipped = shape(ShapeKind.RIGHT_TRIANGLE).also { it.flip = Flip.X }
        val a = plain.connectionPoints()
        val b = flipped.connectionPoints()
        for (i in a.indices) {
            assertTrue(Flip.X.map(a[i].at, plain.box).distanceTo(b[i].at) < 1e-9)
            for (j in a[i].normals.indices) assertTrue(Flip.X.direction(a[i].normals[j]).distanceTo(b[i].normals[j]) < 1e-9)
        }
    }

    @Test fun aMirrorKeepsTheKindFlipsItAndLeavesItNearUpright() {
        for (k in listOf(ShapeKind.RIGHT_TRIANGLE, FlowchartKinds.INTERNAL_STORAGE, SolidMathKinds.CUBE, ArrowKinds.BENT, ArrowKinds.RECT_CALLOUT)) {
            for (turn in listOf(0.0, 0.4, 2.0, -2.5)) {
                for (i in 0..1) {
                    val s = shape(k).apply { applyTransform(Affine.rotateAbout(box.center, turn)) }
                    val before = points(s)
                    val t = mirrors(s)[i]
                    s.applyTransform(t)
                    assertEquals(k, s.shape)
                    assertNotEquals("$k turned $turn", Flip.NONE, s.flip)
                    assertSamePoints(before.map(t::apply), points(s), "$k turned $turn, mirror $i")
                    assertTrue("$k turned $turn, mirror $i: ${s.rotation}", abs(s.rotation) <= PI / 2.0 + 1e-9)
                }
            }
        }
    }

    @Test fun aSymmetricKindMirrorsWithoutAFlip() {
        val rect = shape(ShapeKind.RECTANGLE)
        rect.applyTransform(mirrors(rect)[0])
        assertEquals(Flip.NONE, rect.flip)
        assertNull("still what the oldest readers draw", rect.legacyOutline())
        val star = shape(ShapeKind.STAR)
        star.applyTransform(mirrors(star)[0])
        assertEquals("a star is symmetric left to right", Flip.NONE, star.flip)
        star.applyTransform(mirrors(star)[1])
        assertEquals(Flip.Y, star.flip)
    }

    @Test fun mirroringTwiceComesBack() {
        val s = shape(ArrowKinds.CURVED_RIGHT).apply { applyTransform(Affine.rotateAbout(box.center, 0.3)) }
        val before = points(s)
        val t = mirrors(s)[1]
        s.applyTransform(t)
        s.applyTransform(t)
        assertEquals(Flip.NONE, s.flip)
        assertEquals(0.3, s.rotation, 1e-9)
        assertSamePoints(before, points(s), "twice")
    }

    @Test fun undoBringsTheFlipBack() {
        val s = shape(SolidMathKinds.CUBE)
        val snap = s.snapshotGeometry()
        s.applyTransform(mirrors(s)[0])
        assertEquals(Flip.X, s.flip)
        assertNotEquals(snap, s.snapshotGeometry())
        s.restoreGeometry(snap)
        assertEquals(Flip.NONE, s.flip)
        assertEquals(snap, s.snapshotGeometry())
    }

    @Test fun aFlippedShapeGoesFreeAsDrawn() {
        val s = shape(ShapeKind.RIGHT_TRIANGLE).also { it.flip = Flip.X }
        val before = s.vertexDots().map { it.at }
        assertTrue(s.toFree())
        assertEquals(ShapeKind.POLYGON, s.shape)
        assertEquals(Flip.NONE, s.flip)
        assertSamePoints(before, s.vertices()!!, "free")
    }

    @Test fun aFlippedTriangleWritesItsMirrorAsTheStandIn() {
        val s = shape(ShapeKind.TRIANGLE)
        assertNull(s.legacyOutline())
        s.flip = Flip.Y
        val standIn = s.legacyOutline()
        assertNotNull(standIn)
        standIn!!
        assertEquals("polygon", s.legacyId())
        assertSamePoints(s.vertexDots().map { it.at }, standIn, "stand-in")
        assertEquals("the apex is at the bottom now", s.box.bottom, standIn.maxOf { it.y }, 1e-9)
    }

    @Test fun aMirroredOutlineKeepsItsLabelUpright() {
        val s = ShapeItem.poly(ShapeKind.POLYGON, listOf(Pt(0.0, 0.0), Pt(80.0, 10.0), Pt(60.0, 70.0)), ink)
        s.label = ShapeLabel("hi", TextStyle(ink, 12.0, TextItem.DEFAULT_FACE), FakeTextMeasurer())
        val before = s.vertices()!!
        val t = Affine.scaleAbout(Pt(40.0, 35.0), -1.0, 1.0)
        s.applyTransform(t)
        assertEquals(0.0, s.rotation, 1e-12)
        assertSamePoints(before.map(t::apply), s.vertices()!!, "outline")
    }

    @Test fun aLabelledShapeStaysReadableMirrored() {
        val s = shape(ShapeKind.RIGHT_TRIANGLE)
        s.label = ShapeLabel("hi", TextStyle(ink, 12.0, TextItem.DEFAULT_FACE), FakeTextMeasurer())
        val r = s.labelRect()!!
        s.applyTransform(mirrors(s)[0])
        assertEquals(0.0, s.rotation, 0.0)
        val m = s.labelRect()!!
        assertEquals("the words move to the mirrored side", s.box.left + s.box.right - r.right, m.left, 1e-9)
        assertEquals(r.w, m.w, 1e-9)
    }
}
