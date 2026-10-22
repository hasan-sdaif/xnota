package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoplinImportTest {

    private val colonStyle = """
        :title: Meeting notes
        :created: 1696497300000
        :updated: 1696583700000
        :id: abc123

        # Agenda

        - coffee
        - dates
    """.trimIndent()

    private val yamlStyle = """
        ---
        title: "Quoted title"
        created: 2023-10-05T09:15:00Z
        updated: 2023-10-06T10:15:00.500Z
        ---

        Body line one.
        Body line two.
    """.trimIndent()

    @Test fun parsesTheColonFrontMatter() {
        val note = JoplinImport.parseOne("meeting.md", colonStyle)
        assertEquals("meeting.md", note.filename)
        assertEquals("Meeting notes", note.title)
        assertEquals(1696497300000L, note.created)
        assertEquals(1696583700000L, note.updated)
        assertEquals("# Agenda\n\n- coffee\n- dates", note.body)
    }

    @Test fun parsesTheYamlFrontMatter() {
        val note = JoplinImport.parseOne("quoted.md", yamlStyle)
        assertEquals("Quoted title", note.title) // quotes peeled
        assertEquals(1696497300000L, note.created) // ISO -> ms, UTC
        assertEquals(1696587300500L, note.updated) // fraction kept
        assertEquals("Body line one.\nBody line two.", note.body)
    }

    @Test fun aFileWithNoFrontMatterIsAllBody() {
        val note = JoplinImport.parseOne("plain.md", "# Just a note\n\ntext")
        assertEquals("", note.title)
        assertEquals(0L, note.created)
        assertEquals("# Just a note\n\ntext", note.body)
    }

    @Test fun parseSkipsNonMarkdownFiles() {
        val notes = JoplinImport.parse(
            listOf(
                "folder/resource.jpg" to "not a note",
                "a.md" to ":title: A\n\nbody",
                "b.MD" to ":title: B\n\nbody",
                "c.txt" to ":title: C",
            ),
        )
        assertEquals(listOf("a.md", "b.MD"), notes.map { it.filename })
        assertEquals(listOf("A", "B"), notes.map { it.title })
    }

    @Test fun aBodyLineThatLooksLikeFrontMatterIsLeftAlone() {
        // "title:" mid-body only counts at the very top; here the front-matter already ended.
        val note = JoplinImport.parseOne("x.md", ":title: Real\n\n:created: 999\nstill body")
        assertEquals("Real", note.title)
        assertEquals(0L, note.created)
        assertTrue(note.body.contains(":created: 999"))
        assertEquals(":created: 999\nstill body", note.body)
    }

    @Test fun missingOrNonsenseStampsComeBackZero() {
        val note = JoplinImport.parseOne("x.md", ":title: T\n:created: not-a-number\n\nb")
        assertEquals(0L, note.created)
    }
}
