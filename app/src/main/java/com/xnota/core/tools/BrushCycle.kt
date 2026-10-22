package com.xnota.core.tools

/**
 * The stroke-tool ring the toolbar's quick shuffle walks (the fork): a double-tap on any pen
 * button arms the next brush in the ring, so thumbing the same button flips through every pen
 * without reaching across the bar. Pure, so the ring's order is unit-testable apart from the
 * gesture that walks it.
 */
object BrushCycle {
    /** The ring, in shuffle order: pen first, the fork's new pens last. */
    val RING: List<Tool> = listOf(
        Tool.PEN,
        Tool.DASHED,
        Tool.CALLIGRAPHY,
        Tool.SPEED,
        Tool.TAPER,
        Tool.HIGHLIGHTER,
        Tool.NEON,
        Tool.RAINBOW,
        Tool.PENCIL,
    )

    /** The next brush after [tool] in the ring; a tool outside the ring starts at the pen. */
    fun next(tool: Tool): Tool {
        val i = RING.indexOf(tool)
        if (i < 0) return Tool.PEN
        return RING[(i + 1) % RING.size]
    }

    /** The brush before [tool] in the ring; a tool outside the ring starts at the pen. */
    fun previous(tool: Tool): Tool {
        val i = RING.indexOf(tool)
        if (i < 0) return Tool.PEN
        return RING[(i - 1 + RING.size) % RING.size]
    }
}
