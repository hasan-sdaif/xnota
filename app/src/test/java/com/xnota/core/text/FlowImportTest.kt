package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowImportTest {

    @Test fun plainLinesBecomeOneParagraphEach() {
        val paras = FlowImport.plainParagraphs("Pasta\nand cheese")
        assertEquals(2, paras.size)
        assertEquals("Pasta", paras[0].plainText())
        assertEquals("and cheese", paras[1].plainText())
        assertTrue(paras.all { it.isDefaultStyle() }) // plain text carries no paragraph styling
    }

    @Test fun blankLinesBecomeEmptyParagraphs() {
        val paras = FlowImport.plainParagraphs("a\n\nb")
        assertEquals(3, paras.size)
        assertEquals("", paras[1].plainText())
        assertEquals(0, paras[1].length)
    }

    @Test fun plainTextRoundTripsThroughTheFlow() {
        val text = "first\n\nsecond\nthird"
        val flow = TextFlow().also { it.paragraphs.addAll(FlowImport.plainParagraphs(text)) }
        assertEquals(text, flow.plainText())
    }

    @Test fun carriageReturnsAreNormalizedAway() {
        val paras = FlowImport.plainParagraphs("a\r\nb\rc")
        assertEquals(listOf("a", "b", "c"), paras.map { it.plainText() })
    }

    @Test fun emptyTextIsASingleEmptyParagraph() {
        val paras = FlowImport.plainParagraphs("")
        assertEquals(1, paras.size)
        assertEquals(0, paras[0].length)
    }

    @Test fun markdownBodiesKeepTheirStructure() {
        // The blank separator lines are paragraphs of their own, as they are when typed.
        val paras = FlowImport.markdownParagraphs("# Agenda\n\n- coffee\n\n- [ ] tea")
        assertEquals(5, paras.size)
        assertEquals(1, paras[0].headingLevel)
        assertEquals(ListKind.BULLET, paras[2].list)
        assertEquals(ListKind.CHECK, paras[4].list)
        assertTrue(!paras[4].checked)
    }

    @Test fun markdownParsingMatchesTheReaderThePasteActionUses() {
        // Paragraphs compare by identity, so the shapes are compared instead.
        val body = "# T\n\ntext"
        val shape = { p: Paragraph -> Triple(p.plainText(), p.headingLevel, p.list) }
        assertEquals(
            MarkdownParser.parse(body).map(shape),
            FlowImport.markdownParagraphs(body).map(shape),
        )
    }
}
