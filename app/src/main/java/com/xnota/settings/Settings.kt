package com.xnota.settings

import com.xnota.core.infinite.CanvasBackground
import com.xnota.core.model.PagePattern
import com.xnota.core.model.PageTemplates
import com.xnota.core.model.PageStyle
import com.xnota.core.model.Rgba
import com.xnota.core.pal.FontFace
import com.xnota.core.text.FlowDefaults
import com.xnota.core.text.FlowMargins
import com.xnota.core.text.TableBorders
import com.xnota.core.text.TableDefaults
import com.xnota.core.text.TableStyle
import com.xnota.core.tools.EraseMode
import com.xnota.core.tools.InkPalette
import com.xnota.core.tools.MarkupMode
import com.xnota.core.tools.ShapeConfig
import com.xnota.core.shapes.ShapeKind
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolConfig
import com.xnota.core.tools.ToolDefaults
import com.xnota.core.tools.ToolbarLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * How the in-app explorer orders entries. The chosen key sorts within each group (folders first,
 * then files); [ExplorerView.descending] flips the direction.
 */
enum class ExplorerSortKey(val id: String) {
    NAME("name"),
    MODIFIED("modified"),
    CREATED("created"),
    SIZE("size");

    companion object {
        fun fromId(id: String): ExplorerSortKey = entries.firstOrNull { it.id == id } ?: MODIFIED
    }
}

