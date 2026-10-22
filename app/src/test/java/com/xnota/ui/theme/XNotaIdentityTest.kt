package com.xnota.ui.theme

import com.xnota.core.model.Rgba
import com.xnota.settings.MaterialStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The XNota chrome identity, locked to the reference design's tokens (brand-studio tokens.css):
 * the ink ladder, the emerald accent, the sage hairlines and the light ladder, for every
 * appearance — plus the one property 1.7.0 failed to deliver: the chrome renders the identity
 * no matter which seed the scheme was asked to carry.
 */
class XNotaIdentityTest {

    private fun luminance(c: Rgba): Double =
        (0.299 * c.r + 0.587 * c.g + 0.114 * c.b) / 255.0

    private val emerald = Rgba(0x00, 0xE6, 0x76, 255)

    private fun scheme(dark: Boolean) = MaterialColors.seeded(emerald, dark, MaterialStyle.VIBRANT)

    @Test fun darkChromeCarriesTheInkLadder() {
        val p = xnotaChrome("dark", emerald, scheme(dark = true))
        assertEquals(Rgba(0x05, 0x0B, 0x0A, 255), p.bg)
        assertEquals(Rgba(0x0A, 0x12, 0x11, 255), p.panel)
        assertEquals(Rgba(0x0A, 0x12, 0x11, 255), p.menuBg)
        assertEquals(Rgba(0x0D, 0x19, 0x17, 255), p.surface)
        assertEquals(Rgba(0x10, 0x20, 0x1D, 255), p.surfaceHi)
        assertEquals(Rgba(0xEF, 0xF8, 0xF4, 255), p.text)
        assertEquals(Rgba(0xBE, 0xD0, 0xC9, 255), p.textDim)
        assertEquals(emerald, p.accent)
        assertTrue(p.isDark)
    }

    @Test fun identitySurvivesAnySeedTheUserCarried() {
        // The 1.7.0 failure mode: the install's own seed (a gold preset, a wallpaper scheme)
        // used to repaint the whole chrome. The brand chrome ignores the scheme's tonal ramp —
        // only the accent itself is the user's.
        val gold = Rgba(0x9A, 0x7C, 0x42, 255)
        val p = xnotaChrome("dark", gold, MaterialColors.seeded(gold, dark = true))
        assertEquals(Rgba(0x05, 0x0B, 0x0A, 255), p.bg)
        assertEquals(Rgba(0x0A, 0x12, 0x11, 255), p.menuBg)
        assertEquals(Rgba(0xEF, 0xF8, 0xF4, 255), p.text)
        assertEquals(gold, p.accent)
    }

    @Test fun hairlinesSitJustAboveTheirSurface() {
        val p = xnotaChrome("dark", emerald, scheme(dark = true))
        assertTrue(luminance(p.border) > luminance(p.bg))
        assertTrue(luminance(p.border) < luminance(p.textDim))
        // Quiet by design: the hairline stays far nearer its surface than any content colour —
        // closer to the background it floats on than to the text it separates.
        assertTrue(luminance(p.border) - luminance(p.bg) < luminance(p.textDim) - luminance(p.border))
    }

    @Test fun selectedRolesReadAsTheReferenceActiveRow() {
        val p = xnotaChrome("dark", emerald, scheme(dark = true))
        // selectionBackground: emerald at ~17% over the ink; selectionForeground: the mint.
        assertEquals(Rgba(0x47, 0xFF, 0xC1, 255), p.selectionForeground)
        assertTrue(luminance(p.selectionBackground) > luminance(p.menuBg))
        assertTrue(luminance(p.selectionBackground) < luminance(p.accent))
        // Buttons' text on emerald is the near-black green, never white.
        assertEquals(Rgba(0x03, 0x22, 0x18, 255), p.materialColors.onPrimary)
    }

    @Test fun lightChromeCarriesTheLightLadderAndALegibleAccent() {
        val p = xnotaChrome("light", emerald, scheme(dark = false))
        assertEquals(Rgba(0xF1, 0xF6, 0xF3, 255), p.bg)
        assertEquals(Rgba(0xFF, 0xFF, 0xFF, 255), p.menuBg)
        assertEquals(Rgba(0x11, 0x23, 0x1C, 255), p.text)
        assertEquals(Rgba(0x47, 0x60, 0x57, 255), p.textDim)
        assertFalse(p.isDark)
        // Neon emerald can't carry weight on white; the light accent sinks for legibility.
        assertTrue(luminance(p.accent) < luminance(emerald))
        assertTrue(luminance(p.accent) < 0.45)
    }

    @Test fun oledDropsTheBigSurfacesToBlack() {
        val p = xnotaChrome("oled", emerald, scheme(dark = true))
        val black = Rgba(0, 0, 0, 255)
        assertEquals(black, p.bg)
        assertEquals(black, p.panel)
        assertEquals(black, p.menuBg)
        assertTrue(p.isDark)
        assertEquals(emerald, p.accent)
        // The one lifted surface: paper steps up to the ink-so-dark tone so paper elements
        // don't vanish against pure black.
        assertTrue(luminance(p.paper) > 0.0)
    }

    @Test fun defaultPaletteIsTheBrandChrome() {
        assertEquals(xnotaChrome("dark", emerald, MaterialColors.seeded(emerald, true, MaterialStyle.VIBRANT, com.xnota.settings.Preferences.DEFAULT_MATERIAL_SURFACE)), Palette.DEFAULT)
    }
}
