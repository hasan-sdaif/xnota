package com.xnota.core.text

import com.xnota.core.text.LineStyle.BULLET
import com.xnota.core.text.LineStyle.CHECKED
import com.xnota.core.text.LineStyle.CODE
import com.xnota.core.text.LineStyle.HEADING
import com.xnota.core.text.LineStyle.ORDERED
import com.xnota.core.text.LineStyle.PARAGRAPH
import com.xnota.core.text.LineStyle.QUOTE
import com.xnota.core.text.LineStyle.UNCHECKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteExportersTest {

    private val sample = listOf(
        ExportLine(HEADING, "Chapter", level = 2),
        ExportLine(PARAGRAPH, "First para."),
        ExportLine(BULLET, "apple"),
        ExportLine(BULLET, "banana", indent = 1),
        ExportLine(ORDERED, "wash"),
        ExportLine(ORDERED, "peel"),
        ExportLine(CHECKED, "buy milk"),
        ExportLine(UNCHECKED, "buy bread"),
        ExportLine(QUOTE, "A wise man once said"),
        ExportLine(CODE, "val x = 1"),
        ExportLine(CODE, "println(x)"),
    )

    @Test fun markdownCarriesTitleAndHeading() {
        val md = NoteExporters.markdown("Groceries", sample)
        assertTrue(md.startsWith("# Groceries\n\n"))
        assertTrue(md.contains("## Chapter\n\n"))
        assertTrue(md.contains("First para.\n\n"))
    }

    @Test fun markdownKeepsCheckStates() {
        val md = NoteExporters.markdown("T", sample)
        assertTrue(md.contains("- [x] buy milk\n"))
        assertTrue(md.contains("- [ ] buy bread\n"))
    }

    @Test fun markdownNumbersOrderedRuns() {
        val md = NoteExporters.markdown("T", sample)
        assertTrue(md.contains("1. wash\n"))
        assertTrue(md.contains("2. peel\n"))
    }

    @Test fun markdownNestsIndentedBullets() {
        val md = NoteExporters.markdown("T", sample)
        assertTrue(md.contains("- apple\n  - banana\n"))
    }

    @Test fun markdownFencesCodeRuns() {
        val md = NoteExporters.markdown("T", sample)
        assertTrue(md.contains("```\nval x = 1\nprintln(x)\n```"))
    }

    @Test fun markdownQuotes() {
        assertTrue(NoteExporters.markdown("T", sample).contains("> A wise man once said\n"))
    }

    @Test fun markdownSeparatesRunsFromEachOther() {
        // bullet run then ordered run: a blank line between the two lists
        val md = NoteExporters.markdown("T", sample)
        assertTrue(md.contains("- apple\n  - banana\n\n1. wash\n"))
        assertTrue(md.contains("2. peel\n\n- [x] buy milk\n"))
    }

    @Test fun plainTextUsesSimpleMarkers() {
        val txt = NoteExporters.plainText("Groceries", sample)
        assertTrue(txt.startsWith("Groceries\n\n"))
        assertTrue(txt.contains("Chapter\n\n"))
        assertTrue(txt.contains("• apple\n"))
        assertTrue(txt.contains("    • banana\n"))
        assertTrue(txt.contains("1. wash\n"))
        assertTrue(txt.contains("[x] buy milk\n"))
        assertTrue(txt.contains("[ ] buy bread\n"))
        assertTrue(txt.contains("> A wise man once said\n"))
        assertTrue(txt.contains("    val x = 1\n"))
        assertFalse(txt.contains("#"))
    }

    @Test fun htmlEscapesEntities() {
        val html = NoteExporters.html(
            "T & <more>",
            listOf(ExportLine(PARAGRAPH, "5 < 6 && 7 > \"3\" — 'ok'")),
        )
        assertTrue(html.contains("<title>T &amp; &lt;more&gt;</title>"))
        assertTrue(html.contains("<p>5 &lt; 6 &amp;&amp; 7 &gt; &quot;3&quot; — &#39;ok&#39;</p>"))
        assertFalse(html.contains("5 < 6")) // the raw angle bracket never lands as markup
    }

    @Test fun htmlEmitsHeadingsAndLists() {
        val html = NoteExporters.html("T", sample)
        assertTrue(html.startsWith("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>T</title>"))
        assertTrue(html.contains("<h1>T</h1>"))
        assertTrue(html.contains("<h2>Chapter</h2>"))
        assertTrue(html.contains("<ul>\n<li>apple\n<ul>\n<li>banana\n</li>\n</ul>\n</li>\n<li>wash\n</li>\n<li>peel\n"))
        assertTrue(html.contains("<li><input type=\"checkbox\" checked disabled> buy milk"))
        assertTrue(html.contains("<li><input type=\"checkbox\" disabled> buy bread"))
        assertTrue(html.contains("<blockquote>\nA wise man once said<br>\n</blockquote>"))
        assertTrue(html.contains("<pre><code>val x = 1\nprintln(x)\n</code></pre>"))
        assertTrue(html.trimEnd().endsWith("</body>\n</html>"))
    }

    @Test fun htmlClosesListsBeforeBlocks() {
        val html = NoteExporters.html("T", listOf(ExportLine(BULLET, "a"), ExportLine(PARAGRAPH, "after")))
        assertTrue(html.contains("<ul>\n<li>a\n</li>\n</ul>\n<p>after</p>"))
    }

    @Test fun anEmptyNoteStillYieldsWellFormedOutput() {
        assertEquals("# T\n", NoteExporters.markdown("T", emptyList()))
        assertEquals("T\n", NoteExporters.plainText("T", emptyList()))
        val html = NoteExporters.html("T", emptyList())
        assertTrue(html.contains("<h1>T</h1>"))
        assertTrue(html.contains("</body>\n</html>"))
        assertEquals("", NoteExporters.markdown("", emptyList()).trimEnd('\n'))
    }
}
