package com.xnota.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.6.1 icon additions parse into real path data and land on the 24×24 canvas. The set's own
 * history is the reason this exists: a relative moveto in any path after the first starts from the
 * previous subpath's current point instead of the origin and draws the glyph off-canvas — invisible
 * in code review, obvious the moment the icon renders. Every new glyph is walked with a
 * current-point path simulator (the same walk a renderer does) and swept for coordinates outside
 * the viewport, so a bad path body fails here instead of shipping.
 */
class NotaIconsGeometryTest {

    private fun nodesOf(v: ImageVector): List<PathNode> {
        val out = ArrayList<PathNode>()
        fun walk(node: VectorNode) {
            when (node) {
                is VectorGroup -> node.forEach(::walk) // the group is Iterable over its children
                is VectorPath -> out += node.pathData
            }
        }
        walk(v.root)
        return out
    }

    /**
     * [minX, minY, maxX, maxY] over the walked path. Control points are swept too (a stray
     * control point still betrays a bad path body); arc endpoints ride ArcTo's arcStart pair,
     * which despite the name is where the arc lands.
     */
    private fun boundsOf(nodes: List<PathNode>): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var cx = 0f
        var cy = 0f
        var subX = 0f
        var subY = 0f
        fun sweep(x: Float, y: Float) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        fun moveTo(x: Float, y: Float) {
            cx = x; cy = y; subX = x; subY = y; sweep(x, y)
        }
        fun lineTo(x: Float, y: Float) {
            cx = x; cy = y; sweep(x, y)
        }
        for (n in nodes) when (n) {
            is PathNode.MoveTo -> moveTo(n.x, n.y)
            is PathNode.RelativeMoveTo -> moveTo(cx + n.dx, cy + n.dy)
            is PathNode.LineTo -> lineTo(n.x, n.y)
            is PathNode.RelativeLineTo -> lineTo(cx + n.dx, cy + n.dy)
            is PathNode.HorizontalTo -> lineTo(n.x, cy)
            is PathNode.RelativeHorizontalTo -> lineTo(cx + n.dx, cy)
            is PathNode.VerticalTo -> lineTo(cx, n.y)
            is PathNode.RelativeVerticalTo -> lineTo(cx, cy + n.dy)
            is PathNode.CurveTo -> {
                sweep(n.x1, n.y1); sweep(n.x2, n.y2); lineTo(n.x3, n.y3)
            }
            is PathNode.RelativeCurveTo -> {
                sweep(cx + n.dx1, cy + n.dy1); sweep(cx + n.dx2, cy + n.dy2); lineTo(cx + n.dx3, cy + n.dy3)
            }
            is PathNode.ReflectiveCurveTo -> {
                sweep(n.x1, n.y1); lineTo(n.x2, n.y2)
            }
            is PathNode.RelativeReflectiveCurveTo -> {
                sweep(cx + n.dx1, cy + n.dy1); lineTo(cx + n.dx2, cy + n.dy2)
            }
            is PathNode.QuadTo -> {
                sweep(n.x1, n.y1); lineTo(n.x2, n.y2)
            }
            is PathNode.RelativeQuadTo -> {
                sweep(cx + n.dx1, cy + n.dy1); lineTo(cx + n.dx2, cy + n.dy2)
            }
            is PathNode.ReflectiveQuadTo -> lineTo(n.x, n.y)
            is PathNode.RelativeReflectiveQuadTo -> lineTo(cx + n.dx, cy + n.dy)
            is PathNode.ArcTo -> lineTo(n.arcStartX, n.arcStartY)
            is PathNode.RelativeArcTo -> lineTo(cx + n.arcStartDx, cy + n.arcStartDy)
            PathNode.Close -> lineTo(subX, subY)
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun assertOnCanvas(name: String, v: ImageVector, minNodes: Int) {
        val nodes = nodesOf(v)
        assertTrue("$name parsed to no path nodes", nodes.isNotEmpty())
        assertTrue("$name has only ${nodes.size} nodes", nodes.size >= minNodes)
        val b = boundsOf(nodes)
        assertTrue(
            "$name lands off-canvas: x ${b[0]}..${b[2]}, y ${b[1]}..${b[3]} (viewport 24x24)",
            b[0] >= -1f && b[1] >= -1f && b[2] <= 25f && b[3] <= 25f,
        )
    }

    @Test fun themeIconsParseOntoTheCanvas() {
        assertOnCanvas("sun", NotaIcons.sun, 10)
        assertOnCanvas("moon", NotaIcons.moon, 4)
        assertOnCanvas("monitor", NotaIcons.monitor, 6)
    }

    @Test fun settingsHubIconsParseOntoTheCanvas() {
        assertOnCanvas("globe", NotaIcons.globe, 8)
        assertOnCanvas("corners", NotaIcons.corners, 8)
        assertOnCanvas("hand", NotaIcons.hand, 12)
        assertOnCanvas("keyboard", NotaIcons.keyboard, 14)
        assertOnCanvas("zap", NotaIcons.zap, 8)
        assertOnCanvas("filePlus", NotaIcons.filePlus, 8)
        assertOnCanvas("bookOpen", NotaIcons.bookOpen, 10)
        assertOnCanvas("target", NotaIcons.target, 9)
        assertOnCanvas("compass", NotaIcons.compass, 8)
        assertOnCanvas("gauge", NotaIcons.gauge, 4)
        assertOnCanvas("code", NotaIcons.code, 4)
        assertOnCanvas("archive", NotaIcons.archive, 8)
        assertOnCanvas("penTool", NotaIcons.penTool, 10)
        assertOnCanvas("shield", NotaIcons.shield, 8)
        assertOnCanvas("gear", NotaIcons.gear, 40)
    }

    @Test fun everyIconCarriesTheSetViewport() {
        val all = listOf(
            NotaIcons.sun, NotaIcons.moon, NotaIcons.monitor, NotaIcons.globe,
            NotaIcons.corners, NotaIcons.hand, NotaIcons.keyboard, NotaIcons.zap,
            NotaIcons.filePlus, NotaIcons.bookOpen, NotaIcons.target, NotaIcons.compass,
            NotaIcons.gauge, NotaIcons.code, NotaIcons.archive, NotaIcons.penTool,
            NotaIcons.shield, NotaIcons.gear,
        )
        for (v in all) {
            assertEquals(24f, v.viewportWidth)
            assertEquals(24f, v.viewportHeight)
            assertEquals(24f, v.defaultWidth.value)
            assertEquals(24f, v.defaultHeight.value)
        }
    }
}
