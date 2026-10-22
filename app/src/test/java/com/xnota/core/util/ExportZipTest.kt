package com.xnota.core.util

import com.xnota.core.util.ExportZip.Entry
import com.xnota.core.util.ExportZip.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportZipTest {

    private fun doc(path: String) = Node(path.substringAfterLast('/'), path, "uri:$path", isDir = false)

    private fun dir(path: String, vararg children: Node) =
        Node(path.substringAfterLast('/'), path, "uri:$path", isDir = true, children = children.toList())

    private fun pdf(path: String, source: String) = Entry(path, "uri:$source", source.substringAfterLast('/'))

    private fun folder(path: String) = Entry(path, null, path.trimEnd('/').substringAfterLast('/'))

    @Test
    fun `picked documents land at the top as PDFs`() {
        assertEquals(
            listOf(pdf("Board.pdf", "a/Board.xcanvas"), pdf("Lecture 1.pdf", "b/Lecture 1.xnote")),
            ExportZip.plan(listOf(doc("b/Lecture 1.xnote"), doc("a/Board.xcanvas"))),
        )
    }

    @Test
    fun `a picked folder keeps its tree, empty folders included`() {
        val school = dir(
            "School",
            doc("School/Timetable.xnote"),
            dir("School/Maths", doc("School/Maths/Algebra.xnote"), dir("School/Maths/Old")),
            dir("School/Art"),
        )
        assertEquals(
            listOf(
                folder("School/"),
                folder("School/Art/"),
                folder("School/Maths/"),
                folder("School/Maths/Old/"),
                pdf("School/Maths/Algebra.pdf", "School/Maths/Algebra.xnote"),
                pdf("School/Timetable.pdf", "School/Timetable.xnote"),
            ),
            ExportZip.plan(listOf(school)),
        )
    }

    @Test
    fun `clashing names are numbered, ignoring case`() {
        val plan = ExportZip.plan(
            listOf(
                dir(
                    "Notes",
                    doc("Notes/plan.xnote"),
                    doc("Notes/Plan.xcanvas"),
                    doc("Notes/plan (2).xnote"),
                    dir("Notes/plan.pdf"),
                ),
                doc("x/Notes.xnote"),
                dir("y/Notes"),
            ),
        )
        val paths = plan.map { it.path }
        assertEquals(paths.size, paths.map { it.lowercase() }.toSet().size)
        assertTrue("Notes/" in paths && "Notes (2)/" in paths && "Notes.pdf" in paths)
        assertEquals(
            setOf("Notes/plan.pdf/", "Notes/plan (2).pdf", "Notes/Plan (3).pdf", "Notes/plan (4).pdf"),
            paths.filter { it.startsWith("Notes/") && it != "Notes/" }.toSet(),
        )
    }

    @Test
    fun `a pick inside another picked folder is exported only there`() {
        val inner = dir("Top/Inner", doc("Top/Inner/deep.xnote"))
        val top = dir("Top", inner, doc("Top/note.xnote"))
        val plan = ExportZip.plan(listOf(doc("Top/Inner/deep.xnote"), inner, top))
        assertEquals(
            listOf(
                folder("Top/"),
                folder("Top/Inner/"),
                pdf("Top/Inner/deep.pdf", "Top/Inner/deep.xnote"),
                pdf("Top/note.pdf", "Top/note.xnote"),
            ),
            plan,
        )
    }

    @Test
    fun `the same pick twice is exported once`() {
        assertEquals(listOf(pdf("a.pdf", "a.xnote")), ExportZip.plan(listOf(doc("a.xnote"), doc("a.xnote"))))
    }

    @Test
    fun `names are kept to one path segment`() {
        val plan = ExportZip.plan(
            listOf(
                Node("a/b\\c.xnote", "k1", "s1", isDir = false),
                Node("..", "k2", "s2", isDir = true, children = listOf(Node("tab\there.xnote", "k3", "s3", isDir = false))),
                Node(".xnote", "k4", "s4", isDir = false),
            ),
        )
        assertEquals(
            listOf(
                Entry("_/", null, ".."),
                Entry("_/tab_here.pdf", "s3", "tab\there.xnote"),
                Entry("_.pdf", "s4", ".xnote"),
                Entry("a_b_c.pdf", "s1", "a/b\\c.xnote"),
            ),
            plan,
        )
    }

    @Test
    fun `folders without documents plan no PDFs`() {
        val plan = ExportZip.plan(listOf(dir("Empty", dir("Empty/Also"))))
        assertEquals(listOf(folder("Empty/"), folder("Empty/Also/")), plan)
        assertTrue(plan.none { it.source != null })
    }
}