/** A folder pinned to the home sidebar: its tree document uri and the name to show. */
data class PinnedFolder(val uri: String, val name: String)
/** All persistent non-document state (spec 09 §2). */
data class Settings(
    /** Global View-menu defaults; per-note [com.xnota.canvas.ViewOverrides] shadow them. */
    val viewDefaults: com.xnota.canvas.ViewSettings = com.xnota.canvas.ViewSettings(),
    val tools: Map<Tool, ToolConfig> = emptyMap(),
    val shapeConfig: ShapeConfig = ShapeConfig(),
    val toolbarColors: List<Rgba> = InkPalette.presets,
    val toolbarColorCount: Int = 5,
    val toolbarLayout: ToolbarLayout = ToolbarLayout.DEFAULT,
    /** The infinite canvas's own bar. A separate layout because the two hold different items. */
    val canvasToolbarLayout: ToolbarLayout = ToolbarLayout.CANVAS_DEFAULT,
    val activeColor: Int = 0,
    /** The tool armed when the app was last paused, re-armed on the next launch. */
    val lastTool: Tool = Tool.DEFAULT,
    val recentColors: List<Rgba> = emptyList(),
    /** The colour circles of each [SwatchRole] the user recoloured; the rest keep their defaults. */
    val swatches: Map<SwatchRole, List<Rgba>> = emptyMap(),
    /** Persisted SAF tree URI for the in-app file explorer's root folder, or null. */
    val browseRoot: String? = null,
    /** Persisted SAF tree URI for the backup folder (any Drive/device folder the user granted), or null. */
    val backupRoot: String? = null,
    /** When the last folder backup landed (epoch ms), or null if never. State, not a preference. */
    val lastBackupAt: Long? = null,
    /** Whether the next launch opens the home screen (true) or the last-open note (false). */
    val startOnHome: Boolean = true,
    val sidebarVisible: Boolean = false,
    /** On wide screens, whether the home sidebar is collapsed to its icon rail. */
    val backstageRail: Boolean = false,
    /** xnota: which physical edge the wide-screen navigation hugs —
     *  "auto" (follow the app language's reading direction), "left", "right" or "bottom".
     *  Unknown values fall back to "auto"; the upstream build ignores the extra JSON key. */
    val backstageNavSide: String = "auto",
    /** xnota: app display language — "system" (follow the OS), "ar" or "en".
     *  Applied by wrapping the activity's base context; the upstream build ignores the key. */
    val appLanguage: String = "system",
    /** Folders pinned to the home sidebar on this device, in pin order. */
    val pinnedFolders: List<PinnedFolder> = emptyList(),
    /** Notes and folders starred on this device (the fork), as document uri strings in star order. */
    val favorites: List<String> = emptyList(),
    /** Notes and folders pinned to the top of their folder listing (the fork), uri strings in pin order. */
    val pinnedUris: List<String> = emptyList(),
    /** The user's saved searches (the fork), in the order the sidebar lists them. */
    val smartFolders: List<SmartFolder> = emptyList(),
    /** How the explorer shows a folder, for every folder or for those without a view of their own. */
    val explorerView: ExplorerView = ExplorerView(),
    /** Views set in one folder only, keyed by the folder's document key; used when views are per folder. */
    val folderViews: Map<String, ExplorerView> = emptyMap(),
    /** Notes and canvases opened on this device, newest first. */
    val recentDocs: List<RecentDoc> = emptyList(),
    /** The explorer folder last shown, so Home can open there again. */
    val lastFolder: String? = null,
    val renderScale: Double = 1.0,
    /** All Pages style stamped onto every newly created note; empty ⇒ none saved. */
    val newNoteStyle: PageStyle = PageStyle(),
    /** Flow (text tool) defaults stamped onto every newly created note; null ⇒ none saved (the device's factory). */
    val newNoteFlow: FlowDefaults? = null,
    /** Background stamped onto every newly created canvas; null ⇒ none saved. */
    val newCanvasBackground: CanvasBackground? = null,
    /** Size and look the insert-table dialog starts from ("Default for new tables"). */
    val newTable: TableDefaults = TableDefaults(),
    val prefs: Preferences = Preferences(),
    /** The fork's named brush presets: a tool's whole style snapshot saved under a name, applied
     *  back onto the tool (or another tool of the same kind) from the brush studio. */
    val brushPresets: List<BrushPreset> = emptyList(),
    /** One-shot flag: the first-run stylus check (which may auto-enable finger-draw) has run. */
    val fingerDrawAutoChecked: Boolean = false,
    // ── Reading, focus & Arabic wiring (the fork) ────────────────────────────────
    /** Seconds each note has been open for reading, by document uri, most-recent last; capped. */
    val readingSeconds: Map<String, Long> = emptyMap(),
    /** Where each note was last scrolled to (x, y), by document uri; capped at [com.xnota.core.util.ScrollMemory.CAP]. */
    val scrollOffsets: Map<String, LongArray> = emptyMap(),
    /** Words written per day ("2026-09-16" → count), for the daily goal; capped at 90 days. */
    val dailyWords: Map<String, Long> = emptyMap(),
    /** Days ("2026-09-16") with at least one note edit, newest last; drives the streak. */
    val editDays: List<String> = emptyList(),
    /** xnota: wall-clock ms when the app was last backgrounded (ON_STOP), feeding the app lock's
     *  re-arm clock — persisted so time away still counts if the process dies while backgrounded.
     *  State, not a preference; 0 = never recorded. */
    val appLockLastArmed: Long = 0L,
) {
    fun configFor(tool: Tool): ToolConfig = tools[tool] ?: ToolDefaults.configFor(tool)

    /** Push a colour to the front of recent colours, de-duped, capped at 24. */
    fun rememberColor(c: Rgba): Settings =
        copy(recentColors = (listOf(c) + recentColors.filter { it != c }).take(24))

    fun swatchesFor(role: SwatchRole): List<Rgba> = swatches[role] ?: role.defaults

    fun withSwatch(role: SwatchRole, index: Int, color: Rgba): Settings {
        val current = swatchesFor(role)
        if (index !in current.indices) return this
        return copy(swatches = swatches + (role to current.toMutableList().also { it[index] = color }))
    }

    fun toJson(): JSONObject {
        val toolsObj = JSONObject()
        for (tool in ToolDefaults.persistedTools) {
            toolsObj.put(tool.id, toolConfigJson(configFor(tool)))
        }
        toolsObj.put("shape", shapeConfigJson(shapeConfig))
        return JSONObject()
            .put("tools", toolsObj)
            .put("toolbar_colors", JSONArray().apply { toolbarColors.forEach { put(rgbaArr(it)) } })
            .put("toolbar_color_count", toolbarColorCount)
            .put("toolbar_layout", toolbarLayoutJson(toolbarLayout))
            .put("canvas_toolbar_layout", toolbarLayoutJson(canvasToolbarLayout))
            .put("active_color", activeColor)
            .put("last_tool", lastTool.id)
            .put("recent_colors", JSONArray().apply { recentColors.forEach { put(rgbaArr(it)) } })
            .apply {
                if (swatches.isNotEmpty()) put("swatches", JSONObject().apply {
                    swatches.forEach { (role, colors) -> put(role.id, JSONArray().apply { colors.forEach { put(rgbaArr(it)) } }) }
                })
            }
            .apply { browseRoot?.let { put("browse_root", it) } }
            .apply { backupRoot?.let { put("backup_root", it) } }
            .apply { lastBackupAt?.let { put("last_backup_at", it) } }
            .put("start_on_home", startOnHome)
            .put("sidebar_visible", sidebarVisible)
            .put("backstage_rail", backstageRail)
            .put("backstage_nav_side", backstageNavSide)
            .put("app_language", appLanguage)
            .put("pinned_folders", JSONArray().apply { pinnedFolders.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) } })
            .put("favorites", JSONArray(favorites))
            .put("pinned_uris", JSONArray(pinnedUris))
            .apply { if (smartFolders.isNotEmpty()) put("smart_folders", SmartFolders.toJson(smartFolders)) }
            .put("explorer_view", explorerView.toJson())
            .put("folder_views", JSONObject().apply { folderViews.forEach { (k, v) -> put(k, v.toJson()) } })
            .put("recent_docs", JSONArray().apply { recentDocs.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name).put("opened", it.opened)) } })
            .apply { lastFolder?.let { put("last_folder", it) } }
            .put("render_scale", renderScale)
            .apply { if (!newNoteStyle.isEmpty) put("new_note_style", pageStyleJson(newNoteStyle)) }
            .apply { newNoteFlow?.let { put("new_note_flow", flowDefaultsJson(it)) } }
            .apply { newCanvasBackground?.let { put("new_canvas_background", canvasBackgroundJson(it)) } }
            .apply { if (!newTable.isFactory) put("new_table", tableDefaultsJson(newTable)) }
            .put("prefs", prefs.toJson())
            .apply { if (brushPresets.isNotEmpty()) put("brush_presets", BrushPresets.toJson(brushPresets)) }
            .put("view_defaults", com.xnota.platform.ViewSettingsJson.write(JSONObject(), viewDefaults))
            .put("finger_draw_auto_checked", fingerDrawAutoChecked)
            .apply { if (readingSeconds.isNotEmpty()) put("reading_seconds", JSONObject().apply { readingSeconds.forEach { (k, v) -> put(k, v) } }) }
            .apply { if (scrollOffsets.isNotEmpty()) put("scroll_offsets", JSONObject().apply { scrollOffsets.forEach { (k, v) -> put(k, JSONArray().put(v[0]).put(v.getOrElse(1) { 0L })) } }) }
            .apply { if (dailyWords.isNotEmpty()) put("daily_words", JSONObject().apply { dailyWords.forEach { (k, v) -> put(k, v) } }) }
            .apply { if (editDays.isNotEmpty()) put("edit_days", JSONArray(editDays)) }
            .put("app_lock_last_armed", appLockLastArmed)
    }

    companion object {
        /** xnota: valid [backstageNavSide] values. */
        val NAV_SIDES = setOf("auto", "left", "right", "bottom")

        /** xnota: valid [appLanguage] values. */
        val APP_LANGUAGES = setOf("system", "ar", "en")

        fun fromJson(o: JSONObject): Settings {
            val toolsObj = o.optJSONObject("tools")
            val tools = HashMap<Tool, ToolConfig>()
            if (toolsObj != null) {
                for (tool in ToolDefaults.persistedTools) {
                    toolsObj.optJSONObject(tool.id)?.let { tools[tool] = toolConfig(it, tool) }
                }
            }
            val shape = toolsObj?.optJSONObject("shape")?.let { shapeConfig(it) } ?: ShapeConfig()

            val colors = rgbaList(o.optJSONArray("toolbar_colors")).toMutableList()
            while (colors.size < InkPalette.MAX_SWATCHES) colors.add(InkPalette.presets[colors.size])

            return Settings(
                viewDefaults = o.optJSONObject("view_defaults")
                    ?.let { com.xnota.platform.ViewSettingsJson.read(it) }
                    ?: legacyViewDefaults(o.optJSONObject("prefs")),
                tools = tools,
                shapeConfig = shape,
                toolbarColors = colors.take(InkPalette.MAX_SWATCHES),
                toolbarColorCount = o.optInt("toolbar_color_count", 5).coerceIn(1, InkPalette.MAX_SWATCHES),
                toolbarLayout = toolbarLayout(o.optJSONObject("toolbar_layout")),
                canvasToolbarLayout = toolbarLayout(
                    o.optJSONObject("canvas_toolbar_layout"),
                    ToolbarLayout.CANVAS_ITEMS,
                    ToolbarLayout.CANVAS_DEFAULT,
                ),
                activeColor = o.optInt("active_color", 0).coerceIn(0, InkPalette.MAX_SWATCHES - 1),
                // Only tools the toolbar can arm come back; a transient one (e.g. TEXT_BOX) would
                // leave the bar showing a tool the user never picked.
                lastTool = Tool.fromId(o.optString("last_tool", ""))
                    ?.takeIf { it in Tool.wheelOrder } ?: Tool.DEFAULT,
                recentColors = rgbaList(o.optJSONArray("recent_colors")).take(24),
                swatches = swatches(o.optJSONObject("swatches")),
                browseRoot = o.optString("browse_root", "").ifEmpty { null },
                backupRoot = o.optString("backup_root", "").ifEmpty { null },
                lastBackupAt = if (o.has("last_backup_at")) o.optLong("last_backup_at") else null,
                startOnHome = o.optBoolean("start_on_home", true),
                sidebarVisible = o.optBoolean("sidebar_visible", false),
                backstageRail = o.optBoolean("backstage_rail", false),
                backstageNavSide = o.optString("backstage_nav_side", "auto").let { if (it in NAV_SIDES) it else "auto" },
                appLanguage = o.optString("app_language", "system").let { if (it in APP_LANGUAGES) it else "system" },
                pinnedFolders = pinnedFolders(o.optJSONArray("pinned_folders")),
                favorites = stringList(o.optJSONArray("favorites")),
                pinnedUris = stringList(o.optJSONArray("pinned_uris")),
                smartFolders = SmartFolders.of(o.optJSONArray("smart_folders")),
                explorerView = o.optJSONObject("explorer_view")?.let { ExplorerView.fromJson(it) } ?: ExplorerView(
                    sortKey = ExplorerSortKey.fromId(o.optString("explorer_sort_key", "modified")),
                    descending = o.optBoolean("explorer_sort_descending", true),
                ),
                folderViews = o.optJSONObject("folder_views")?.let { v -> v.keys().asSequence().associateWith { ExplorerView.fromJson(v.optJSONObject(it)) } }.orEmpty(),
                recentDocs = recentDocs(o.optJSONArray("recent_docs")),
                lastFolder = o.optString("last_folder", "").ifEmpty { null },
                renderScale = o.optDouble("render_scale", 1.0),
                newNoteStyle = pageStyle(o.optJSONObject("new_note_style")),
                newNoteFlow = o.optJSONObject("new_note_flow")?.let { flowDefaults(it) },
                newCanvasBackground = canvasBackground(o.optJSONObject("new_canvas_background")),
                newTable = tableDefaults(o.optJSONObject("new_table")),
                prefs = Preferences.fromJson(o.optJSONObject("prefs")),
                brushPresets = BrushPresets.of(o.optJSONArray("brush_presets")),
                fingerDrawAutoChecked = o.optBoolean("finger_draw_auto_checked", false),
                readingSeconds = longMap(o.optJSONObject("reading_seconds"), com.xnota.core.util.ReadingTime.CAP),
                scrollOffsets = scrollOffsets(o.optJSONObject("scroll_offsets")),
                dailyWords = longMap(o.optJSONObject("daily_words"), com.xnota.core.util.DailyWords.CAP),
                editDays = stringList(o.optJSONArray("edit_days")).takeLast(EDIT_DAYS_CAP),
                appLockLastArmed = o.optLong("app_lock_last_armed", 0L).let { if (it > 0L) it else 0L },
            )
        }

        /** Days of edit history kept (the streak needs 2). */
        private const val EDIT_DAYS_CAP = 120

        /** A JSON object of string keys to long values, newest-entry-last by insertion order, capped. */
        private fun longMap(o: JSONObject?, cap: Int): Map<String, Long> {
            if (o == null) return emptyMap()
            val out = LinkedHashMap<String, Long>(o.length())
            for (k in o.keys()) {
                val v = o.opt(k)
                if (v is Long || v is Int || v is Number) out[k] = o.optLong(k)
            }
            while (out.size > cap) out.remove(out.keys.first())
            return out
        }

        /** A JSON object of uri keys to [x, y] pairs, capped at the scroll memory's cap. */
        private fun scrollOffsets(o: JSONObject?): Map<String, LongArray> {
            if (o == null) return emptyMap()
            val out = LinkedHashMap<String, LongArray>(o.length())
            for (k in o.keys()) {
                val a = o.optJSONArray(k) ?: continue
                if (a.length() < 2) continue
                out[k] = longArrayOf(a.optLong(0), a.optLong(1))
            }
            while (out.size > com.xnota.core.util.ScrollMemory.CAP) out.remove(out.keys.first())
            return out
        }

        /** Each saved role padded or cut to [SwatchRole.COUNT], filling gaps from its defaults. */
        private fun swatches(o: JSONObject?): Map<SwatchRole, List<Rgba>> {
            if (o == null) return emptyMap()
            return SwatchRole.entries.mapNotNull { role ->
                val saved = rgbaList(o.optJSONArray(role.id))
                if (saved.isEmpty()) null else role to List(SwatchRole.COUNT) { saved.getOrNull(it) ?: role.defaults[it] }
            }.toMap()
        }

        private fun recentDocs(a: JSONArray?): List<RecentDoc> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri", "").ifEmpty { return@mapNotNull null }
                RecentDoc(uri, o.optString("name", ""), o.optLong("opened", 0L))
            }
        }

        private fun pinnedFolders(a: JSONArray?): List<PinnedFolder> {
            if (a == null) return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri", "").ifEmpty { return@mapNotNull null }
                PinnedFolder(uri, o.optString("name", ""))
            }
        }

        /** A persisted list of uri strings: blanks and non-strings drop out, duplicates fold. */
        private fun stringList(a: JSONArray?): List<String> {
            if (a == null) return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until a.length()) {
                val s = a.opt(i) as? String ?: continue
                if (s.isEmpty()) continue
                if (s !in out) out.add(s)
            }
            return out
        }

        /** Settings written before the View menu's Global tab: the old PDF dark-mode
         *  checkboxes seed the global invert / keep-images defaults. */
        private fun legacyViewDefaults(prefs: JSONObject?): com.xnota.canvas.ViewSettings {
            if (prefs == null) return com.xnota.canvas.ViewSettings()
            return com.xnota.canvas.ViewSettings(
                invert = if (prefs.optBoolean("pdf_dark_mode", false)) 100 else 0,
                keepImages = prefs.optBoolean("pdf_keep_image_colors", false),
            )
        }

        private fun rgbaArr(c: Rgba) = JSONArray().put(c.r).put(c.g).put(c.b).put(c.a)

        private fun rgba(a: JSONArray?): Rgba? =
            a?.let { Rgba.fromList((0 until it.length()).map { i -> it.optInt(i, 0) }) }

        private fun pageStyleJson(s: PageStyle) = JSONObject()
            .apply { s.pageColor?.let { put("page_color", rgbaArr(it)) } }
            .apply { s.template?.let { put(if (it == PageTemplates.NONE || PageTemplates.isBuiltIn(it)) "pattern" else "template", it) } }
            .apply { s.patternColor?.let { put("pattern_color", rgbaArr(it)) } }
            .apply { s.spacing?.let { put("spacing", it) } }
            .apply { s.accentColor?.let { put("accent_color", rgbaArr(it)) } }
            .apply { s.params?.takeIf { it.isNotEmpty() }?.let { m -> put("params", JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }) } }
            .apply { s.colors?.takeIf { it.isNotEmpty() }?.let { m -> put("colors", JSONObject().apply { m.forEach { (k, v) -> put(k, rgbaArr(v)) } }) } }

        private fun pageStyle(o: JSONObject?): PageStyle {
            if (o == null) return PageStyle()
            val params = o.optJSONObject("params")?.let { j ->
                j.keys().asSequence().mapNotNull { k -> j.optDouble(k).takeIf { !it.isNaN() }?.let { k to it } }.toMap()
            }
            val colors = o.optJSONObject("colors")?.let { j ->
                j.keys().asSequence().mapNotNull { k -> rgba(j.optJSONArray(k))?.let { k to it } }.toMap()
            }
            return PageStyle(
                pageColor = rgba(o.optJSONArray("page_color")),
                template = o.optString("template", "").takeIf { it.isNotEmpty() }
                    ?: PagePattern.fromId(o.optString("pattern", ""))?.id,
                patternColor = rgba(o.optJSONArray("pattern_color")),
                spacing = if (o.has("spacing")) o.optDouble("spacing") else null,
                accentColor = rgba(o.optJSONArray("accent_color")),
                params = params?.takeIf { it.isNotEmpty() },
                colors = colors?.takeIf { it.isNotEmpty() },
            )
        }

        /** Written in full, like the canvas file's own copy: a canvas has no level to inherit
         *  from, so every field carries a real value. */
        private fun canvasBackgroundJson(b: CanvasBackground) = JSONObject()
            .put("pattern", b.pattern.id)
            .put("pattern_color", rgbaArr(b.patternColor))
            .put("spacing", b.spacing)
            .apply { b.paperColor?.let { put("paper_color", rgbaArr(it)) } }

        private fun canvasBackground(o: JSONObject?): CanvasBackground? {
            if (o == null) return null
            val d = CanvasBackground()
            return CanvasBackground(
                pattern = PagePattern.fromId(o.optString("pattern", "")) ?: d.pattern,
                patternColor = rgba(o.optJSONArray("pattern_color")) ?: d.patternColor,
                spacing = o.optDouble("spacing", d.spacing),
                paperColor = rgba(o.optJSONArray("paper_color")),
            )
        }

        private fun flowDefaultsJson(d: FlowDefaults) = JSONObject()
            .put("face", d.face.id)
            .put("mono_face", d.monoFace.id)
            .put("size_pt", d.sizePt)
            .apply { d.color?.let { put("color", rgbaArr(it)) } }
            .put("margin_left_mm", d.margins.leftMm)
            .put("margin_top_mm", d.margins.topMm)
            .put("margin_right_mm", d.margins.rightMm)
            .put("margin_bottom_mm", d.margins.bottomMm)

        private fun flowDefaults(o: JSONObject): FlowDefaults {
            val d = FlowDefaults()
            return FlowDefaults(
                face = FontFace(o.optString("face", d.face.id)),
                monoFace = FontFace(o.optString("mono_face", d.monoFace.id)),
                sizePt = o.optDouble("size_pt", d.sizePt),
                color = rgba(o.optJSONArray("color")),
                margins = FlowMargins(
                    leftMm = o.optDouble("margin_left_mm", FlowMargins.DEFAULT_MM),
                    topMm = o.optDouble("margin_top_mm", FlowMargins.DEFAULT_MM),
                    rightMm = o.optDouble("margin_right_mm", FlowMargins.DEFAULT_MM),
                    bottomMm = o.optDouble("margin_bottom_mm", FlowMargins.DEFAULT_MM),
                ),
            )
        }

        private fun tableDefaultsJson(d: TableDefaults) = JSONObject()
            .put("rows", d.rows)
            .put("cols", d.cols)
            .put("padding_pt", d.style.paddingPt)
            .put("line_width_pt", d.style.lineWidthPt)
            .put("borders", d.style.borders.id)
            .put("header_row", d.style.headerRow)
            .put("banded", d.style.banded)
            .apply { d.style.lineColor?.let { put("line_color", rgbaArr(it)) } }
            .apply { d.style.tint?.let { put("tint", rgbaArr(it)) } }

        private fun tableDefaults(o: JSONObject?): TableDefaults {
            if (o == null) return TableDefaults()
            val d = TableDefaults()
            return TableDefaults(
                rows = o.optInt("rows", d.rows).coerceIn(1, TableDefaults.MAX_ROWS),
                cols = o.optInt("cols", d.cols).coerceIn(1, TableDefaults.MAX_COLS),
                style = TableStyle(
                    paddingPt = o.optDouble("padding_pt", d.style.paddingPt),
                    lineColor = rgba(o.optJSONArray("line_color")),
                    lineWidthPt = o.optDouble("line_width_pt", d.style.lineWidthPt),
                    borders = TableBorders.fromId(o.optString("borders", "")),
                    headerRow = o.optBoolean("header_row", false),
                    banded = o.optBoolean("banded", false),
                    tint = rgba(o.optJSONArray("tint")),
                ).clamped(),
            )
        }

        private fun rgbaList(arr: JSONArray?): List<Rgba> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONArray(i) ?: return@mapNotNull null
                Rgba.fromList((0 until a.length()).map { a.optInt(it, 0) })
            }
        }

        internal fun toolConfigJson(c: ToolConfig) = JSONObject()
            .put("base_width", c.baseWidth)
            .put("pressure_enabled", c.pressureEnabled)
            .put("pressure_min_factor", c.pressureMinFactor)
            .put("direction_strength", c.directionStrength)
            .put("speed_strength", c.speedStrength)
            .put("taper_enabled", c.taperEnabled)
            .put("taper_min_factor", c.taperMinFactor)
            .put("neon", c.neon)
            .put("neon_strength", c.neonStrength)
            .put("dash_length", c.dashLength)
            .put("dash_gap", c.dashGap)
            .put("erase_mode", c.eraseMode.id)
            .put("switch_back_after_erase", c.switchBackAfterErase)
            .put("erase_markups", c.eraseMarkups)
            .put("scribble_erase", c.scribbleErase)
            .put("switch_back_after_select", c.switchBackAfterSelect)
            .put("snap_grid", c.snapGrid)
            .put("snap_shapes", c.snapShapes)
            .put("snap_rotate", c.snapRotate)
            .put("straight_line", c.straightLine)
            .put("scale", c.scale)
            .put("highlighter_alpha", c.highlighterAlpha)
            .put("highlighter_inverse", c.highlighterInverse)
            .put("markup_mode", c.markupMode.id)
            .put("markup_intensity", c.markupIntensity)
            .put("rgba", rgbaArr(c.rgba))
            .apply { c.colorOverride?.let { put("color_override", rgbaArr(it)) } }
            // The fork's brush-studio knobs, so a tuned brush survives a restart.
            .put("roughness", c.roughness)
            .put("scatter", c.scatter)
            .put("opacity", c.opacity)

        internal fun toolConfig(o: JSONObject, tool: Tool): ToolConfig {
            val d = ToolDefaults.configFor(tool)
            return ToolConfig(
                baseWidth = o.optDouble("base_width", d.baseWidth),
                pressureEnabled = o.optBoolean("pressure_enabled", d.pressureEnabled),
                pressureMinFactor = o.optDouble("pressure_min_factor", d.pressureMinFactor),
                directionStrength = o.optDouble("direction_strength", d.directionStrength),
                rgba = Rgba.fromList(o.optJSONArray("rgba")?.let { a -> (0 until a.length()).map { a.optInt(it, 0) } }) ?: d.rgba,
                speedStrength = o.optDouble("speed_strength", d.speedStrength),
                taperEnabled = o.optBoolean("taper_enabled", d.taperEnabled),
                taperMinFactor = o.optDouble("taper_min_factor", d.taperMinFactor),
                neon = o.optBoolean("neon", d.neon),
                neonStrength = o.optDouble("neon_strength", d.neonStrength),
                dashLength = o.optDouble("dash_length", d.dashLength),
                dashGap = o.optDouble("dash_gap", d.dashGap),
                eraseMode = EraseMode.fromId(o.optString("erase_mode", d.eraseMode.id)),
                switchBackAfterErase = o.optBoolean("switch_back_after_erase", d.switchBackAfterErase),
                eraseMarkups = o.optBoolean("erase_markups", d.eraseMarkups),
                scribbleErase = o.optBoolean("scribble_erase", d.scribbleErase),
                switchBackAfterSelect = o.optBoolean("switch_back_after_select", d.switchBackAfterSelect),
                snapGrid = o.optBoolean("snap_grid", d.snapGrid),
                snapShapes = o.optBoolean("snap_shapes", d.snapShapes),
                snapRotate = o.optBoolean("snap_rotate", d.snapRotate),
                straightLine = o.optBoolean("straight_line", d.straightLine),
                scale = o.optBoolean("scale", d.scale),
                highlighterAlpha = o.optDouble("highlighter_alpha", d.highlighterAlpha),
                highlighterInverse = o.optBoolean("highlighter_inverse", d.highlighterInverse),
                markupMode = if (o.has("markup_mode")) MarkupMode.fromId(o.optString("markup_mode")) else d.markupMode,
                markupIntensity = o.optDouble("markup_intensity", d.markupIntensity)
                    .coerceIn(ToolConfig.MARKUP_INTENSITY_MIN, 1.0),
                colorOverride = o.optJSONArray("color_override")
                    ?.let { a -> Rgba.fromList((0 until a.length()).map { i -> a.optInt(i, 0) }) }
                    ?: d.colorOverride,
                // The fork's brush-studio knobs; coerced so a corrupt file cannot wedge a tool.
                roughness = o.optDouble("roughness", d.roughness).coerceIn(0.0, 100.0),
                scatter = o.optDouble("scatter", d.scatter).coerceIn(0.0, 100.0),
                opacity = o.optDouble("opacity", d.opacity).coerceIn(ToolConfig.OPACITY_MIN, 1.0),
            )
        }

        private fun shapeConfigJson(c: ShapeConfig) = JSONObject()
            .put("preset", c.preset)
            .put("shape", c.shape.id).put("stroke_width", c.strokeWidth).put("fill", c.fill)
            .put("fill_alpha", c.fillAlpha)
            .apply { c.fillColor?.let { put("fill_color", rgbaArr(it)) } }
            .put("neon", c.neon).put("neon_strength", c.neonStrength)
            .put("dashed", c.dashed).put("dash_length", c.dashLength).put("dash_gap", c.dashGap)
            .apply { c.fillPattern?.let { put("fill_pattern", it) } }
            .put("n", c.n).put("head_start", c.headStart.id).put("head_end", c.headEnd.id)
            .put("perfect", c.perfect)
            .put("shape_page", c.shapePage)
            .put("connector_page", c.connectorPage)

        private fun shapeConfig(o: JSONObject): ShapeConfig {
            // Settings from before chips name only a kind; its chip and that chip's heads stand in.
            val chip = com.xnota.core.shapes.ShapePresets.byKey(o.optString("preset", ""))
                ?: com.xnota.core.shapes.ShapePresets.forKind(ShapeKind.fromId(o.optString("shape", "rectangle")))
            val connector = com.xnota.core.shapes.ConnectorPresets.byKey(o.optString("preset", ""))
            return ShapeConfig(
                preset = connector?.key ?: chip.key,
                strokeWidth = o.optDouble("stroke_width", 3.0),
                fill = o.optBoolean("fill", false),
                fillAlpha = o.optDouble("fill_alpha", ShapeConfig.FILL_ALPHA)
                    .coerceIn(ShapeConfig.FILL_ALPHA_MIN, ShapeConfig.FILL_ALPHA_MAX),
                fillColor = rgba(o.optJSONArray("fill_color")),
                neon = o.optBoolean("neon", false),
                neonStrength = o.optDouble("neon_strength", 0.6),
                dashed = o.optBoolean("dashed", false),
                dashLength = o.optDouble("dash_length", 10.0),
                dashGap = o.optDouble("dash_gap", 8.0),
                fillPattern = o.optString("fill_pattern").takeIf { it.isNotEmpty() },
                n = o.optInt("n", 0),
                perfect = o.optBoolean("perfect", false),
                headStart = com.xnota.core.shapes.Head.fromId(o.optString("head_start", chip.headStart.id)),
                headEnd = com.xnota.core.shapes.Head.fromId(o.optString("head_end", chip.headEnd.id)),
                shapePage = o.optInt("shape_page", com.xnota.core.shapes.ShapePresets.pageOf(chip)),
                connectorPage = o.optInt("connector_page", connector?.let { com.xnota.core.shapes.ConnectorPresets.pageOf(it) } ?: 0),
            )
        }

        private fun toolbarLayoutJson(layout: ToolbarLayout): JSONObject {
            val secArr = JSONArray()
            for (sec in layout.sections) {
                val entryArr = JSONArray()
                for (e in sec.entries) {
                    entryArr.put(JSONObject().put("id", e.item.id).put("visible", e.visible))
                }
                secArr.put(entryArr)
            }
            return JSONObject().put("sections", secArr)
        }

        private fun toolbarLayout(
            o: JSONObject?,
            among: Set<com.xnota.core.tools.ToolbarItem> = ToolbarLayout.NOTE_ITEMS,
            fallback: ToolbarLayout = ToolbarLayout.DEFAULT,
        ): ToolbarLayout {
            if (o == null) return fallback
            val secArr = o.optJSONArray("sections") ?: return fallback
            val raw = ArrayList<List<Pair<String, Boolean>>>()
            for (i in 0 until secArr.length()) {
                val entryArr = secArr.optJSONArray(i) ?: continue
                val entries = ArrayList<Pair<String, Boolean>>()
                for (j in 0 until entryArr.length()) {
                    val e = entryArr.optJSONObject(j) ?: continue
                    entries.add(e.optString("id") to e.optBoolean("visible", true))
                }
                raw.add(entries)
            }
            return ToolbarLayout.fromRaw(raw, among, fallback)
        }
    }
}
