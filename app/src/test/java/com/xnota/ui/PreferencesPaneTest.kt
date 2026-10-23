package com.xnota.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings hub's category gate: the All chip unfolds every section, a picked category shows
 * its own sections only, and an active search spans them all regardless of the selection.
 */
class PreferencesPaneTest {

    @Test
    fun `the all chip unfolds every section`() {
        for (section in SettingsCategory.entries) {
            assertTrue(
                "All must show the ${section.name} sections",
                sectionInCategory(SettingsCategory.ALL, section, searching = false),
            )
        }
    }

    @Test
    fun `a picked category shows its own sections only`() {
        for (picked in SettingsCategory.entries) {
            for (section in SettingsCategory.entries) {
                val shown = sectionInCategory(picked, section, searching = false)
                if (picked == SettingsCategory.ALL) continue // covered above
                if (section == picked) {
                    assertTrue("${picked.name} must show its own sections", shown)
                } else {
                    assertFalse("${picked.name} must fold the ${section.name} sections away", shown)
                }
            }
        }
    }

    @Test
    fun `an active search spans every section regardless of the selection`() {
        for (picked in SettingsCategory.entries) {
            for (section in SettingsCategory.entries) {
                assertTrue(
                    "a search from ${picked.name} must reach the ${section.name} sections",
                    sectionInCategory(picked, section, searching = true),
                )
            }
        }
    }
}
