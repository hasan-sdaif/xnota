package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnexImportTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE en-export SYSTEM "http://xml.evernote.com/pub/evernote-export3.dtd">
        <en-export export-date="20260101T000000Z" application="Evernote" version="10">
        <note>
        <title><![CDATA[Recipe ideas]]></title>
        <created>20231005T091500Z</created>
        <updated>20231006T101500Z</updated>
        <tag><![CDATA[cooking]]></tag>
        <tag><![CDATA[todo]]></tag>
        <note-resources/>
        <content><![CDATA[<?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE en-note SYSTEM "http://xml.evernote.com/pub/enml2.dtd">
        <en-note><div>Pasta with &amp; without cheese</div><div><br/></div><div>Try a&nbsp;ruqaa font for the menu</div></en-note>]]></content>
        </note>
        <note>
        <title><![CDATA[Empty-ish]]></title>
        <content><![CDATA[<en-note><div>just one line</div></en-note>]]></content>
        </note>
        </en-export>
        """.trimIndent()

    @Test fun parsesTitlesStampsTagsAndText() {
        val notes = EnexImport.parse(sample)
        assertEquals(2, notes.size)
        val first = notes[0]
        assertEquals("Recipe ideas", first.title)
        assertEquals(listOf("cooking", "todo"), first.tags)
        // 2023-10-05 09:15 UTC in epoch ms
        assertEquals(1696497300000L, first.created)
        assertEquals(1696587300000L, first.updated)
        assertEquals("Pasta with & without cheese\n\nTry a ruqaa font for the menu", first.text)
    }

    @Test fun aNoteWithoutStampsOrTagsStillComesOut() {
        val second = EnexImport.parse(sample)[1]
        assertEquals("Empty-ish", second.title)
        assertEquals(0L, second.created)
        assertEquals(0L, second.updated)
        assertTrue(second.tags.isEmpty())
        assertEquals("just one line", second.text)
    }

    @Test fun theDtdIsNeverFetched() {
        // Two DOCTYPEs point at evernote.com; parsing stays local and succeeds.
        assertTrue(EnexImport.parse(sample).isNotEmpty())
    }

    @Test fun brokenXmlYieldsNothing() {
        assertTrue(EnexImport.parse("this is not xml <<<").isEmpty())
        assertTrue(EnexImport.parse("").isEmpty())
    }

    @Test fun stripEnmlDecodesEntitiesOnce() {
        val text = EnexImport.stripEnml("<en-note><div>&amp;lt; stays escaped &amp; becomes &amp;amp;</div></en-note>")
        assertEquals("&lt; stays escaped & becomes &amp;", text)
    }

    @Test fun stripEnmlCollapsesBlankRuns() {
        val text = EnexImport.stripEnml("<en-note><div>a</div><div><br/></div><div><br/></div><div><br/></div><div>b</div></en-note>")
        assertEquals("a\n\nb", text)
    }
}
