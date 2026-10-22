package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppImportTest {

    private val enex = """
        <?xml version="1.0" encoding="UTF-8"?>
        <en-export><note>
        <title><![CDATA[Recipe ideas]]></title>
        <created>20231005T091500Z</created>
        <tag><![CDATA[cooking]]></tag>
        <content><![CDATA[<en-note><div>Pasta</div><div>and cheese</div></en-note>]]></content>
        </note></en-export>
    """.trimIndent()

    @Test fun evernoteNotesCarryTitleTagsAndStamp() {
        val notes = evernoteNotesOf(enex)
        assertEquals(1, notes.size)
        val n = notes[0]
        assertEquals("Recipe ideas", n.title)
        assertEquals("Pasta\nand cheese", n.text)
        assertEquals(listOf("cooking"), n.tags)
        assertEquals(1696497300000L, n.created)
        assertTrue(!n.markdown) // ENML was stripped to plain lines
    }

    @Test fun brokenEvernoteXmlYieldsNoNotes() {
        assertTrue(evernoteNotesOf("not xml <<<").isEmpty())
    }

    @Test fun keepJsonArraysBecomeNotesWithLabels() {
        val notes = keepNotesOf("""[{"title":"Shopping","text":"milk","labels":[{"name":"errands"}]}]""")
        assertEquals(1, notes.size)
        assertEquals("Shopping", notes[0].title)
        assertEquals(listOf("errands"), notes[0].tags)
        assertTrue(!notes[0].markdown)
    }

    @Test fun takeoutsLoneKeepObjectIsWrappedIntoAnArray() {
        // Takeout hands each Keep note over as its own single-object JSON file.
        val notes = keepNotesOf("""{"title":"Thought","text":"a quick one"}""")
        assertEquals(1, notes.size)
        assertEquals("Thought", notes[0].title)
        assertEquals("a quick one", notes[0].text)
    }

    @Test fun keepHtmlFallsInWhenTheFileIsNotJson() {
        val notes = keepNotesOf("<html><head><title>Shopping</title></head><body><div>milk</div></body></html>")
        assertEquals(1, notes.size)
        assertEquals("Shopping", notes[0].title)
        assertEquals("milk", notes[0].text)
    }

    @Test fun aKeepFileThatIsNeitherShapeYieldsNothing() {
        assertTrue(keepNotesOf("plain text, not an export").isEmpty())
        assertTrue(keepNotesOf("").isEmpty())
    }

    @Test fun joplinFilesKeepTheirFrontMatterTitlesAndMarkdownBodies() {
        val notes = joplinNotesOf(listOf("meeting.md" to ":title: Meeting\n\n# Agenda\n\n- coffee"))
        assertEquals(1, notes.size)
        assertEquals("Meeting", notes[0].title)
        assertEquals("# Agenda\n\n- coffee", notes[0].text)
        assertTrue(notes[0].markdown)
        assertTrue(notes[0].tags.isEmpty()) // Joplin's front matter carries no labels
    }

    @Test fun aJoplinFileWithoutATitleTakesItsStem() {
        val notes = joplinNotesOf(listOf("2010-05-19.md" to "just body text"))
        assertEquals("2010-05-19", notes[0].title)
    }

    @Test fun joplinNonMarkdownFilesFallAway() {
        assertTrue(joplinNotesOf(listOf("resource.jpg" to "not a note")).isEmpty())
    }
}
