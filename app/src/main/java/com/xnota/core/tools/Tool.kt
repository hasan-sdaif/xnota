package com.xnota.core.tools

/** The tool set (spec 04 §1). */
enum class Tool(val id: String) {
    PEN("pen"),
    DASHED("dashed"),
    CALLIGRAPHY("calligraphy"),
    SPEED("speed"),
    TAPER("taper"),
    HIGHLIGHTER("highlighter"),

    /** The fork's neon pen: a bright core over a wider translucent same-colour underglow. An
     *  ordinary pressure pen whose preset simply turns the composable neon rendering on. */
    NEON("neon"),

    /** The fork's rainbow brush: the hue rotates along the stroke's arc length (render-time; the
     *  stored colour is only the base hue). See [com.xnota.core.stroke.RainbowInk]. */
    RAINBOW("rainbow"),

    /** The fork's pencil: a fixed-width, grainy, slightly translucent stroke built on the ink
     *  roughness engine. */
    PENCIL("pencil"),

    ERASER("eraser"),
    PAN("pan"),
    SELECT("select"),
    LASSO("lasso"),
    SCREENSHOT("screenshot"),
    SHAPE("shape"),

    /** The fork's fill (bucket) tool: a tap fills a closed shape's interior — or every closed
     *  shape in the active selection — with the armed ink colour, through the shapes' own
     *  fill colour (`ShapeItem.fillRgba`), as one undo step. Not a stroke tool; a finger taps
     *  it only with finger-draw on, like every other content tool. */
    FILL("fill"),

    TEXT("text"),
    TEXT_BOX("text_box"),
    IMAGE("image"),

    /** Marks a PDF's text: highlights and lines along it, or selects it ([MarkupMode]). */
    MARKUP("markup");

    /** Tools that produce ink via the stroke engine. */
    val isStroke: Boolean get() = this == PEN || this == DASHED || this == CALLIGRAPHY ||
        this == SPEED || this == TAPER || this == HIGHLIGHTER ||
        this == NEON || this == RAINBOW || this == PENCIL

    /**
     * Tools the "draw with finger" gate covers: when finger-draw is off, a finger pans
     * instead of activating these (the stroke tools plus select/lasso/shape/eraser/markup). The
     * stylus always uses the armed tool (its eraser tip still erases); text and pan stay
     * usable by finger either way.
     */
    val fingerPansWhenOff: Boolean get() = isStroke ||
        this == SELECT || this == LASSO || this == SCREENSHOT || this == SHAPE || this == ERASER ||
        this == MARKUP || this == FILL

    /** Render-time ink alpha scale: the highlighter is translucent (spec 03 §3), the pencil
     *  slightly translucent like graphite. */
    val alphaScale: Double get() = when (this) {
        HIGHLIGHTER -> 0.35
        PENCIL -> InkToolPresets.PENCIL_ALPHA
        else -> 1.0
    }

    companion object {
        fun fromId(id: String?): Tool? = entries.firstOrNull { it.id == id }

        /** Armed at startup. */
        val DEFAULT = PAN

        /** Quick-tool-wheel order (spec 06 §11 / 10 §7). The fork's three new pens slot in after
         *  the classic set so existing muscle memory keeps its slots. */
        val wheelOrder = listOf(PEN, DASHED, CALLIGRAPHY, SPEED, TAPER, HIGHLIGHTER, NEON, RAINBOW, PENCIL, ERASER, SELECT, LASSO, SCREENSHOT, SHAPE, FILL, TEXT, MARKUP, PAN)
    }
}
