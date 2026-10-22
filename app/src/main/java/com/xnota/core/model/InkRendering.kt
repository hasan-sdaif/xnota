package com.xnota.core.model

import com.xnota.vendor.materialcolor.hct.Hct

/**
 * Theme-adaptive ink rendering (xnota).
 *
 * The fork's Arabic-first promise: ink stays readable whatever paper the current theme lays
 * down. A note written in white ink on the dark theme's near-black paper becomes near-black ink
 * the moment the same note opens on the light theme's white paper — and back — without the
 * stored colour ever changing. The transform is applied only where ink is *resolved for
 * rendering* ([Stroke.renderColor], [ShapeItem.pen], text paints), never where colours are
 * authored (palettes, pickers, lasso colour edits), so what a stroke *says* is independent of
 * what the reader currently *sees*.
 *
 * The transform is a process-wide install rather than a render parameter: [Stroke.renderColor]
 * is read from deep inside both render stacks (paged CPU paint, GL mesher, PDF export, page
 * caches, thumbnails) and by code that has no renderer argument to hand. The paper is a global
 * anyway — [com.xnota.settings.Preferences.pageColor] is one value for the whole app, and the
 * palette's paper follows the single appearance mode — so one volatile reference read at colour
 * resolution time is both correct and cheap. PDF export keeps the on-screen paper colour, so
 * exported pages match what the reader saw.
 */
object InkRendering {

    /** The installed transform; volatile so any render thread sees the latest install. */
    @Volatile
    var transform: InkTransform = InkTransformOff
        private set

    /**
     * The installed highlighter blend (the fork's `highlighter_blend` preference), read wherever
     * the highlighter's composite mode is resolved — the paged canvas's layer blend and the GL
     * canvas's ink pass. MULTIPLY (the historical look) by default. Installed like the transform
     * above: a property of the reader, applied at render time, never written into a note.
     */
    @Volatile
    var highlighterBlend: HighlighterBlend = HighlighterBlend.MULTIPLY

    /** Install [t] as the process-wide transform; cheap enough to call on every theme change. */
    fun install(t: InkTransform) {
        transform = t
    }

    /** The single entry render sites call: the adapted ink colour for the current paper. */
    fun adapt(c: Rgba): Rgba = transform.apply(c)
}

/** How strongly stored ink colours are re-toned to the paper at render time. */
enum class InkAdaptiveMode(val id: String) {
    OFF("off"),
    /** Only greys flip: near-white ink reads as near-black on light paper and vice versa. */
    ACHROMATIC("achromatic"),
    /** Greys flip, and chromatic ink's tone is kept inside a readable band for the paper. */
    FULL("full");

    companion object {
        fun fromId(id: String?): InkAdaptiveMode = entries.firstOrNull { it.id == id } ?: FULL
    }
}

/**
 * How the highlighter composites (the fork's `highlighter_blend` preference). [MULTIPLY] is the
 * historical look — the marker tints what is under it and can never lighten dark ink, so text
 * stays legible through a highlight. [NORMAL] is plain alpha compositing: brighter, more like a
 * wax crayon, and the only mode that shows anything on a page already darker than the ink.
 */
enum class HighlighterBlend(val id: String) {
    NORMAL("normal"),
    MULTIPLY("multiply");

    companion object {
        fun fromId(id: String?): HighlighterBlend = entries.firstOrNull { it.id == id } ?: MULTIPLY
    }
}

/** A colour mapping applied to ink at render time. Implementations must be thread-safe. */
interface InkTransform {
    fun apply(c: Rgba): Rgba
}

/** [InkAdaptiveMode.OFF] as a singleton: every colour passes through untouched. */
object InkTransformOff : InkTransform {
    override fun apply(c: Rgba): Rgba = c
}

/**
 * The paper-aware transform. Constructed once per palette/paper change with the effective paper
 * colour, then installed; it is immutable afterwards, so render threads race nothing.
 *
 * Greyscale ink (HCT chroma below [GRAY_CHROMA_MAX]) is treated as "ink" in the typographic
 * sense: its tone is pushed to the far side of the paper's tone, so black-on-black and
 * white-on-white both resolve to their readable opposite. Chromatic ink is left alone in
 * [InkAdaptiveMode.ACHROMATIC]; in [InkAdaptiveMode.FULL] its tone is clamped into the band the
 * paper can hold — dark-enough hues lifted on dark paper, bright ones pulled back on light —
 * with hue and chroma preserved, which keeps the *colour* the writer chose while keeping it
 * legible.
 */
class PaperInkTransform(paper: Rgba, val mode: InkAdaptiveMode) : InkTransform {

    private val darkPaper: Boolean = paper.luminance() < 0.5

    override fun apply(c: Rgba): Rgba {
        if (mode == InkAdaptiveMode.OFF || c.a == 0) return c
        val hct = Hct.fromInt(c.toArgb())
        val chroma = hct.chroma
        val tone = hct.tone
        if (chroma <= GRAY_CHROMA_MAX) {
            // Greyscale ink: flip only the shades that vanish against this paper.
            if (darkPaper && tone < DARK_INK_TONE) {
                hct.tone = READABLE_TONE_DARK
            } else if (!darkPaper && tone > LIGHT_INK_TONE) {
                hct.tone = READABLE_TONE_LIGHT
            } else {
                return c
            }
            return keepAlpha(c, hct)
        }
        if (mode != InkAdaptiveMode.FULL) return c
        // Chromatic ink: hold the tone inside the paper's readable band, hue and chroma intact.
        val (lo, hi) = if (darkPaper) FULL_BAND_DARK else FULL_BAND_LIGHT
        when {
            tone < lo -> hct.tone = lo + (lo - tone) * FULL_PULL
            tone > hi -> hct.tone = hi - (tone - hi) * FULL_PULL
            else -> return c
        }
        return keepAlpha(c, hct)
    }

    private fun keepAlpha(original: Rgba, hct: Hct): Rgba {
        val mapped = Rgba.fromArgb(hct.toInt())
        // Hct discards alpha; the ink's own translucency (highlighter et al.) survives verbatim.
        return mapped.copy(a = original.a)
    }

    companion object {
        /** Below this HCT chroma a colour reads as a grey, whatever its hue formally is. */
        const val GRAY_CHROMA_MAX = 9.0

        /** A grey darker than this on dark paper is invisible: flip it to the light side. */
        const val DARK_INK_TONE = 25.0

        /** A grey lighter than this on light paper is invisible: flip it to the dark side. */
        const val LIGHT_INK_TONE = 82.0

        /** Where flipped greys land: near-white on dark paper, near-black on light paper. */
        const val READABLE_TONE_DARK = 94.0
        const val READABLE_TONE_LIGHT = 14.0

        /** The tone band chromatic ink is held inside, per paper darkness. */
        val FULL_BAND_DARK = 48.0 to 96.0
        val FULL_BAND_LIGHT = 12.0 to 74.0

        /** How far past the band an out-of-band tone is pulled back into it (0 = hard clamp). */
        const val FULL_PULL = 0.35

        /** Build (or collapse to OFF) without the caller repeating the mode juggling. */
        fun forPaper(paper: Rgba, mode: InkAdaptiveMode): InkTransform =
            if (mode == InkAdaptiveMode.OFF) InkTransformOff else PaperInkTransform(paper, mode)
    }
}
