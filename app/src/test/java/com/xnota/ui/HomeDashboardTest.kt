package com.xnota.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dashboard header's quick actions as data: the four cards the reference design's home opens
 * with (new note, new canvas, import PDF, new folder) ride the create menu's own glyphs and label
 * resources, so the header can never drift from the menu that owns the same actions. The stats and
 * the cover chooser are likewise pure data — the home presentation can rest on them untested in
 * itself, as presentation code should.
 */
class HomeDashboardTest {

    @Test
    fun `four quick actions carry distinct glyphs and labels`() {
        assertEquals(4, dashboardQuickActions.size)
        assertEquals(4, dashboardQuickActions.map { it.icon }.toSet().size)
        assertEquals(4, dashboardQuickActions.map { it.labelRes }.toSet().size)
    }

    @Test
    fun `three cards start a create mode and one imports a pdf`() {
        assertEquals(listOf(CreateMode.FILE, CreateMode.CANVAS, null, CreateMode.FOLDER), dashboardQuickActions.map { it.mode })
        assertEquals(1, dashboardQuickActions.count { it.mode == null })
    }

    @Test
    fun `each quick action carries its own line of description`() {
        assertEquals(4, dashboardQuickActions.map { quickActionSub(it.mode) }.toSet().size)
    }

    @Test
    fun `the description follows the action's create mode`() {
        assertEquals(R.string.quick_sub_note, quickActionSub(CreateMode.FILE))
        assertEquals(R.string.quick_sub_canvas, quickActionSub(CreateMode.CANVAS))
        assertEquals(R.string.quick_sub_import, quickActionSub(null))
        assertEquals(R.string.quick_sub_folder, quickActionSub(CreateMode.FOLDER))
    }

    private fun entry(name: String, modified: Long, isDir: Boolean = false) =
        BrowseEntry(name, "uri:$name", isDir, 0, modified, 0, "")

    @Test
    fun `the stats count the shelf's own files, pages and newest touch`() {
        val entries = listOf(
            entry("Lecture notebook.flow", 100L),
            entry("Ideas.flow", 200L),
            entry("Paper.pdf", 300L),
            entry("Sketches.flow", 0L),
            entry("Folder", 400L, isDir = true),
        )
        val stats = homeStats(
            entries,
            isPdf = { it.name.endsWith(".pdf") },
            pagesOf = { if (it.name == "Lecture notebook.flow") 28 else if (it.name.endsWith(".pdf")) 42 else 3 },
        )
        assertEquals(3, stats.notes)
        assertEquals(1, stats.pdfs)
        assertEquals(76, stats.pages)
        assertEquals(300L, stats.lastModified)
    }

    @Test
    fun `an empty shelf counts nothing`() {
        assertEquals(HomeStats(0, 0, 0, 0L), homeStats(emptyList(), { false }, { 0 }))
    }

    @Test
    fun `the cover follows the kind and the page count`() {
        assertEquals(CoverKind.PAPER, coverKind(EntryKind.PDF, 1))
        assertEquals(CoverKind.NOTEBOOK, coverKind(EntryKind.NOTE, 28))
        assertEquals(CoverKind.NOTEBOOK, coverKind(EntryKind.CANVAS, 2))
        assertEquals(CoverKind.PLAIN, coverKind(EntryKind.NOTE, 1))
    }
}
