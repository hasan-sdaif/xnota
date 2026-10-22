package com.xnota.settings

import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * One named brush preset (the fork): the whole style snapshot of a tool at the moment it was
 * saved — width, pressure response, roughness, scatter, opacity, glow — under a name the user
 * chose. Presets are stored in [Settings.brushPresets] as the JSON array `brush_presets`, next to
 * the per-tool styles, and are applied back through the same [ToolPopupHost.updateToolConfig]
 * path the tool's own popup uses, so a preset is exactly "this tool, as I tuned it then".
 *
 * A preset is identified by its (name, tool) pair: "Sketch" can exist for the pen and again for
 * the pencil. Applying always lands on the tool it was saved for.
 */
data class BrushPreset(
    val name: String,
    val tool: Tool,
    val config: ToolConfig,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("tool", tool.id)
        .put("config", Settings.toolConfigJson(config))

    companion object {
        /** Forgiving read: a blank name, an unknown tool or a missing config drops the entry. */
        fun of(o: JSONObject?): BrushPreset? {
            if (o == null) return null
            val name = o.optString("name", "").trim()
            val tool = Tool.fromId(o.optString("tool", "")) ?: return null
            if (name.isEmpty()) return null
            val config = o.optJSONObject("config")?.let { Settings.toolConfig(it, tool) } ?: return null
            return BrushPreset(name, tool, config)
        }
    }
}

/** The preset list's JSON codec and its pure list operations. */
object BrushPresets {
    /** How many presets are kept; the oldest beyond the cap fall off when a new one is saved. */
    const val MAX_PRESETS = 24

    /** Read the `brush_presets` array; unreadable entries are dropped, never fatal. */
    fun of(a: JSONArray?): List<BrushPreset> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i -> BrushPreset.of(a.optJSONObject(i)) }
    }

    fun toJson(list: List<BrushPreset>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

    /**
     * Save [preset]: a preset with the same (name, tool) is replaced in place, a new one is
     * appended, and the list is capped at [MAX_PRESETS] by dropping the oldest. A blank name is
     * rejected (the list returns unchanged). Pure, so the save semantics are unit-testable.
     */
    fun save(list: List<BrushPreset>, preset: BrushPreset): List<BrushPreset> {
        val name = preset.name.trim()
        if (name.isEmpty()) return list
        val named = preset.copy(name = name)
        val replaced = list.any { it.name == name && it.tool == named.tool }
        val next = if (replaced) list.map { if (it.name == name && it.tool == named.tool) named else it } else list + named
        return if (next.size > MAX_PRESETS) next.drop(next.size - MAX_PRESETS) else next
    }

    /** Remove [preset] (matched by name and tool). Pure. */
    fun delete(list: List<BrushPreset>, preset: BrushPreset): List<BrushPreset> =
        list.filterNot { it.name == preset.name && it.tool == preset.tool }
}
