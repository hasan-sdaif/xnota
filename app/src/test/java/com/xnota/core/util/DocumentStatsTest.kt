package com.xnota.core.util

import com.xnota.core.model.Document
import com.xnota.core.model.ImageData
import com.xnota.core.model.ImageItem
import com.xnota.core.model.Page
import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import com.xnota.core.model.Rgba
import com.xnota.core.model.ShapeItem

import com.xnota.core.model.Stroke
import com.xnota.core.model.TextItem
import com.xnota.core.shapes.ShapeKind
import com.xnota.core.stroke.Sample
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolDefaults
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentStatsTest {

    private fun imageFile(): File = File.createTempFile("img", null).apply { writeBytes(byteArrayOf(1, 2)); deleteOnExit() }

    private fun stroke(vararg pts: Pair<Double, Double>): Stroke =
        Stroke(Tool.PEN, ToolDefaults.configFor(Tool.PEN), pts.map { Sample(it.first, it.second, 1.0) })

    @Test fun countsPagesAndItemsByKind() {
        val doc = Document(dpi = 150)
        doc.pages.add(Page(100.0, 100.0))
        doc.pages.add(Page(100.0, 100.0))
        doc.pages[0].items.add(stroke(0.0 to 0.0, 3.0 to 4.0))
        doc.pages[0].items.add(
            ShapeItem(ShapeKind.RECTANGLE, Pt(0.0, 0.0), Pt(9.0, 9.0), Rgba(1, 2, 3, 255)),
        )
        doc.pages[1].items.add(TextItem(Pt(1.0, 1.0), 100.0, 0.0, "hi", Rgba(0, 0, 0), 12.0, measurer = com.xnota.core.FakeTextMeasurer()))
        doc.pages[1].items.add(ImageItem(ImageData(imageFile(), 20, 10), Rect(0.0, 0.0, 20.0, 10.0)))

        val stats = DocumentStats.of(doc)
        assertEquals(2, stats.pages)
        assertEquals(1, stats.strokes)
        assertEquals(1, stats.shapes)
        assertEquals(1, stats.texts)
        assertEquals(1, stats.images)
        assertEquals(4, stats.items)
    }

    @Test fun inkLengthSumsEveryStrokeSegmentInDocumentPx() {
        val doc = Document(dpi = 150)
        doc.pages.add(Page(100.0, 100.0))
        // two legs of a 3-4-5 triangle each: 5 + 5
        doc.pages[0].items.add(stroke(0.0 to 0.0, 3.0 to 4.0, 6.0 to 8.0))
        // a lone sample contributes nothing
        doc.pages[0].items.add(stroke(9.0 to 9.0))
        assertEquals(10.0, DocumentStats.of(doc).inkLength, 1e-9)
    }

    @Test fun emptyDocumentCountsZeroes() {
        val stats = DocumentStats.of(Document())
        assertEquals(0, stats.pages)
        assertEquals(0, stats.items)
        assertEquals(0.0, stats.inkLength, 1e-9)
    }
}
