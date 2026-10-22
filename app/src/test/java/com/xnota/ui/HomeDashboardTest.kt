package com.xnota.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dashboard header's quick actions as data: the four cards the reference design's home opens
 * with (new note, new canvas, import PDF, new folder) ride the create menu's own glyphs and label
 * resources, so the header can never drift from the menu that owns the same actions.
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
}
