package com.xnota.core.tools

import com.xnota.core.stroke.RainbowInk

/**
 * Factory presets for the fork's three new pens — neon glow, rainbow and pencil — as pure
 * functions so the tools' whole engine-facing behaviour (which knobs are set, and to what) is
 * unit-testable without a canvas or a Compose tree.
 *
 * The neon pen reuses the composable `neon` rendering every stroke tool already has
 * ([ToolConfig.neon]): a bright core with a wider translucent same-colour underglow, whose
 * geometry the existing four-layer painter and GL glow pass draw. [neonUnderglow] is the pure
 * width/alpha arithmetic of that underglow, kept here so the preset and the tests agree on what
 * "wider translucent glow" means: strictly wider than the core, strictly between transparent and
 * opaque.
 */
object InkToolPresets {

    /** The neon pen's core half-width as a fraction of the configured base width. */
    const val NEON_CORE_FRACTION = 0.3

    /** The underglow's width as a multiple of the core stroke's width (the feature's spec). */
    const val NEON_UNDERGLOW_WIDTH_FACTOR = 2.6

    /** The underglow's alpha (the feature's spec: 30%). */
    const val NEON_UNDERGLOW_ALPHA = 0.30

    /**
     * The neon pen's underglow: `[coreWidth × 2.6]` wide at 30% alpha. Pure arithmetic, the
     * invariant the preset is pinned to (a *wider, translucent* halo under a *bright core*).
     */
    fun neonUnderglow(coreWidth: Double): Pair<Double, Double> =
        coreWidth * NEON_UNDERGLOW_WIDTH_FACTOR to NEON_UNDERGLOW_ALPHA

    /** The neon pen preset: a normal pressure pen with the glow baked on at a balanced strength. */
    fun neon(): ToolConfig = ToolConfig(
        baseWidth = 4.0,
        pressureEnabled = true,
        pressureMinFactor = 0.30,
        directionStrength = 0.0,
        neon = true,
        neonStrength = 0.6,
    )

    /**
     * The rainbow brush preset: an ordinary pressure pen by every stored field — the hue rotation
     * is a render-time read of [RainbowInk] keyed off the stroke's arc, so the stored colour is
     * simply the base hue the toolbar picked.
     */
    fun rainbow(): ToolConfig = ToolConfig(
        baseWidth = 4.0,
        pressureEnabled = true,
        pressureMinFactor = 0.35,
        directionStrength = 0.0,
    )

    /** The pencil's fixed-width look: pressure off, the grain engine doing the width jitter. */
    const val PENCIL_ROUGHNESS = 80.0

    /** The pencil's render-time opacity (the slight translucency of graphite). */
    const val PENCIL_ALPHA = 0.9

    /** The pencil preset: narrow, pressureless, grainy. */
    fun pencil(): ToolConfig = ToolConfig(
        baseWidth = 2.5,
        pressureEnabled = false,
        pressureMinFactor = 1.0,
        directionStrength = 0.0,
        roughness = PENCIL_ROUGHNESS,
    )
}
