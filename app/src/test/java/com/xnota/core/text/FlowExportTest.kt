package com.xnota.core.text

import com.xnota.core.text.LineStyle.BULLET
import com.xnota.core.text.LineStyle.CHECKED
import com.xnota.core.text.LineStyle.CODE
import com.xnota.core.text.LineStyle.HEADING
import com.xnota.core.text.LineStyle.ORDERED
import com.xnota.core.text.LineStyle.PARAGRAPH
import com.xnota.core.text.LineStyle.UNCHECKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowExportTest {

    /** A paragraph of runs, styled by the named properties. */
    private fun para(text: String, style: Paragraph.() -> Unit = {}): Paragraph =
        Paragraph(mutableListOf(Run(text))).apply(style)

    @Test
    fun `a plain paragraph becomes a paragraph line of its runs' text`() {
        val p = Paragraph(mutableListOf(Run("hello "), Run("world")))
        assertEquals(listOf(ExportLine(PARAGRAPH, "hello world")), FlowExport.lines(listOf(p)))
    }

    @Test
    fun `heading levels carry with their text`() {
        val lines = FlowExport.lines(listOf(para("Big", { headingLevel = 1 }), para("Deeper", { headingLevel = 3 })))
        assertEquals(listOf(ExportLine(HEADING, "Big", level = 1), ExportLine(HEADING, "Deeper", level = 3)), lines)
    }

    @Test
    fun `list kinds map to their markers with indentation`() {
        val lines = FlowExport.lines(
            listOf(
                para("apple", { list = ListKind.BULLET }),
                para("banana", { list = ListKind.BULLET; indent = 2 }),
                para("wash", { list = ListKind.ORDERED }),
            ),
        )
        assertEquals(
            listOf(ExportLine(BULLET, "apple"), ExportLine(BULLET, "banana", indent = 2), ExportLine(ORDERED, "wash")),
            lines,
        )
    }

    @Test
    fun `check states split into checked and unchecked lines`() {
        val lines = FlowExport.lines(
            listOf(
                para("done", { list = ListKind.CHECK; checked = true }),
                para("todo", { list = ListKind.CHECK; checked = false }),
                para("nested", { list = ListKind.CHECK; checked = true; indent = 1 }),
            ),
        )
        assertEquals(
            listOf(ExportLine(CHECKED, "done"), ExportLine(UNCHECKED, "todo"), ExportLine(CHECKED, "nested", indent = 1)),
            lines,
        )
    }

    @Test
    fun `a code line is a code line, plain or languaged`() {
        val lines = FlowExport.lines(
            listOf(
                para("val x = 1", { codeLang = "" }),
                para("println(x)", { codeLang = "kotlin" }),
            ),
        )
        assertEquals(listOf(ExportLine(CODE, "val x = 1"), ExportLine(CODE, "println(x)")), lines)
    }

    @Test
    fun `a checked non-list paragraph is still just a paragraph`() {
        // checked only means something on a CHECK list; the mapping must not be fooled by leftovers.
        val lines = FlowExport.lines(listOf(para("odd", { checked = true })))
        assertEquals(listOf(ExportLine(PARAGRAPH, "odd")), lines)
    }

    @Test
    fun `a code line wins over its list kind, as the editor shows it`() {
        val lines = FlowExport.lines(listOf(para("x = 1", { codeLang = ""; list = ListKind.BULLET })))
        assertEquals(listOf(ExportLine(CODE, "x = 1")), lines)
    }

    @Test
    fun `the mapped flow exports through the pure exporters end to end`() {
        val flow = listOf(
            para("Chapter", { headingLevel = 2 }),
            para("First para."),
            para("buy milk", { list = ListKind.CHECK; checked = true }),
        )
        val md = NoteExporters.export(NoteExporters.Format.MARKDOWN, "T", FlowExport.lines(flow))
        assertTrue(md.startsWith("# T\n\n"))
        assertTrue(md.contains("## Chapter\n\n"))
        assertTrue(md.contains("First para.\n\n"))
        assertTrue(md.contains("- [x] buy milk\n"))
    }

    @Test
    fun `an empty flow maps to no lines at all`() {
        assertEquals(emptyList<ExportLine>(), FlowExport.lines(emptyList()))
    }
}
