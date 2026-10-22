package com.xnota.settings

import com.xnota.core.tools.BrushCycle
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Named brush presets: the JSON codec and the (name, tool) identity rules. */
class BrushPresetsTest {

    private fun config(width: Double = 3.5, roughness: Double = 40.0, opacity: Double = 0.7) =
        ToolConfig(baseWidth = width, roughness = roughness, opacity = opacity)

    @Test fun roundTripsThroughJson() {
        val p = BrushPreset("رسم سريع", Tool.PENCIL, config())
        val back = BrushPreset.of(p.toJson())
        assertEquals("رسم سريع", back!!.name)
        assertEquals(Tool.PENCIL, back.tool)
        assertEquals(3.5, back.config.baseWidth, 1e-12)
        assertEquals(40.0, back.config.roughness, 1e-12)
        assertEquals(0.7, back.config.opacity, 1e-12)
    }

    @Test fun blankNameOrUnknownToolDrops() {
        assertNull(BrushPreset.of(JSONObject().put("name", "  ").put("tool", "pen").put("config", JSONObject())))
        assertNull(BrushPreset.of(JSONObject().put("name", "x").put("tool", "bogus").put("config", JSONObject())))
        assertNull(BrushPreset.of(JSONObject().put("name", "x").put("tool", "pen")))
        assertNull(BrushPreset.of(null))
    }

    @Test fun listRoundTripsThroughTheArray() {
        val presets = listOf(
            BrushPreset("Sketch", Tool.PEN, config(2.0)),
            BrushPreset("Glow", Tool.NEON, config(6.0, 0.0, 1.0)),
        )
        val arr = JSONArray().apply { presets.forEach { put(it.toJson()) } }
        val back = (0 until arr.length()).mapNotNull { BrushPreset.of(arr.optJSONObject(it)) }
        assertEquals(presets, back)
    }

    @Test fun opacityPersistsThroughTheToolConfigCodec() {
        val c = config(opacity = 0.45)
        val json = Settings.toolConfigJson(c)
        val back = Settings.toolConfig(json, Tool.PEN)
        assertEquals(0.45, back.opacity, 1e-12)
    }

    @Test fun opacityFloorKeepsInkAGhost() {
        // Out-of-range values clamp into [floor, 1] rather than vanishing the ink.
        val c = ToolConfig(opacity = 0.001)
        val back = Settings.toolConfig(Settings.toolConfigJson(c), Tool.PEN)
        assertTrue(back.opacity >= ToolConfig.OPACITY_MIN)
        assertTrue(back.opacity <= 1.0)
    }
}

/** The toolbar's brush shuffle ring. */
class BrushCycleTest {

    @Test fun everyRingEntryAdvances() {
        for (t in BrushCycle.RING) assertEquals(t, BrushCycle.next(BrushCycle.previous(t)))
    }

    @Test fun ringWrapsAround() {
        // Enough nexts come back to the start.
        var t = Tool.PEN
        repeat(BrushCycle.RING.size) { t = BrushCycle.next(t) }
        assertEquals(Tool.PEN, t)
    }

    @Test fun outsideTheRingStartsAtThePen() {
        assertEquals(Tool.PEN, BrushCycle.next(Tool.ERASER))
        assertEquals(Tool.PEN, BrushCycle.next(Tool.PAN))
    }

    @Test fun ringHoldsOnlyStrokeTools() {
        assertTrue(BrushCycle.RING.all { it.isStroke })
        assertEquals(BrushCycle.RING.size, BrushCycle.RING.distinct().size)
    }
}
