package com.xnota.core.text

/**
 * Named text-style presets (the fork): Title, Heading, Body and Caption as one-tap chips on the
 * text format bar. Each is a [CharStyle] rewrite relative to the flow's base size — a preset says
 * "twice the body, bold", not "24 pt", so it lands the same on a phone-sized default and a
 * tablet's. Pure, so the bar's chips and the tests agree on what each preset means.
 */
object TextPresets {

    /** The presets the format bar offers, in bar order. */
    enum class Kind { TITLE, HEADING, BODY, CAPTION }

    /**
     * The style a preset makes of [current] at the flow's [baseSizePt]: Title is double size and
     * bold, Heading one-and-a-half and bold, Body clears both back to whatever the paragraph sets,
     * Caption three-quarters and never bold.
     */
    fun apply(kind: Kind, current: CharStyle, baseSizePt: Double): CharStyle = when (kind) {
        Kind.TITLE -> current.copy(bold = true, sizePt = (baseSizePt * 2.0).coerceIn(MIN_PT, MAX_PT))
        Kind.HEADING -> current.copy(bold = true, sizePt = (baseSizePt * 1.5).coerceIn(MIN_PT, MAX_PT))
        Kind.BODY -> current.copy(bold = false, sizePt = null)
        Kind.CAPTION -> current.copy(bold = false, sizePt = (baseSizePt * 0.75).coerceIn(MIN_PT, MAX_PT))
    }

    /** The bounds every preset's computed size keeps, matching the size stepper's. */
    const val MIN_PT = 6.0
    const val MAX_PT = 96.0
}
