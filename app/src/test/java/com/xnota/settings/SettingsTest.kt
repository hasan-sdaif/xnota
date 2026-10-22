package com.xnota.settings

import com.xnota.core.model.Orientation
import com.xnota.core.model.PageSize
import com.xnota.core.model.Rgba
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolbarLayout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test fun emptyJsonYieldsDefaults() {
        val s = Settings.fromJson(JSONObject())
        assertEquals(15, s.toolbarColors.size)
        assertEquals(5, s.toolbarColorCount)
        assertEquals(0, s.activeColor)
        assertEquals(1.0, s.renderScale, 1e-9)
        assertFalse(s.sidebarVisible)
        assertEquals(PageSize.A4, s.prefs.defaultPageSize)
        assertEquals("system", s.prefs.uiAppearance)
        assertEquals(com.xnota.canvas.ViewSettings(), s.viewDefaults)
    }

    @Test fun viewDefaultsRoundTrip() {
        val original = Settings(
            viewDefaults = com.xnota.canvas.ViewSettings(
                mode = com.xnota.canvas.ViewingMode.DOUBLE,
                invert = 100,
                keepImages = true,
                scrollbar = true,
            ),
        )
        val back = Settings.fromJson(original.toJson())
        assertEquals(original.viewDefaults, back.viewDefaults)
    }

    @Test fun legacyPdfDarkModeSeedsTheViewDefaults() {
        // Settings written before the View menu's Global tab: the old checkboxes migrate.
        val legacy = JSONObject().put(
            "prefs",
            JSONObject().put("pdf_dark_mode", true).put("pdf_keep_image_colors", true),
        )
        val s = Settings.fromJson(legacy)
        assertEquals(100, s.viewDefaults.invert)
        assertTrue(s.viewDefaults.keepImages)
        // Once written back, the migrated defaults persist on their own.
        val back = Settings.fromJson(s.toJson())
        assertEquals(100, back.viewDefaults.invert)
        assertTrue(back.viewDefaults.keepImages)
    }

    @Test fun roundTripPreservesValues() {
        val original = Settings(
            tools = mapOf(Tool.PEN to com.xnota.core.tools.ToolConfig(5.0, false, 0.2, 0.1, Rgba(1, 2, 3, 255))),
            toolbarColors = listOf(Rgba(0, 230, 118), Rgba(1, 1, 1), Rgba(2, 2, 2), Rgba(3, 3, 3), Rgba(4, 4, 4)),
            activeColor = 2,
            renderScale = 1.5,
            sidebarVisible = true,
            prefs = Preferences(
                uiAppearance = "light",
                defaultPageSize = PageSize.LETTER,
                defaultPageOrientation = Orientation.LANDSCAPE,
                pageColor = Rgba(20, 20, 20),
                hidePageBorders = true,
            ),
        )
        val back = Settings.fromJson(original.toJson())
        assertEquals(5.0, back.configFor(Tool.PEN).baseWidth, 1e-9)
        assertFalse(back.configFor(Tool.PEN).pressureEnabled)
        assertEquals(2, back.activeColor)
        assertEquals(1.5, back.renderScale, 1e-9)
        assertTrue(back.sidebarVisible)
        assertEquals("light", back.prefs.uiAppearance)
        assertEquals(PageSize.LETTER, back.prefs.defaultPageSize)
        assertEquals(Orientation.LANDSCAPE, back.prefs.defaultPageOrientation)
        assertEquals(Rgba(20, 20, 20, 255), back.prefs.pageColor)
        assertTrue(back.prefs.hidePageBorders)
    }

    @Test fun theMarkupToolsModeAndIntensityRoundTrip() {
        val markup = com.xnota.core.tools.ToolConfig(markupMode = com.xnota.core.tools.MarkupMode.SQUIGGLY, markupIntensity = 0.8)
        val back = Settings.fromJson(Settings(tools = mapOf(Tool.MARKUP to markup)).toJson())
        assertEquals(com.xnota.core.tools.MarkupMode.SQUIGGLY, back.configFor(Tool.MARKUP).markupMode)
        assertEquals(0.8, back.configFor(Tool.MARKUP).markupIntensity, 1e-9)
        val wild = Settings(tools = mapOf(Tool.MARKUP to markup)).toJson()
        wild.getJSONObject("tools").getJSONObject("markup").put("markup_intensity", 7.0)
        assertEquals(1.0, Settings.fromJson(wild).configFor(Tool.MARKUP).markupIntensity, 1e-9)
    }

    @Test fun theEraserTakesTextMarkupsUnlessSwitchedOff() {
        assertTrue(Settings().configFor(Tool.ERASER).eraseMarkups)
        val off = Settings().configFor(Tool.ERASER).copy(eraseMarkups = false)
        val json = Settings(tools = mapOf(Tool.ERASER to off)).toJson()
        assertFalse(Settings.fromJson(json).configFor(Tool.ERASER).eraseMarkups)
        // Settings saved before the switch existed read as on.
        json.getJSONObject("tools").getJSONObject("eraser").remove("erase_markups")
        assertTrue(Settings.fromJson(json).configFor(Tool.ERASER).eraseMarkups)
    }

    @Test fun scribbleToEraseIsOnUnlessSwitchedOff() {
        assertTrue(Settings().configFor(Tool.ERASER).scribbleErase)
        val off = Settings().configFor(Tool.ERASER).copy(scribbleErase = false)
        val json = Settings(tools = mapOf(Tool.ERASER to off)).toJson()
        assertFalse(Settings.fromJson(json).configFor(Tool.ERASER).scribbleErase)
        // Settings saved before the switch existed read as on.
        json.getJSONObject("tools").getJSONObject("eraser").remove("scribble_erase")
        assertTrue(Settings.fromJson(json).configFor(Tool.ERASER).scribbleErase)
    }

    @Test fun everyTapGestureMappingRoundTrips() {
        val prefs = Preferences(
            twoFingerTap = "undo",
            threeFingerTap = "redo",
            stylusDoubleTap = "toggle_eraser",
            stylusButtonTap = "toggle_pan",
            stylusButton1Tap = "toggle_previous",
            stylusButton2Tap = "undo",
        )
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals("undo", back.twoFingerTap)
        assertEquals("redo", back.threeFingerTap)
        assertEquals("toggle_eraser", back.stylusDoubleTap)
        assertEquals("toggle_pan", back.stylusButtonTap)
        assertEquals("toggle_previous", back.stylusButton1Tap)
        assertEquals("undo", back.stylusButton2Tap)
    }

    @Test fun frontBufferingIsOnUntilItIsTurnedOff() {
        // Settings written before the switch existed, which is every install that has one.
        assertFalse(Settings.fromJson(JSONObject()).prefs.disableFrontBuffering)
        val back = Settings.fromJson(Settings(prefs = Preferences(disableFrontBuffering = true)).toJson()).prefs
        assertTrue(back.disableFrontBuffering)
    }

    @Test fun headingBookmarksAreOnUntilTurnedOff() {
        assertTrue(Settings.fromJson(JSONObject()).prefs.pdfHeadingBookmarks)
        val back = Settings.fromJson(Settings(prefs = Preferences(pdfHeadingBookmarks = false)).toJson()).prefs
        assertFalse(back.pdfHeadingBookmarks)
    }

    @Test fun customPageSizeRoundTripsAndSizesANewPage() {
        val prefs = Preferences(
            defaultPageSize = PageSize.CUSTOM,
            defaultPageOrientation = Orientation.LANDSCAPE,
            customPageWidthMm = 254.0,
            customPageHeightMm = 127.0,
        )
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals(PageSize.CUSTOM, back.defaultPageSize)
        assertEquals(254.0, back.customPageWidthMm, 1e-9)
        assertEquals(127.0, back.customPageHeightMm, 1e-9)
        // Taken as typed: the landscape chip does not swap a custom page's sides.
        val (w, h) = back.newPagePixels(150)
        assertEquals(1500.0, w, 1e-6)
        assertEquals(750.0, h, 1e-6)
    }

    @Test fun aNamedSizeStillFollowsTheOrientation() {
        val prefs = Preferences(defaultPageSize = PageSize.LEGAL, defaultPageOrientation = Orientation.LANDSCAPE)
        val (w, h) = prefs.newPagePixels(150)
        assertEquals(PageSize.mmToPx(355.6, 150), w, 1e-6)
        assertEquals(PageSize.mmToPx(215.9, 150), h, 1e-6)
    }

    @Test fun anOutOfRangeCustomSideIsPulledBackIn() {
        val o = JSONObject().put(
            "prefs",
            JSONObject().put("custom_page_width_mm", 9000.0).put("custom_page_height_mm", 0.0),
        )
        val back = Settings.fromJson(o).prefs
        assertEquals(PageSize.CUSTOM_MAX_MM, back.customPageWidthMm, 1e-9)
        assertEquals(PageSize.CUSTOM_MIN_MM, back.customPageHeightMm, 1e-9)
    }

    @Test fun malformedAppearanceFallsBackToSystem() {
        val o = JSONObject().put("prefs", JSONObject().put("ui_appearance", "rainbow"))
        assertEquals("system", Settings.fromJson(o).prefs.uiAppearance)
    }

    @Test fun toolbarColorsPaddedToFifteen() {
        val o = JSONObject().put(
            "toolbar_colors",
            org.json.JSONArray().put(org.json.JSONArray().put(0).put(0).put(0).put(255)),
        )
        assertEquals(15, Settings.fromJson(o).toolbarColors.size)
    }

    @Test fun toolbarColorCountDefaultsToFive() {
        assertEquals(5, Settings.fromJson(JSONObject()).toolbarColorCount)
    }

    @Test fun toolbarColorCountRoundTripsAndClamps() {
        assertEquals(15, Settings.fromJson(Settings(toolbarColorCount = 15).toJson()).toolbarColorCount)
        assertEquals(1, Settings.fromJson(Settings(toolbarColorCount = 0).toJson()).toolbarColorCount)
        assertEquals(15, Settings.fromJson(Settings(toolbarColorCount = 99).toJson()).toolbarColorCount)
    }

    @Test fun rememberColorDedupesAndCaps() {
        var s = Settings()
        repeat(30) { s = s.rememberColor(Rgba(it, it, it)) }
        assertEquals(24, s.recentColors.size)
        s = s.rememberColor(Rgba(5, 5, 5))
        assertEquals(Rgba(5, 5, 5, 255), s.recentColors.first())
        assertEquals(24, s.recentColors.size)
    }

    @Test fun swatchesStartAtTheirDefaultsAndWriteNothing() {
        val s = Settings.fromJson(JSONObject())
        SwatchRole.entries.forEach { assertEquals(it.defaults, s.swatchesFor(it)) }
        assertEquals(false, s.toJson().has("swatches"))
    }

    @Test fun aRecolouredSwatchRoundTripsAndLeavesOtherRolesAlone() {
        val s = Settings().withSwatch(SwatchRole.LINES, 2, Rgba(1, 2, 3))
        val back = Settings.fromJson(s.toJson())
        assertEquals(Rgba(1, 2, 3), back.swatchesFor(SwatchRole.LINES)[2])
        assertEquals(SwatchRole.LINES.defaults[0], back.swatchesFor(SwatchRole.LINES)[0])
        assertEquals(SwatchRole.PAPER.defaults, back.swatchesFor(SwatchRole.PAPER))
    }

    @Test fun aShortSavedSwatchListIsPaddedFromTheDefaults() {
        val o = JSONObject().put(
            "swatches",
            JSONObject().put("accent", org.json.JSONArray().put(org.json.JSONArray().put(9).put(9).put(9).put(255))),
        )
        val accent = Settings.fromJson(o).swatchesFor(SwatchRole.ACCENT)
        assertEquals(SwatchRole.COUNT, accent.size)
        assertEquals(Rgba(9, 9, 9), accent[0])
        assertEquals(SwatchRole.ACCENT.defaults[4], accent[4])
    }

    @Test fun recolouringAMissingSwatchChangesNothing() {
        val s = Settings()
        assertEquals(s, s.withSwatch(SwatchRole.PAPER, SwatchRole.COUNT, Rgba(1, 1, 1)))
    }

    @Test fun pageColorNullByDefault() {
        assertNull(Settings.fromJson(JSONObject()).prefs.pageColor)
    }

    @Test fun newNoteStyleEmptyByDefaultAndUnwritten() {
        val s = Settings.fromJson(JSONObject())
        assertTrue(s.newNoteStyle.isEmpty)
        assertFalse(s.toJson().has("new_note_style"))
    }

    @Test fun newNoteStyleRoundTrips() {
        val style = com.xnota.core.model.PageStyle(
            pageColor = Rgba(255, 250, 230),
            template = "0123456789abcdef",
            patternColor = Rgba(100, 120, 140, 80),
            spacing = 48.0,
            accentColor = Rgba(200, 10, 10, 150),
            params = mapOf("rows" to 7.0),
            colors = mapOf("frame" to Rgba(1, 2, 3)),
        )
        val back = Settings.fromJson(Settings(newNoteStyle = style).toJson())
        assertEquals(style, back.newNoteStyle)
    }

    @Test fun newNoteStylePartialFieldsStayNull() {
        val style = com.xnota.core.model.PageStyle(template = com.xnota.core.model.PagePattern.LINES.id)
        val back = Settings.fromJson(Settings(newNoteStyle = style).toJson())
        assertEquals(style, back.newNoteStyle)
        assertNull(back.newNoteStyle.pageColor)
        assertNull(back.newNoteStyle.spacing)
    }

    @Test fun newCanvasBackgroundNullByDefaultAndUnwritten() {
        val s = Settings.fromJson(JSONObject())
        assertNull(s.newCanvasBackground)
        assertFalse(s.toJson().has("new_canvas_background"))
    }

    @Test fun newCanvasBackgroundRoundTrips() {
        val bg = com.xnota.core.infinite.CanvasBackground(
            pattern = com.xnota.core.model.PagePattern.DOTS,
            patternColor = Rgba(40, 60, 90, 120),
            spacing = 52.0,
            paperColor = Rgba(250, 244, 226),
        )
        val back = Settings.fromJson(Settings(newCanvasBackground = bg).toJson())
        assertEquals(bg, back.newCanvasBackground)
    }

    @Test fun newCanvasBackgroundKeepsThemePaperWhenUnset() {
        val bg = com.xnota.core.infinite.CanvasBackground(pattern = com.xnota.core.model.PagePattern.NONE)
        val back = Settings.fromJson(Settings(newCanvasBackground = bg).toJson())
        assertEquals(bg, back.newCanvasBackground)
        assertNull(back.newCanvasBackground?.paperColor)
    }

    @Test fun fingerDrawAutoCheckedDefaultsFalse() {
        assertFalse(Settings.fromJson(JSONObject()).fingerDrawAutoChecked)
    }

    @Test fun fingerDrawAutoCheckedRoundTrips() {
        val back = Settings.fromJson(Settings(fingerDrawAutoChecked = true).toJson())
        assertTrue(back.fingerDrawAutoChecked)
    }

    @Test fun toolbarLayoutDefaultsWhenAbsent() {
        assertEquals(ToolbarLayout.DEFAULT, Settings.fromJson(JSONObject()).toolbarLayout)
    }

    @Test fun toolbarLayoutRoundTrips() {
        val custom = ToolbarLayout.DEFAULT.toggleVisible(2, 0).addSection()
        val back = Settings.fromJson(Settings(toolbarLayout = custom).toJson())
        assertEquals(custom, back.toolbarLayout)
    }

    @Test fun tapGesturesDefaultArmed() {
        // XNota ships undo/redo armed by default; a fresh install has no settings file at all.
        val p = Preferences.fromJson(JSONObject())
        assertEquals("undo", p.twoFingerTap)
        assertEquals("redo", p.threeFingerTap)
    }

    @Test fun tapGesturesUpgradeOnceOnly() {
        // A pre-XNota settings file (no pref_schema) with untouched "none" mappings upgrades
        // once to the armed defaults; an explicit user choice of "none" afterwards is final.
        val legacy = JSONObject().put("two_finger_tap", "none").put("three_finger_tap", "none")
        val upgraded = Preferences.fromJson(legacy)
        assertEquals("undo", upgraded.twoFingerTap)
        assertEquals("redo", upgraded.threeFingerTap)
        val chosen = Preferences.fromJson(upgraded.toJson().put("two_finger_tap", "none"))
        assertEquals("none", chosen.twoFingerTap)
    }

    @Test fun tapGesturesRoundTrip() {
        val back = Preferences.fromJson(
            Preferences(twoFingerTap = "undo", threeFingerTap = "toggle_eraser").toJson(),
        )
        assertEquals("undo", back.twoFingerTap)
        assertEquals("toggle_eraser", back.threeFingerTap)
    }

    @Test fun tapGestureMalformedFallsBackToNone() {
        val o = JSONObject().put("two_finger_tap", "explode")
        assertEquals("none", Preferences.fromJson(o).twoFingerTap)
    }

    @Test fun startFullscreenNullByDefaultAndUnwritten() {
        assertNull(Preferences.fromJson(JSONObject()).startFullscreen)
        assertFalse(Preferences().toJson().has("start_fullscreen"))
    }

    @Test fun classicPaletteKeysAreDroppedOnLoad() {
        val o = JSONObject().put("accent_color", "#ff8a1e").put("oled_palette_style", "classic").put("dark_palette_style", "classic")
        val json = Preferences.fromJson(o).toJson()
        for (key in listOf("accent_color", "system_palette_style", "dark_palette_style", "light_palette_style", "oled_palette_style")) {
            assertFalse(key, json.has(key))
        }
    }

    @Test fun freshInstallKeepsSystemModeWithTheEmeraldSeeds() {
        // A fresh install reads no file at all: the constructor's own defaults, "system" mode
        // with the emerald seeds ready. (The v4 identity reset applies to files, not defaults.)
        val fresh = Preferences()
        assertEquals(MaterialColourMode.SYSTEM, fresh.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, fresh.materialDualSeed)
        // The 1.7 emerald identity: neon-green accent seed over a teal surface seed.
        assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, fresh.materialSingleSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, fresh.materialSurfaceSeed)
        assertEquals(Rgba(0, 230, 118), fresh.materialDualSeed)
        assertEquals(Rgba(15, 91, 74), fresh.materialSurfaceSeed)
        // The vibrant scheme style and the floating toolbar are the redesigned defaults too.
        assertEquals(MaterialStyle.VIBRANT, fresh.materialStyle)
        assertTrue(fresh.toolbarLook.floating)
        assertEquals("shelves", fresh.homeOpensTo)
        for (key in listOf("material_mode", "material_seed", "material_single_seed", "material_dual_seed", "material_surface_seed")) {
            assertFalse(fresh.toJson().has(key))
        }
    }

    @Test fun anyPreIdentityFileYieldsTheWholeMaterialSetOnce() {
        // v4 (the 1.8 lesson): 1.7.0's default-only switch stayed invisible behind explicit picks,
        // so a pre-v4 file — whatever it carried, a gold preset in single tone, a blue surface,
        // a wallpaper-following mode — hands the whole material set to the emerald identity once.
        val pre = JSONObject()
            .put("pref_schema", 3)
            .put("material_mode", "single")
            .put("material_seed", "#9A7C42")
            .put("material_single_seed", "#9A7C42")
            .put("material_surface_seed", "#3D75E6")
            .put("material_style", "tonal_spot")
        val upgraded = Preferences.fromJson(pre)
        assertEquals(MaterialColourMode.DUAL, upgraded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, upgraded.materialSingleSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, upgraded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, upgraded.materialSurfaceSeed)
        assertEquals(MaterialStyle.VIBRANT, upgraded.materialStyle)
        // The reset sticks: the saved file (schema now v4) reloads to the identity.
        val saved = Preferences.fromJson(upgraded.toJson())
        assertEquals(upgraded, saved)
        // And a post-identity pick is fresh and explicit: it survives from v4 onward.
        val repicked = Preferences.fromJson(upgraded.toJson()
            .put("material_mode", "single").put("material_single_seed", "#40C4FF"))
        assertEquals(MaterialColourMode.SINGLE, repicked.materialMode)
        assertEquals(Rgba(64, 196, 255), repicked.materialSingleSeed)
    }

    @Test fun preRedesignFileUpgradesOnceToEmeraldIdentity() {
        // A settings file as the user's device carries it after 1.6.1: dual mode with the old default
        // seeds written verbatim (the dual-mode save writes them unconditionally), schema v2, the
        // old default home and strip toolbar — everything still at a pre-1.7 default upgrades once.
        val pre = JSONObject()
            .put("pref_schema", 2)
            .put("material_mode", "dual")
            .put("material_seed", "#9A7C42")
            .put("material_dual_tone", true)
            .put("material_surface_seed", "#3D75E6")
            .put("home_opens_to", "top")
        val upgraded = Preferences.fromJson(pre)
        assertEquals(MaterialColourMode.DUAL, upgraded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, upgraded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, upgraded.materialSurfaceSeed)
        assertEquals(MaterialStyle.VIBRANT, upgraded.materialStyle)
        assertEquals("shelves", upgraded.homeOpensTo)
        assertTrue(upgraded.toolbarLook.floating)
        // The upgrade sticks: reloading the saved file keeps the identity (schema is now v3).
        val saved = Preferences.fromJson(upgraded.toJson())
        assertEquals(upgraded, saved)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, saved.materialDualSeed)
        assertEquals("shelves", saved.homeOpensTo)
        // And post-1.7 choices are final: the old gold pair re-picked through the presets stays,
        // as does a conscious return to the bare top-folder home.
        val repicked = Preferences.fromJson(upgraded.toJson()
            .put("material_seed", "#9A7C42").put("material_surface_seed", "#3D75E6").put("home_opens_to", "top"))
        assertEquals(Rgba(154, 124, 66), repicked.materialDualSeed)
        assertEquals(Rgba(61, 117, 230), repicked.materialSurfaceSeed)
        assertEquals("top", repicked.homeOpensTo)
    }

    @Test fun legacySingleToneYieldsToTheIdentityOnce() {
        // A pre-v4 file in single tone: the v4 reset takes the whole set (mode and seeds),
        // so the chrome and the accent land on the brand identity together.
        val loaded = Preferences.fromJson(JSONObject().put("material_seed", "#2196f3"))
        assertEquals(MaterialColourMode.DUAL, loaded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialSingleSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        assertEquals(loaded, Preferences.fromJson(loaded.toJson()))
    }

    @Test fun legacyDualToneYieldsToTheIdentityOnce() {
        val loaded = Preferences.fromJson(JSONObject()
            .put("material_dual_tone", true).put("material_seed", "#800000")
            .put("material_surface_seed", "#000080"))
        assertEquals(MaterialColourMode.DUAL, loaded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialSingleSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        assertEquals(loaded, Preferences.fromJson(loaded.toJson()))
    }

    @Test fun legacyDualToneYieldsItsFallbackColoursToTheIdentity() {
        // The v4 reset is unconditional for pre-v4 files: even the old dual-tone fallback pair
        // (a bare accent over the legacy blue surface) lands on the brand identity, and so does
        // a legacy accent_color pick — the exact case the user's 1.7.0 install proved invisible.
        val json = JSONObject().put("material_dual_tone", true)
        val loaded = Preferences.fromJson(json)
        assertEquals(MaterialColourMode.DUAL, loaded.materialMode)
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        val classicAccent = Preferences.fromJson(json.put("accent_color", "#123456"))
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, classicAccent.materialDualSeed)
        assertEquals(classicAccent, Preferences.fromJson(classicAccent.toJson()))
    }

    @Test fun customMaterialOptionsDefaultForgivingly() {
        for (value in listOf<Any>(JSONObject.NULL, "unknown", "medium", "high", 123, JSONObject())) {
            val p = Preferences.fromJson(JSONObject().put("material_style", value).put("material_contrast", value))
            assertEquals(MaterialStyle.VIBRANT, p.materialStyle)
            assertFalse(p.toJson().has("material_contrast"))
        }
        val defaults = Preferences.fromJson(JSONObject())
        assertEquals(MaterialStyle.VIBRANT, defaults.materialStyle)
        assertFalse(defaults.toJson().has("material_style"))
        assertFalse(defaults.toJson().has("material_contrast"))
        // An explicit pre-1.7 style pick yields to the v4 identity like every other pre-v4 pick;
        // re-picked from v4 onward it is explicit and survives.
        assertEquals(MaterialStyle.VIBRANT, Preferences.fromJson(JSONObject().put("material_style", "tonal_spot")).materialStyle)
        assertEquals(MaterialStyle.TONAL_SPOT, Preferences.fromJson(JSONObject().put("pref_schema", 4).put("material_style", "tonal_spot")).materialStyle)
    }

    @Test fun materialOptionsDefaultForgivingly() {
        for (value in listOf<Any>(JSONObject.NULL, "unknown", 123, JSONObject())) {
            val loaded = Preferences.fromJson(JSONObject()
                .put("material_mode", value).put("material_dual_tone", value)
                .put("material_seed", value).put("material_single_seed", value)
                .put("material_dual_seed", value).put("material_surface_seed", value))
            // Junk reads as no pick — and a file with no v4 stamp carries no pick at all,
            // so the identity reset lands the mode and seeds on the defaults.
            assertEquals(MaterialColourMode.DUAL, loaded.materialMode)
            assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, loaded.materialSingleSeed)
            assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, loaded.materialDualSeed)
            assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, loaded.materialSurfaceSeed)
        }
    }

    @Test fun materialModesKeepSeparateColoursAcrossSwitchesAndRestarts() {
        fun reload(p: Preferences) = Preferences.fromJson(p.toJson())
        val single = reload(Preferences(materialMode = MaterialColourMode.SINGLE)
            .copy(materialSingleSeed = Rgba(4, 5, 6)))
        val firstDual = reload(single.copy(materialMode = MaterialColourMode.DUAL))
        assertEquals(Preferences.DEFAULT_MATERIAL_DUAL, firstDual.materialDualSeed)
        assertEquals(Preferences.DEFAULT_MATERIAL_SURFACE, firstDual.materialSurfaceSeed)
        val dual = reload(firstDual.copy(materialDualSeed = Rgba(7, 8, 9), materialSurfaceSeed = Rgba(10, 11, 12)))
        val backToSingle = reload(dual.copy(materialMode = MaterialColourMode.SINGLE))
        assertEquals(Rgba(4, 5, 6), backToSingle.materialSingleSeed)
        val changedSingle = reload(backToSingle.copy(materialSingleSeed = Rgba(13, 14, 15)))
        val system = reload(changedSingle.copy(materialMode = MaterialColourMode.SYSTEM))
        assertFalse(system.toJson().has("material_seed"))
        assertFalse(system.toJson().has("material_dual_tone"))
        val backToDual = reload(system.copy(materialMode = MaterialColourMode.DUAL))
        assertEquals(Rgba(7, 8, 9), backToDual.materialDualSeed)
        assertEquals(Rgba(10, 11, 12), backToDual.materialSurfaceSeed)
        assertEquals(Rgba(13, 14, 15), backToDual.materialSingleSeed)
    }

    @Test fun firstSingleToneDoesNotInheritDualTone() {
        val dual = Preferences(materialMode = MaterialColourMode.DUAL,
            materialDualSeed = Rgba(4, 5, 6), materialSurfaceSeed = Rgba(7, 8, 9))
        val single = Preferences.fromJson(dual.copy(materialMode = MaterialColourMode.SINGLE).toJson())
        assertEquals(Preferences.DEFAULT_MATERIAL_SINGLE, single.materialSingleSeed)
        assertEquals(dual.materialDualSeed, single.materialDualSeed)
        assertEquals(dual.materialSurfaceSeed, single.materialSurfaceSeed)
    }

    @Test fun materialColoursAndStylesSurviveAllModesAndAppearanceSwitches() {
        for (style in MaterialStyle.entries) {
            for (mode in MaterialColourMode.entries) {
                val p = Preferences(materialMode = mode, materialSingleSeed = Rgba(128, 0, 0),
                    materialDualSeed = Rgba(0, 128, 0), materialSurfaceSeed = Rgba(0, 0, 128), materialStyle = style)
                assertEquals(p, Preferences.fromJson(p.toJson()))
                for (appearance in listOf("light", "dark", "oled", "system")) {
                    val changed = p.copy(uiAppearance = appearance)
                    assertEquals(changed, Preferences.fromJson(changed.toJson()))
                }
            }
        }
    }

    @Test fun materialContrastRoundTripsAndRejectsOutOfRange() {
        for (level in listOf(-1.0, -0.3, 0.5, 1.0)) {
            val p = Preferences(materialContrast = level)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        assertEquals(0.0, Preferences.fromJson(JSONObject().put("material_contrast", 1.5)).materialContrast, 0.0)
    }

    @Test fun cornerStyleRoundTripsAndDefaultsToRounded() {
        for (style in CornerStyle.entries) {
            val p = Preferences(cornerStyle = style)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        assertEquals(CornerStyle.ROUNDED, Preferences.fromJson(JSONObject().put("corner_style", "blobby")).cornerStyle)
    }

    @Test fun toolbarLookRoundTripsAndDefaults() {
        for (size in ToolbarSize.entries) for (position in ToolbarPosition.entries) for (floating in listOf(false, true)) {
            val p = Preferences(toolbarLook = ToolbarLook(position, size, floating))
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
        val junk = JSONObject().put("toolbar_size", "huge").put("toolbar_position", "middle")
        assertEquals(ToolbarLook(), Preferences.fromJson(junk).toolbarLook)
        assertFalse(Preferences().toJson().has("toolbar_size"))
        assertFalse(Preferences().toJson().has("toolbar_position"))
        assertFalse(Preferences().toJson().has("toolbar_floating"))
    }

    @Test fun defaultPresetsRoundTripInEveryMode() {
        for (mode in MaterialColourMode.entries) {
            val p = Preferences(materialMode = mode)
            assertEquals(p, Preferences.fromJson(p.toJson()))
        }
    }

    @Test fun startFullscreenRoundTrips() {
        assertEquals(false, Preferences.fromJson(Preferences(startFullscreen = false).toJson()).startFullscreen)
        assertEquals(true, Preferences.fromJson(Preferences(startFullscreen = true).toJson()).startFullscreen)
    }

    @Test fun markdownInputDefaultsOnAndRoundTrips() {
        assertTrue(Preferences().markdownInput)
        assertTrue(Settings.fromJson(JSONObject()).prefs.markdownInput)
        val off = Settings(prefs = Preferences(markdownInput = false))
        assertFalse(Settings.fromJson(off.toJson()).prefs.markdownInput)
    }

    @Test fun slashCommandsDefaultsOnAndRoundTrips() {
        assertTrue(Preferences().slashCommands)
        assertTrue(Settings.fromJson(JSONObject()).prefs.slashCommands)
        val off = Settings(prefs = Preferences(slashCommands = false))
        assertFalse(Settings.fromJson(off.toJson()).prefs.slashCommands)
    }

    @Test fun theTwoTypingPreferencesAreIndependent() {
        val s = Settings(prefs = Preferences(markdownInput = false, slashCommands = true))
        val back = Settings.fromJson(s.toJson()).prefs
        assertFalse(back.markdownInput)
        assertTrue(back.slashCommands)
    }


    @Test fun theShapeToolsChipFillAndHeadsRoundTrip() {
        val shape = com.xnota.core.tools.ShapeConfig(
            preset = "arrow", fill = true, fillColor = com.xnota.core.model.Rgba(1, 2, 3),
            headStart = com.xnota.core.shapes.Head.FILLED_DIAMOND, headEnd = com.xnota.core.shapes.Head.OPEN, n = 6, shapePage = 0,
        )
        val back = Settings.fromJson(Settings(shapeConfig = shape).toJson()).shapeConfig
        assertEquals(shape, back)
    }

    @Test fun thePerfectSwitchRoundTripsAndStartsOff() {
        val on = com.xnota.core.tools.ShapeConfig(perfect = true)
        assertTrue(Settings.fromJson(Settings(shapeConfig = on).toJson()).shapeConfig.perfect)
        val older = JSONObject().put("tools", JSONObject().put("shape", JSONObject().put("shape", "rectangle")))
        assertFalse(Settings.fromJson(older).shapeConfig.perfect)
    }

    @Test fun anOldShapeSettingPicksItsChip() {
        val legacy = JSONObject().put("tools", JSONObject().put("shape", JSONObject().put("shape", "arrow").put("fill", true)))
        val c = Settings.fromJson(legacy).shapeConfig
        assertEquals("arrow", c.preset)
        assertEquals(com.xnota.core.shapes.Head.OPEN, c.headEnd)
        assertTrue(c.fill)
    }

    @Test fun aChosenConnectorAndItsPageRoundTrip() {
        val shape = com.xnota.core.tools.ShapeConfig().choose(com.xnota.core.shapes.ConnectorPresets.byKey("uml_aggregation_elbow")!!)
            .copy(connectorPage = 1)
        val back = Settings.fromJson(Settings(shapeConfig = shape).toJson()).shapeConfig
        assertEquals(shape, back)
        assertEquals("uml_aggregation_elbow", back.connector!!.key)
    }

    @Test fun favoritesPinnedUrisAndSmartFoldersRoundTrip() {
        val original = Settings(
            favorites = listOf("content://tree/primary%3ANotes/document/notes-a.xnote", "content://tree/primary%3ANotes/document/sub"),
            pinnedUris = listOf("content://tree/primary%3ANotes/document/notes-b.xnote"),
            smartFolders = listOf(SmartFolder("Work", "report", "work"), SmartFolder("All")),
        )
        val back = Settings.fromJson(original.toJson())
        assertEquals(original.favorites, back.favorites)
        assertEquals(original.pinnedUris, back.pinnedUris)
        assertEquals(original.smartFolders, back.smartFolders)
    }

    @Test fun explorerListSettingsDropJunkAndKeepOrder() {
        val junk = JSONObject()
            .put("favorites", org.json.JSONArray().put("a").put("").put("a").put(true))
            .put("pinned_uris", org.json.JSONArray().put("z").put("z"))
            .put("smart_folders", org.json.JSONArray().put(JSONObject().put("name", " n ").put("query", " q ")))
        val back = Settings.fromJson(junk)
        assertEquals(listOf("a"), back.favorites)
        assertEquals(listOf("z"), back.pinnedUris)
        assertEquals(listOf(SmartFolder("n", "q", null)), back.smartFolders)
    }

    @Test fun readerBrightnessRoundTripsAndClamps() {
        val prefs = Preferences(readerBrightness = 0.55)
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals(0.55, back.readerBrightness, 1e-9)
        // A wild stored value is pulled back into the slider's range, never above untouched paper.
        val wild = JSONObject().put("prefs", JSONObject().put("reader_brightness", 0.05))
        assertEquals(Preferences.READER_BRIGHTNESS_MIN, Settings.fromJson(wild).prefs.readerBrightness, 1e-9)
        assertEquals(1.0, Settings.fromJson(JSONObject()).prefs.readerBrightness, 1e-9)
    }

    @Test fun pageNumberModesRoundTripAndDropJunk() {
        val prefs = Preferences(pageNumbers = 2)
        val back = Settings.fromJson(Settings(prefs = prefs).toJson()).prefs
        assertEquals(2, back.pageNumbers)
        val centre = JSONObject().put("prefs", JSONObject().put("page_numbers", 1))
        assertEquals(1, Settings.fromJson(centre).prefs.pageNumbers)
        // A mode the chips never offered falls back to off, and a fresh file starts off.
        val junk = JSONObject().put("prefs", JSONObject().put("page_numbers", 5))
        assertEquals(0, Settings.fromJson(junk).prefs.pageNumbers)
        assertEquals(0, Settings.fromJson(JSONObject()).prefs.pageNumbers)
    }

    @Test fun searchHistoryRoundTripsAndIsSanitized() {
        val original = Settings(searchHistory = listOf("قلم", "ink", "notes"))
        val back = Settings.fromJson(original.toJson())
        assertEquals(listOf("قلم", "ink", "notes"), back.searchHistory)
        // A stored array of junk reads back trimmed and deduped, exactly what the chips then show.
        val junk = JSONObject().put("search_history", org.json.JSONArray().put(" a ").put("").put("a").put(true))
        assertEquals(listOf("a"), Settings.fromJson(junk).searchHistory)
        assertEquals(emptyList<String>(), Settings.fromJson(JSONObject()).searchHistory)
    }
}
