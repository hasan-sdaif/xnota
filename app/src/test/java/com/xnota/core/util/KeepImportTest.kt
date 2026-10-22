package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepImportTest {

    private val json = """
        [
          {
            "title": "Shopping",
            "text": "milk\nbread",
            "pinned": true,
            "labels": [{"name": "errands"}, {"name": "weekend"}],
            "createdTimestampUsec": 1696497300000000,
            "updatedTimestampUsec": 1696583700000000
          },
          {
            "title": "",
            "text": "a quick thought",
            "labels": ["ideas"]
          },
          {"title": "no text at all"}
        ]
    """.trimIndent()

    @Test fun parsesTheJsonExport() {
        val notes = KeepImport.parseJson(json)
        assertEquals(3, notes.size)
        val first = notes[0]
        assertEquals("Shopping", first.title)
        assertEquals("milk\nbread", first.text)
        assertEquals(listOf("errands", "weekend"), first.labels)
        assertTrue(first.pinned)
        assertEquals(1696497300000L, first.created) // usec -> ms
        assertEquals(1696583700000L, first.updated)
    }

    @Test fun notesWithoutEverythingStillComeOut() {
        val notes = KeepImport.parseJson(json)
        assertEquals("", notes[1].title)
        assertEquals("a quick thought", notes[1].text)
        assertEquals(listOf("ideas"), notes[1].labels)
        assertEquals(0L, notes[1].created)
        assertEquals("no text at all", notes[2].title)
        assertEquals("", notes[2].text)
        assertTrue(!notes[2].pinned)
    }

    @Test fun unreadableJsonYieldsNothing() {
        assertTrue(KeepImport.parseJson("{nope").isEmpty())
        assertTrue(KeepImport.parseJson("").isEmpty())
    }

    @Test fun millisecondLookingTimestampsPassThrough() {
        val notes = KeepImport.parseJson("""[{"title":"t","createdTimestampUsec":1696497300000}]""")
        assertEquals(1696497300000L, notes.single().created)
    }

    private val html = """
        <html><head><meta content="text/html; charset=UTF-8" http-equiv="content-type">
        <title>Shopping</title></head>
        <body>
        <div>Shopping</div>
        <br>
        <div>milk &amp; bread</div>
        </body></html>
    """.trimIndent()

    @Test fun parsesTheHtmlExport() {
        val note = KeepImport.parseHtml(html)
        assertEquals("Shopping", note.title)
        assertEquals("milk & bread", note.text) // title line dropped, entity decoded
    }

    @Test fun anHtmlNoteWithoutATitleKeepsAllItsText() {
        val note = KeepImport.parseHtml("<html><head><title></title></head><body><div>just body</div></body></html>")
        assertEquals("", note.title)
        assertEquals("just body", note.text)
    }

    @Test fun htmlWithoutBodyTagsIsTakenWhole() {
        val note = KeepImport.parseHtml("<html><head><title>T</title></head>loose text</html>")
        assertEquals("T", note.title)
        assertEquals("loose text", note.text)
    }
}
