package com.xnota.ui.theme

import com.xnota.core.model.Rgba

/**
 * The XNota brand tokens — the reference design's own values (brand-studio `css/tokens.css`),
 * the one place the whole identity lives. Surfaces are the ink ladder; the accent is emerald;
 * hairlines are sage at the reference's two weights pre-mixed opaque for Compose borders.
 */
object XNotaBrand {
    /** brand-500: the emerald accent — active states and primary actions only. */
    val ACCENT = Rgba(0x00, 0xE6, 0x76, 255)

    /** brand-300: the mint — selected/active text and icon tint over dark surfaces. */
    val ACCENT_BRIGHT = Rgba(0x47, 0xFF, 0xC1, 255)

    /** brand-700: the forest — deep accent, and the light theme's active tint. */
    val ACCENT_DEEP = Rgba(0x00, 0x9E, 0x59, 255)

    /** The text on emerald buttons: the reference's near-black green, not white. */
    val ON_ACCENT = Rgba(0x03, 0x22, 0x18, 255)

    // The ink ladder (dark surfaces).
    val INK950 = Rgba(0x05, 0x0B, 0x0A, 255)
    val INK925 = Rgba(0x08, 0x10, 0x0F, 255)
    val INK900 = Rgba(0x0A, 0x12, 0x11, 255)
    val INK850 = Rgba(0x0D, 0x19, 0x17, 255)
    val INK800 = Rgba(0x10, 0x20, 0x1D, 255)
    val INK750 = Rgba(0x13, 0x27, 0x24, 255)

    // The light ladder (tokens.css [data-theme="light"]).
    val LIGHT_BG = Rgba(0xF1, 0xF6, 0xF3, 255)
    val LIGHT_CARD = Rgba(0xFF, 0xFF, 0xFF, 255)
    val LIGHT_INPUT = Rgba(0xF8, 0xFB, 0xF9, 255)
    val LIGHT_SURFACE = Rgba(0xEF, 0xF5, 0xF1, 255)
    val LIGHT_SURFACE_HI = Rgba(0xE8, 0xF1, 0xEC, 255)
    val LIGHT_TEXT = Rgba(0x11, 0x23, 0x1C, 255)
    val LIGHT_TEXT_SOFT = Rgba(0x47, 0x60, 0x57, 255)

    /** mist: primary text on dark. */
    val TEXT = Rgba(0xEF, 0xF8, 0xF4, 255)

    /** sage: secondary text on dark. */
    val TEXT_SOFT = Rgba(0xBE, 0xD0, 0xC9, 255)

    val BLACK = Rgba(0x00, 0x00, 0x00, 255)

    /** The quiet hairline: sage at 14% over a dark surface (tokens.css --line). */
    fun line(over: Rgba): Rgba = ColorMath.mix(over, SAGE, 0.14)

    /** The stronger hairline: sage at 24% (--line-strong). */
    fun lineStrong(over: Rgba): Rgba = ColorMath.mix(over, SAGE, 0.24)

    /** Light-mode hairline: pine at 12%. */
    fun lightLine(over: Rgba): Rgba = ColorMath.mix(over, PINE, 0.12)

    private val SAGE = Rgba(0xAE, 0xDE, 0xCB, 255)
    private val PINE = Rgba(0x1C, 0x4D, 0x37, 255)
}

/**
 * The XNota chrome: the app shell's palette hand-built from the brand tokens so the reference
 * design's look renders unconditionally — no user preference can hide it, the lesson of 1.7.0.
 * What stays the user's own: the appearance mode (dark/light/OLED, via [appearance]) and the
 * accent colour [accent] (the brand emerald unless re-picked). [m] carries the Material scheme
 * the components read; its primary roles are re-stamped to the accent so Material widgets
 * (switches, chips, sliders, selected menu rows) never disagree with the chrome.
 */
fun xnotaChrome(appearance: String, accent: Rgba, m: MaterialColors): Palette = when (appearance) {
    "light" -> xnotaLight(accent, m)
    "oled" -> xnotaOled(accent, m)
    else -> xnotaDark(accent, m)
}

private fun xnotaDark(accent: Rgba, m: MaterialColors): Palette = Palette(
    bg = XNotaBrand.INK950,
    panel = XNotaBrand.INK900,
    paper = XNotaBrand.INK850,
    paperBorder = XNotaBrand.line(XNotaBrand.INK850),
    accent = accent,
    border = XNotaBrand.line(XNotaBrand.INK900),
    text = XNotaBrand.TEXT,
    textDim = XNotaBrand.TEXT_SOFT,
    surface = XNotaBrand.INK850,
    surfaceHi = XNotaBrand.INK800,
    menuBg = XNotaBrand.INK900,
    isDark = true,
    materialColors = brandedScheme(accent, m, dark = true),
)

private fun xnotaLight(accent: Rgba, m: MaterialColors): Palette {
    // The reference's light theme keeps emerald accents, but a neon accent can't carry text or
    // icons on white; sink bright accents the way the seeded scheme always did.
    val legible = ColorMath.darkenForLight(accent)
    return Palette(
        bg = XNotaBrand.LIGHT_BG,
        panel = XNotaBrand.LIGHT_CARD,
        paper = XNotaBrand.LIGHT_INPUT,
        paperBorder = XNotaBrand.lightLine(XNotaBrand.LIGHT_INPUT),
        accent = legible,
        border = XNotaBrand.lightLine(XNotaBrand.LIGHT_CARD),
        text = XNotaBrand.LIGHT_TEXT,
        textDim = XNotaBrand.LIGHT_TEXT_SOFT,
        surface = XNotaBrand.LIGHT_SURFACE,
        surfaceHi = XNotaBrand.LIGHT_SURFACE_HI,
        menuBg = XNotaBrand.LIGHT_CARD,
        isDark = false,
        materialColors = brandedScheme(legible, m, dark = false),
    )
}

private fun xnotaOled(accent: Rgba, m: MaterialColors): Palette = xnotaDark(accent, m).copy(
    bg = XNotaBrand.BLACK,
    panel = XNotaBrand.BLACK,
    menuBg = XNotaBrand.BLACK,
    paper = XNotaBrand.INK925,
)

/**
 * Re-stamp a seeded scheme's primary roles onto the chrome's accent: the accent itself, its
 * near-black-green opposite (buttons never flip to white-on-neon), and the emerald-tinted
 * container pair the sidebar's and menus' selected rows read (selectionBackground/Foreground).
 */
private fun brandedScheme(accent: Rgba, m: MaterialColors, dark: Boolean): MaterialColors = m.copy(
    primary = accent,
    onPrimary = XNotaBrand.ON_ACCENT,
    primaryContainer = if (dark) ColorMath.mix(XNotaBrand.INK925, accent, 0.17) else ColorMath.mix(XNotaBrand.LIGHT_CARD, accent, 0.16),
    onPrimaryContainer = if (dark) XNotaBrand.ACCENT_BRIGHT else ColorMath.mix(accent, XNotaBrand.BLACK, 0.45),
    surfaceTint = accent,
    inversePrimary = accent,
)
