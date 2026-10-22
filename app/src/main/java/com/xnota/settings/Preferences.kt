package com.xnota.settings

import com.xnota.core.model.Orientation
import com.xnota.core.model.PageSize
import com.xnota.core.model.PageSizeChoice
import com.xnota.core.model.Rgba
import com.xnota.core.util.NameTemplate
import com.xnota.core.stroke.NibAngle
import com.xnota.core.stroke.Pressure
import com.xnota.core.stroke.StrokeFilter
import org.json.JSONObject

/**
 * Global, document-independent preferences (spec 09 §4). Forgiving load: every
 * field falls back to its default if missing or malformed.
 */
data class Preferences(
    val uiAppearance: String = "system", // "system" (follows OS dark/light) | "dark" | "light" | "oled"
    val materialMode: MaterialColourMode = MaterialColourMode.DUAL,
    val materialSingleSeed: Rgba = DEFAULT_MATERIAL_SINGLE,
    val materialDualSeed: Rgba = DEFAULT_MATERIAL_DUAL,
    val materialStyle: MaterialStyle = MaterialStyle.TONAL_SPOT,
    /** Material contrast level, -1 (reduced) .. 0 (standard) .. 1 (high). */
    val materialContrast: Double = 0.0,
    val materialSurfaceSeed: Rgba = DEFAULT_MATERIAL_SURFACE,
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val toolbarLook: ToolbarLook = ToolbarLook(),
    val hideWindowDecoration: Boolean = false,
    val pageColor: Rgba? = null, // null ⇒ follow theme paper
    val pageTemplatePdf: String? = null,
    val defaultTemplate: String = "color", // "color" | "pdf"
    val defaultPageSize: PageSize = PageSize.A4,
    /** Filename template for new notes; see [com.xnota.core.util.NameTemplate]. */
    val newNoteNameTemplate: String = NameTemplate.DEFAULT,
    val defaultPageOrientation: Orientation = Orientation.PORTRAIT,
    /** Width of a [PageSize.CUSTOM] page, in millimetres. Ignored by every named size. */
    val customPageWidthMm: Double = 210.0,
    /** Height of a [PageSize.CUSTOM] page, in millimetres. Ignored by every named size. */
    val customPageHeightMm: Double = 297.0,
    /** Whether a finger draws (true) or pans (false, default). The stylus always draws. */
    val fingerDraws: Boolean = false,
    /** Panning allowed while zoom is locked: "single" (default) | "double" | "none". */
    val zoomLockPan: String = "single",
    /** Whether holding a freehand ink stroke still snaps it to a recognized shape. */
    val detectShapes: Boolean = true,
    /** Tool the stylus side button activates while held; "none" disables it. */
    val penButtonTool: String = "eraser",
    /** Tool the stylus's *second* side button activates while held (two-button pens, the fork);
     * "none" lets the second button fall back to [penButtonTool]. */
    val penButton2Tool: String = "pan",
    /** Whether the side-button tool also activates during hover (no contact needed); eraser/pan only. */
    val penButtonHover: Boolean = false,
    /** Action mapped to a clean two-finger tap; "none" disables it. The fork ships "undo"
     *  armed by default — the Arabic-first gesture vocabulary the owner asked for. */
    val twoFingerTap: String = "undo",
    /** Action mapped to a clean three-finger tap; "none" disables it. The fork default: "redo". */
    val threeFingerTap: String = "redo",
    /** Action mapped to a stylus barrel double-tap (pens with no side button, e.g. Huawei M-Pencil
     *  whose double-tap arrives as keycode 718); "none" disables it. The fork default: "undo". */
    val stylusDoubleTap: String = "undo",
    /** Action mapped to a momentary stylus side-button click (pens that report the button as a vendor
     *  key press rather than a held state, e.g. HONOR Magic-Pencil whose click arrives as keycode
     *  333); "none" disables it. */
    val stylusButtonTap: String = "none",
    /** Action mapped to the first page-key side button (keycode 92 — Redmi/Xiaomi Smart Pen's
     *  lower button); "none" disables it. The fork default: "toggle_eraser". */
    val stylusButton1Tap: String = "toggle_eraser",
    /** Action mapped to the second page-key side button (keycode 93 — Redmi/Xiaomi Smart Pen's
     *  upper button); "none" disables it. The fork default: "undo". */
    val stylusButton2Tap: String = "undo",
    /** Horizontal margin (px) on each side of the page column; 0 ⇒ fit-width fills the screen. */
    val sideMargin: Double = 16.0,
    /** Whether the hairline outline around every page is left undrawn. */
    val hidePageBorders: Boolean = false,
    /** User zoom floor: when enabled, no zoom path goes below [minZoomPercent]%. */
    val minZoomEnabled: Boolean = false,
    val minZoomPercent: Int = 50,
    /** User zoom ceiling: when enabled, no zoom path goes above [maxZoomPercent]%. */
    val maxZoomEnabled: Boolean = false,
    val maxZoomPercent: Int = 400,
    /** Infinite canvas zoom range, as percentages. Far wider than a page's, since a canvas is
     *  read at both a wall's scale and a hair's. */
    val canvasMinZoomPercent: Int = 2,
    val canvasMaxZoomPercent: Int = 6400,
    /** Long-edge cap (px) for the on-screen page cache; higher holds more of the page ready at deep zoom. */
    val maxCacheResolution: Int = 2048,
    /** Whether wet ink keeps off the front buffer, so every stroke takes the ordinary canvas path. */
    val disableFrontBuffering: Boolean = false,
    /** Open in fullscreen; null ⇒ auto (on unless the display has a camera cutout). */
    val startFullscreen: Boolean? = null,
    /** An imported Helix code theme's file path, or null for the built-in colours. */
    val codeThemePath: String? = null,
    /** The imported code theme's original file name, shown in preferences. */
    val codeThemeName: String? = null,
    /** Language "Paste as Code" assigns to pasted blocks; "plain" pastes unhighlighted. */
    val defaultCodeLanguage: String = "cpp",
    /** Language the format bar's code toggle last applied; "" until a language is picked. */
    val lastCodeLanguage: String = "",
    /** Whether typed markdown markers (# - ** `) convert the text as you write. */
    val markdownInput: Boolean = true,
    /** Whether typing "/" at a word start opens the command menu. */
    val slashCommands: Boolean = true,
    /** Layouts the explorer header's switcher offers, in switcher order. */
    val switcherLayouts: List<ExplorerLayout> = ExplorerLayout.entries,
    val sidebarRecent: Boolean = true,
    val sidebarPinned: Boolean = true,
    val sidebarColours: Boolean = true,
    val sidebarTrash: Boolean = true,
    /** Where Home opens: "top" (the notes folder), "last" (the folder last shown) or "shelves" (the notes folder under Recent and Pinned). */
    val homeOpensTo: String = "top",
    /** How the explorer writes dates: "relative" (2 days ago), "day" (Wed 17:30) or "date" (16 Sep 2026). */
    val dateStyle: String = "day",
    /** Which calendar dates are written in (the fork): "gregorian" (default) or "hijri" (Umm al-Qura,
     *  via java.time.chrono.HijrahDate). Display-only; sorting and file times stay Gregorian. */
    val dateSystem: String = "gregorian",
    /** Digits shown across the UI (the fork): "auto" (Arabic-Indic when the app language is Arabic),
     *  "latin" (0-9) or "arabic" (٠١٢٣٤٥٦٧٨٩). Display-only — nothing written to disk is ever shaped. */
    val digitSystem: String = "auto",
    /** Tapping a file previews it instead of opening it. */
    val tapPreviews: Boolean = false,
    /** Days a deleted item waits in Trash: 0 deletes at once, [TRASH_FOREVER] keeps it until Trash is emptied. */
    val trashDays: Int = 30,
    /** Each folder keeps its own view instead of every folder sharing one. */
    val perFolderViews: Boolean = false,
    val showFolderCounts: Boolean = true,
    val showExtensions: Boolean = false,
    /** Whether an exported PDF gets a bookmark for each heading of the note's text. */
    val pdfHeadingBookmarks: Boolean = true,
    /** Adaptive ink (the fork): how stored ink colours are re-toned to the current paper at
     *  render time — "full" (greys flip, chromatic tones held in a readable band), "achromatic"
     *  (greys only) or "off". The stored colours never change; only what the reader sees. */
    val adaptiveInk: String = "full",
    /** Folder-backup cadence: "never" (default) | "daily" | "weekly". The folder itself is
     *  [com.xnota.settings.Settings.backupRoot], any SAF tree the user granted — a Drive folder
     *  works with no network code in the app at all. */
    val backupEvery: String = "never",
    // ── Ink Engine 2.0 (the fork) ────────────────────────────────────────────────
    /** Position+pressure low-pass behind the ribbon: "ema" (historical per-arc low-pass) or
     *  "one_euro" (the fork's adaptive one-euro filter: jitter-resistant at rest, low-lag while
     *  moving). Reader-level: stored ink never changes, only how it renders. */
    val strokeFilter: String = "ema",
    /** Speed-scaled low-pass lag: fast ink crisp, slow ink steady. Off by default — the
     *  historical ink pipeline is the default until the owner opts in. */
    val adaptiveSmoothing: Boolean = false,
    /** Raw-pressure deadzone fraction in [0, 0.20]; pressures below it read as zero. */
    val pressureDeadzone: Double = 0.0,
    /** Pressure gamma in [0.5, 2.0], applied after the logistic reshape: below 1 hardens
     *  (width arrives sooner), above 1 softens. */
    val pressureGamma: Double = 1.0,
    /** Pressure-only EMA smoothing strength 0–100; position smoothing untouched. */
    val pressureSmoothing: Int = 0,
    /** Calligraphy nib angle in degrees [0, 360), or -1 to follow the travel direction. */
    val nibAngleLockDeg: Double = NibAngle.FOLLOW_TRAVEL,
    /** Highlighter compositing: "multiply" (historical — tints what is under it) or "normal"
     *  (plain alpha, brighter; the only mode that shows on a darker page). */
    val highlighterBlend: String = "multiply",
    /** Join a new stroke onto the previous one when its start lands within the join gap of that
     *  stroke's end with a matching tool and width. Off by default — deliberate strokes only. */
    val joinStrokes: Boolean = false,
    /** Rapid back-to-back strokes merge into one undo unit (within ~350 ms). On by default. */
    val undoCoalesce: Boolean = true,

    /** Version of the preferences payload, so one-off migrations can tell a stale file from a
     *  fresh one; see [PREF_SCHEMA]. */
    val prefSchema: Int = PREF_SCHEMA,
) {
    /**
     * A new note's page size in document pixels. A named size is laid out under
     * [defaultPageOrientation]; a custom one is taken as typed, since the two fields already say
     * which way round the page goes.
     */
    fun newPagePixels(dpi: Int = PageSize.DEFAULT_DPI): Pair<Double, Double> = defaultPageChoice.pixels(dpi)

    /** The default page size, orientation and custom sides as one choice. */
    val defaultPageChoice: PageSizeChoice
        get() = PageSizeChoice(defaultPageSize, defaultPageOrientation, customPageWidthMm, customPageHeightMm)

    fun toJson(): JSONObject = JSONObject()
        .put("ui_appearance", uiAppearance)
        .put("hide_window_decoration", hideWindowDecoration)
        .put("page_color", pageColor?.let { Rgba.toHex(it) } ?: JSONObject.NULL)
        .put("page_template_pdf", pageTemplatePdf ?: JSONObject.NULL)
        .put("default_template", defaultTemplate)
        .put("default_page_size", defaultPageSize.displayName)
        .put("new_note_name_template", newNoteNameTemplate)
        .put("default_page_orientation", defaultPageOrientation.toName())
        .put("custom_page_width_mm", customPageWidthMm)
        .put("custom_page_height_mm", customPageHeightMm)
        .put("finger_draws", fingerDraws)
        .put("zoom_lock_pan", zoomLockPan)
        .put("detect_shapes", detectShapes)
        .put("pen_button_tool", penButtonTool)
        .put("pen_button_hover", penButtonHover)
        .put("two_finger_tap", twoFingerTap)
        .put("three_finger_tap", threeFingerTap)
        .put("stylus_double_tap", stylusDoubleTap)
        .put("stylus_button_tap", stylusButtonTap)
        .put("stylus_button_1_tap", stylusButton1Tap)
        .put("stylus_button_2_tap", stylusButton2Tap)
        .put("side_margin", sideMargin)
        .put("hide_page_borders", hidePageBorders)
        .put("min_zoom_enabled", minZoomEnabled)
        .put("min_zoom_percent", minZoomPercent)
        .put("max_zoom_enabled", maxZoomEnabled)
        .put("max_zoom_percent", maxZoomPercent)
        .put("canvas_min_zoom_percent", canvasMinZoomPercent)
        .put("canvas_max_zoom_percent", canvasMaxZoomPercent)
        .put("max_cache_resolution", maxCacheResolution)
        .put("disable_front_buffering", disableFrontBuffering)
        .apply {
            if (materialMode != MaterialColourMode.SYSTEM) put("material_mode", materialMode.id)
            if (materialSingleSeed != DEFAULT_MATERIAL_SINGLE) put("material_single_seed", Rgba.toHex(materialSingleSeed))
            if (materialDualSeed != DEFAULT_MATERIAL_DUAL) put("material_dual_seed", Rgba.toHex(materialDualSeed))
            // Keep the active colours readable by versions with a shared accent seed.
            when (materialMode) {
                MaterialColourMode.SYSTEM -> Unit
                MaterialColourMode.SINGLE -> put("material_seed", Rgba.toHex(materialSingleSeed))
                MaterialColourMode.DUAL -> put("material_seed", Rgba.toHex(materialDualSeed)).put("material_dual_tone", true)
            }
            if (materialStyle != MaterialStyle.TONAL_SPOT) put("material_style", materialStyle.id)
            if (materialContrast != 0.0) put("material_contrast", materialContrast)
            if (materialMode == MaterialColourMode.DUAL || materialSurfaceSeed != DEFAULT_MATERIAL_SURFACE) {
                put("material_surface_seed", Rgba.toHex(materialSurfaceSeed))
            }
            if (cornerStyle != CornerStyle.ROUNDED) put("corner_style", cornerStyle.id)
            if (toolbarLook.position != ToolbarPosition.TOP) put("toolbar_position", toolbarLook.position.id)
            if (toolbarLook.size != ToolbarSize.REGULAR) put("toolbar_size", toolbarLook.size.id)
            if (toolbarLook.floating) put("toolbar_floating", true)
            startFullscreen?.let { put("start_fullscreen", it) }
            codeThemePath?.let { put("code_theme_path", it) }
            codeThemeName?.let { put("code_theme_name", it) }
            put("default_code_language", defaultCodeLanguage)
            lastCodeLanguage.takeIf { it.isNotEmpty() }?.let { put("last_code_language", it) }
            put("markdown_input", markdownInput)
            put("slash_commands", slashCommands)
        }
        .put("switcher_layouts", org.json.JSONArray().apply { switcherLayouts.forEach { put(it.id) } })
        .put("sidebar_recent", sidebarRecent)
        .put("sidebar_pinned", sidebarPinned)
        .put("sidebar_colours", sidebarColours)
        .put("sidebar_trash", sidebarTrash)
        .put("home_opens_to", homeOpensTo)
        .put("date_style", dateStyle)
        .put("date_system", dateSystem)
        .put("digit_system", digitSystem)
        .put("tap_previews", tapPreviews)
        .put("trash_days", trashDays)
        .put("per_folder_views", perFolderViews)
        .put("show_folder_counts", showFolderCounts)
        .put("show_extensions", showExtensions)
        .put("pdf_heading_bookmarks", pdfHeadingBookmarks)
        .put("pen_button_2_tool", penButton2Tool)
        .put("adaptive_ink", adaptiveInk)
        .put("backup_every", backupEvery)
        .put("stroke_filter", strokeFilter)
        .put("adaptive_smoothing", adaptiveSmoothing)
        .put("pressure_deadzone", pressureDeadzone)
        .put("pressure_gamma", pressureGamma)
        .put("pressure_smoothing", pressureSmoothing)
        .put("nib_angle_lock", nibAngleLockDeg)
        .put("highlighter_blend", highlighterBlend)
        .put("join_strokes", joinStrokes)
        .put("undo_coalesce", undoCoalesce)
        .put("pref_schema", prefSchema)

    companion object {
        val DEFAULT_ACCENT = Rgba(0, 230, 118, 255)
        val DEFAULT_MATERIAL_SINGLE = Rgba(244, 67, 54, 255)
        val DEFAULT_MATERIAL_DUAL = Rgba(154, 124, 66, 255)
        val DEFAULT_MATERIAL_SURFACE = Rgba(61, 117, 230, 255)

        /** [trashDays] for keeping deleted items until Trash is emptied by hand. */
        const val TRASH_FOREVER = -1

        /** Preferences payload version. v2 is the fork's gesture-and-pen defaults: a file written
         *  before the fork (or by upstream) carries no `pref_schema`, and its untouched "none"
         *  gesture mappings are upgraded once to the fork's defaults — after which the user's own
         *  choices are final, whatever they set them to. */
        const val PREF_SCHEMA = 2

        /** Settable range of the min/max zoom limits, in percent (within the hard zoom bounds). */
        const val ZOOM_LIMIT_MIN_PCT = 20
        const val ZOOM_LIMIT_MAX_PCT = 1600

        fun fromJson(o: JSONObject?): Preferences {
            if (o == null) return Preferences()
            val appearance = o.optString("ui_appearance", "system")
                .let { if (it == "dark" || it == "light" || it == "oled") it else "system" }
            val template = o.optString("default_template", "color").let { if (it == "pdf") "pdf" else "color" }
            val zoomLockPan = o.optString("zoom_lock_pan", "single").let { if (it == "double" || it == "none") it else "single" }
            val tapActions = setOf("none", "undo", "redo", "toggle_pan", "toggle_eraser", "toggle_previous")
            fun tapAction(key: String) = o.optString(key, "none").let { if (it in tapActions) it else "none" }
            // One-time upgrade to the fork's gesture defaults (PREF_SCHEMA v2): every mapping still
            // sitting at "none" in a pre-fork file flips to its armed default. A file that already
            // carries the schema, or a mapping the user actually chose, passes through untouched.
            val schema = o.optInt("pref_schema", 1)
            fun migrated(key: String, to: String) =
                if (schema >= PREF_SCHEMA || o.optString(key, "none") != "none") tapAction(key) else to
            val legacySeed = Rgba.fromHex(o.optString("material_seed"))
            val legacyDual = o.optBoolean("material_dual_tone", false)
            val materialMode = MaterialColourMode.fromId(o.optString("material_mode")) ?: when {
                legacyDual -> MaterialColourMode.DUAL
                legacySeed != null -> MaterialColourMode.SINGLE
                else -> MaterialColourMode.SYSTEM
            }
            val legacyDualAccent = legacySeed ?: Rgba.fromHex(o.optString("accent_color")) ?: DEFAULT_ACCENT
            return Preferences(
                uiAppearance = appearance,
                materialMode = materialMode,
                materialSingleSeed = Rgba.fromHex(o.optString("material_single_seed"))
                    ?: legacySeed?.takeIf { !legacyDual } ?: DEFAULT_MATERIAL_SINGLE,
                materialDualSeed = Rgba.fromHex(o.optString("material_dual_seed"))
                    ?: if (legacyDual) legacyDualAccent else DEFAULT_MATERIAL_DUAL,
                materialStyle = MaterialStyle.fromId(o.optString("material_style")),
                materialContrast = o.optDouble("material_contrast", 0.0).takeIf { it in -1.0..1.0 } ?: 0.0,
                materialSurfaceSeed = Rgba.fromHex(o.optString("material_surface_seed"))
                    ?: if (legacyDual && !o.has("material_mode")) Rgba(33, 150, 243, 255) else DEFAULT_MATERIAL_SURFACE,
                cornerStyle = CornerStyle.fromId(o.optString("corner_style")),
                toolbarLook = ToolbarLook(
                    position = ToolbarPosition.fromId(o.optString("toolbar_position")),
                    size = ToolbarSize.fromId(o.optString("toolbar_size")),
                    floating = o.optBoolean("toolbar_floating", false),
                ),
                hideWindowDecoration = o.optBoolean("hide_window_decoration", false),
                pageColor = if (o.isNull("page_color")) null else Rgba.fromHex(o.optString("page_color")),
                pageTemplatePdf = if (o.isNull("page_template_pdf")) null else o.optString("page_template_pdf").ifEmpty { null },
                defaultTemplate = template,
                defaultPageSize = PageSize.fromName(o.optString("default_page_size", "A4")),
                newNoteNameTemplate = o.optString("new_note_name_template", NameTemplate.DEFAULT)
                    .ifBlank { NameTemplate.DEFAULT },
                defaultPageOrientation = Orientation.fromName(o.optString("default_page_orientation", "portrait")),
                customPageWidthMm = o.optDouble("custom_page_width_mm", 210.0)
                    .coerceIn(PageSize.CUSTOM_MIN_MM, PageSize.CUSTOM_MAX_MM),
                customPageHeightMm = o.optDouble("custom_page_height_mm", 297.0)
                    .coerceIn(PageSize.CUSTOM_MIN_MM, PageSize.CUSTOM_MAX_MM),
                fingerDraws = o.optBoolean("finger_draws", false),
                zoomLockPan = zoomLockPan,
                detectShapes = o.optBoolean("detect_shapes", true),
                penButtonTool = o.optString("pen_button_tool", "eraser").ifEmpty { "eraser" },
                penButton2Tool = o.optString("pen_button_2_tool", "pan"),
                penButtonHover = o.optBoolean("pen_button_hover", false),
                twoFingerTap = migrated("two_finger_tap", "undo"),
                threeFingerTap = migrated("three_finger_tap", "redo"),
                stylusDoubleTap = migrated("stylus_double_tap", "undo"),
                stylusButtonTap = tapAction("stylus_button_tap"),
                stylusButton1Tap = migrated("stylus_button_1_tap", "toggle_eraser"),
                stylusButton2Tap = migrated("stylus_button_2_tap", "undo"),
                sideMargin = o.optDouble("side_margin", 16.0).coerceIn(0.0, 80.0),
                hidePageBorders = o.optBoolean("hide_page_borders", false),
                minZoomEnabled = o.optBoolean("min_zoom_enabled", false),
                minZoomPercent = o.optInt("min_zoom_percent", 50).coerceIn(ZOOM_LIMIT_MIN_PCT, ZOOM_LIMIT_MAX_PCT),
                maxZoomEnabled = o.optBoolean("max_zoom_enabled", false),
                maxZoomPercent = o.optInt("max_zoom_percent", 400).coerceIn(ZOOM_LIMIT_MIN_PCT, ZOOM_LIMIT_MAX_PCT),
                canvasMinZoomPercent = o.optInt("canvas_min_zoom_percent", 2).coerceIn(1, 100),
                canvasMaxZoomPercent = o.optInt("canvas_max_zoom_percent", 6400).coerceIn(200, 100000),
                maxCacheResolution = o.optInt("max_cache_resolution", 2048).coerceIn(1024, 4096),
                disableFrontBuffering = o.optBoolean("disable_front_buffering", false),
                startFullscreen = if (o.has("start_fullscreen")) o.getBoolean("start_fullscreen") else null,
                codeThemePath = o.optString("code_theme_path").ifEmpty { null },
                codeThemeName = o.optString("code_theme_name").ifEmpty { null },
                defaultCodeLanguage = o.optString("default_code_language", "cpp")
                    .lowercase().trim().ifEmpty { "cpp" },
                lastCodeLanguage = o.optString("last_code_language").lowercase().trim(),
                markdownInput = o.optBoolean("markdown_input", true),
                slashCommands = o.optBoolean("slash_commands", true),
                switcherLayouts = o.optJSONArray("switcher_layouts")?.let { a ->
                    (0 until a.length()).mapNotNull { ExplorerLayout.fromId(a.optString(it)) }.distinct()
                } ?: ExplorerLayout.entries,
                sidebarRecent = o.optBoolean("sidebar_recent", true),
                sidebarPinned = o.optBoolean("sidebar_pinned", true),
                sidebarColours = o.optBoolean("sidebar_colours", true),
                sidebarTrash = o.optBoolean("sidebar_trash", true),
                homeOpensTo = o.optString("home_opens_to", "top").let { if (it == "last" || it == "shelves") it else "top" },
                dateStyle = o.optString("date_style", "day").let { if (it == "relative" || it == "date") it else "day" },
                dateSystem = o.optString("date_system", "gregorian").let { if (it == "hijri") it else "gregorian" },
                digitSystem = o.optString("digit_system", "auto").let { if (it == "latin" || it == "arabic") it else "auto" },
                tapPreviews = o.optBoolean("tap_previews", false),
                trashDays = o.optInt("trash_days", 30).let { if (it == TRASH_FOREVER || it in 0..365) it else 30 },
                perFolderViews = o.optBoolean("per_folder_views", false),
                showFolderCounts = o.optBoolean("show_folder_counts", true),
                showExtensions = o.optBoolean("show_extensions", false),
                pdfHeadingBookmarks = o.optBoolean("pdf_heading_bookmarks", true),
                adaptiveInk = o.optString("adaptive_ink", "full")
                    .let { if (it == "off" || it == "achromatic") it else "full" },
                backupEvery = o.optString("backup_every", "never")
                    .let { if (it == "daily" || it == "weekly") it else "never" },
                strokeFilter = o.optString("stroke_filter", "ema")
                    .let { if (it == "one_euro") it else "ema" },
                adaptiveSmoothing = o.optBoolean("adaptive_smoothing", true),
                pressureDeadzone = o.optDouble("pressure_deadzone", 0.0)
                    .coerceIn(0.0, Pressure.DEADZONE_MAX),
                pressureGamma = o.optDouble("pressure_gamma", 1.0)
                    .coerceIn(Pressure.GAMMA_MIN, Pressure.GAMMA_MAX),
                pressureSmoothing = o.optInt("pressure_smoothing", 0).coerceIn(0, 100),
                nibAngleLockDeg = o.optDouble("nib_angle_lock", NibAngle.FOLLOW_TRAVEL)
                    .let { if (it >= 0.0) it % 360.0 else NibAngle.FOLLOW_TRAVEL },
                highlighterBlend = o.optString("highlighter_blend", "multiply")
                    .let { if (it == "normal") it else "multiply" },
                joinStrokes = o.optBoolean("join_strokes", false),
                undoCoalesce = o.optBoolean("undo_coalesce", true),
                prefSchema = schema.coerceAtLeast(PREF_SCHEMA),
            )
        }
    }
}
