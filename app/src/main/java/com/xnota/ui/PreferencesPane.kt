package com.xnota.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.model.Orientation
import com.xnota.core.model.PageSize
import com.xnota.core.model.Rgba
import com.xnota.settings.ExplorerLayout
import com.xnota.core.tools.InkPalette
import com.xnota.core.tools.ToolbarItem
import com.xnota.core.tools.ToolbarLayout
import com.xnota.core.util.NameTemplate
import com.xnota.core.util.PinHash
import com.xnota.settings.Preferences
import com.xnota.settings.MaterialStyle
import com.xnota.settings.CornerStyle
import com.xnota.settings.ToolbarPosition
import com.xnota.settings.ToolbarSize
import com.xnota.settings.MaterialColourMode
import com.xnota.settings.SwatchRole
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One-tap filename templates offered beside the free-text field. */
private val NAME_TEMPLATE_PRESETS = listOf(
    NameTemplate.DEFAULT,
    "note_YYYY-MM-DD",
    "note_YYYY-MM-DD_HH-mm",
)
private val penButtonOptions = listOf("eraser" to R.string.tool_eraser, "pan" to R.string.tool_pan, "select" to R.string.tool_select, "none" to R.string.none)
private val adaptiveInkOptions = listOf(
    "full" to R.string.pref_adaptive_ink_full,
    "achromatic" to R.string.pref_adaptive_ink_achromatic,
    "off" to R.string.pref_adaptive_ink_off,
)
private val tapGestureOptions = listOf(
    "none" to R.string.none,
    "undo" to R.string.undo,
    "redo" to R.string.redo,
    "toggle_pan" to R.string.toggle_pan,
    "toggle_eraser" to R.string.toggle_eraser,
    "toggle_previous" to R.string.toggle_previous,
)

/**
 * The settings hub's categories: the pane's sections folded into five browsable groups plus All,
 * each chip carrying its line icon. Which sections belong to which category is decided at each
 * section's own gate (see [PreferencesPane]); a non-empty search bypasses the selection.
 */
private enum class SettingsCategory(val labelRes: Int, val icon: ImageVector) {
    ALL(R.string.settings_cat_all, NotaIcons.layoutGrid),
    INTERFACE(R.string.settings_cat_interface, NotaIcons.palette),
    WRITING(R.string.settings_cat_writing, NotaIcons.penTool),
    READING(R.string.settings_cat_reading, NotaIcons.bookOpen),
    DATA(R.string.settings_cat_data, NotaIcons.shield),
    ADVANCED(R.string.settings_cat_advanced, NotaIcons.sliders),
}

/**
 * Preferences as a backstage pane (spec 10 §8). Edits apply live — each change is
 * pushed straight to the [Editor] (and persisted), so theme/page tweaks are seen
 * immediately, including in the surrounding backstage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesPane(
    editor: Editor,
    compact: Boolean,
    sidebarOpen: Boolean,
    onShowSidebar: () -> Unit,
    onBackToHome: () -> Unit,
    onImportCodeTheme: () -> Unit = {},
    onImportFont: () -> Unit = {},
    onPickBackupRoot: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val focusManager = LocalFocusManager.current
    var prefs by remember { mutableStateOf(editor.preferences) }
    fun update(p: Preferences) {
        prefs = p
        editor.applyPreferences(p)
    }
    // Home, explorer and export settings leave the open note alone, so they skip its canvas refresh.
    fun updateHome(p: Preferences) {
        prefs = p
        editor.applyHomePreferences(p)
    }
    var namingColor by remember { mutableStateOf<Rgba?>(null) }
    namingColor?.let { c -> ColorNameDialog(editor, c) { namingColor = null } }
    // The fork's pane search: what's typed filters the sections by their titles and row labels,
    // folded with the note search's own loose Arabic fold so "قلم" finds "القلم" and a diacritic
    // never blocks a match. An empty query shows every section, exactly the pane as it was.
    var prefQuery by remember { mutableStateOf("") }
    val prefCtx = LocalContext.current
    val foldedQuery = prefSearchFold(prefQuery.trim())
    fun sectionShown(vararg tokenRes: Int): Boolean {
        if (foldedQuery.isEmpty()) return true
        return tokenRes.any { prefSearchFold(prefCtx.getString(it)).contains(foldedQuery) }
    }
    // The settings hub's selected category (the chip row under the search field). While a search
    // is active the category is ignored: the query spans every section exactly as it did before
    // the hub, so the pane's search behaviour is untouched.
    var category by remember { mutableStateOf(SettingsCategory.ALL) }
    fun inCategory(cat: SettingsCategory): Boolean = foldedQuery.isNotEmpty() || category == cat
    // Follow out-of-pane preference changes too (the .scm import round-trips a picker).
    LaunchedEffect(editor.prefsVersion) { prefs = editor.preferences }

    val scrollState = rememberScrollState()
    // The hub chip row's own horizontal scroll, separate from the pane's vertical one.
    val categoryRowScroll = rememberScrollState()
    // Which bar the toolbar section is arranging. The two are separate layouts, so the drag state
    // below always belongs to whichever is on screen.
    var canvasTab by remember { mutableStateOf(false) }
    val layout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    // Toolbar drag state, hoisted here so the floating "ghost" chip can be drawn at the pane
    // root (above and outside the scroll, so it is never clipped). Bounds are plain maps read
    // only at drag time; observable state is just the dragged item, finger, and drop slot.
    var dragItem by remember { mutableStateOf<ToolbarItem?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val chipBounds = remember { mutableMapOf<ToolbarItem, Rect>() }
    val sectionBounds = remember { mutableMapOf<Int, Rect>() }
    val sectionGrabBounds = remember { mutableMapOf<Int, Rect>() }
    // Section-drag state (the whole card, by its grip handle); shares the finger/grab with the
    // chip drag since only one gesture runs at a time. The drop slot is an insertion index.
    var dragSection by remember { mutableStateOf<Int?>(null) }
    var sectionDropTarget by remember { mutableStateOf<Int?>(null) }
    var paneTopLeft by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    fun editedLayout(): ToolbarLayout = if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout
    fun applyEdited(next: ToolbarLayout) {
        if (canvasTab) editor.applyCanvasToolbarLayout(next) else editor.applyToolbarLayout(next)
    }
    fun retarget() {
        dropTarget = toolbarDropTarget(editedLayout(), dragFinger, chipBounds, sectionBounds)
    }
    fun retargetSection() {
        sectionDropTarget = toolbarSectionDropTarget(editedLayout(), dragFinger, sectionBounds)
    }
    fun showTab(canvas: Boolean) {
        if (canvasTab == canvas) return
        canvasTab = canvas
        // The bounds maps are keyed by item and the two bars share most items, so stale entries
        // from the other tab would aim a drop at a chip that is no longer there.
        chipBounds.clear()
        sectionBounds.clear()
        sectionGrabBounds.clear()
        dragItem = null
        dragSection = null
        dropTarget = null
        sectionDropTarget = null
    }

    // A tap on empty space dismisses the template field's focus; children consume their own taps.
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            .onGloballyPositioned { paneTopLeft = it.boundsInRoot().topLeft },
    ) {
    Column(Modifier.fillMaxSize()) {
        // Leading button inline with the title, at a constant row height so toggling the sidebar never
        // shifts the settings below: a back arrow to Home on compact, else a hamburger when hidden.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                Tooltip(stringResource(R.string.back_to_home)) {
                    IconButton(onClick = onBackToHome) {
                        DirectionalIcon(NotaIcons.prev, stringResource(R.string.back_to_home), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
            } else if (!sidebarOpen) {
                Tooltip(stringResource(R.string.show_sidebar)) {
                    IconButton(onClick = onShowSidebar) {
                        Icon(NotaIcons.menu, stringResource(R.string.show_sidebar), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(stringResource(R.string.preferences), color = palette.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { update(Preferences()) }) { Text(stringResource(R.string.reset_to_defaults), fontSize = 13.sp) }
        }
        Spacer(Modifier.height(12.dp))
        // The pane's own search: one line that filters every section below it.
        OutlinedTextField(
            value = prefQuery,
            onValueChange = { prefQuery = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_prefs), color = palette.textDim.toComposeColor()) },
            leadingIcon = { Icon(NotaIcons.search, null, tint = palette.textDim.toComposeColor()) },
            trailingIcon = {
                if (prefQuery.isNotEmpty()) {
                    Icon(
                        NotaIcons.close, stringResource(R.string.clear_search),
                        tint = palette.textDim.toComposeColor(),
                        modifier = Modifier.clip(CircleShape).clickable { prefQuery = "" },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        // The settings hub: the pane's sections folded into five categories (plus All), one chip
        // per category with its line icon. The row scrolls horizontally so a narrow pane still
        // reaches every category; picking one filters the sections below, while the search above
        // keeps spanning them all (see inCategory).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(categoryRowScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsCategory.entries.forEach { cat ->
                CategoryChip(stringResource(cat.labelRes), cat.icon, category == cat) { category = cat }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            // Ending at the keyboard's edge scrolls a focused field up out from under it.
            Modifier.fillMaxSize().imePadding().verticalScroll(scrollState)
                .onGloballyPositioned { viewport = it.boundsInRoot() },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inCategory(SettingsCategory.INTERFACE) && sectionShown(
                R.string.pref_general,
                R.string.pref_ui_theme,
                R.string.theme_system,
                R.string.theme_dark,
                R.string.theme_light,
                R.string.theme_oled,
                R.string.pref_nav_side,
                R.string.pref_nav_side_auto,
                R.string.edge_left,
                R.string.edge_right,
                R.string.edge_bottom,
                R.string.pref_nav_side_help,
                R.string.pref_app_language,
                R.string.pref_app_language_system,
                R.string.app_language_name_ar,
                R.string.app_language_name_en,
                R.string.material_tone,
                R.string.material_system_colours,
                R.string.material_single_tone,
                R.string.material_dual_tone,
                R.string.material_system_fallback,
                R.string.pref_corners,
                R.string.corners_sharp,
                R.string.corners_rounded,
                R.string.corners_soft,
                R.string.pref_start_fullscreen
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_general), NotaIcons.gear)
                FieldLabel(stringResource(R.string.pref_ui_theme))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.theme_system), prefs.uiAppearance == "system") { update(prefs.copy(uiAppearance = "system")) }
                    Chip(stringResource(R.string.theme_dark), prefs.uiAppearance == "dark") { update(prefs.copy(uiAppearance = "dark")) }
                    Chip(stringResource(R.string.theme_light), prefs.uiAppearance == "light") { update(prefs.copy(uiAppearance = "light")) }
                    Chip(stringResource(R.string.theme_oled), prefs.uiAppearance == "oled") { update(prefs.copy(uiAppearance = "oled")) }
                }

                // xnota: the wide-screen navigation surface's physical edge, and the app language.
                // Both live in Settings (not Preferences) and apply live / via activity recreate.
                FieldLabel(stringResource(R.string.pref_nav_side))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((side, label) in listOf(
                        "auto" to R.string.pref_nav_side_auto,
                        "left" to R.string.edge_left,
                        "right" to R.string.edge_right,
                        "bottom" to R.string.edge_bottom,
                    )) {
                        Chip(stringResource(label), editor.backstageNavSide == side) { editor.applyBackstageNavSide(side) }
                    }
                }
                Text(stringResource(R.string.pref_nav_side_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                FieldLabel(stringResource(R.string.pref_app_language))
                val activity = LocalContext.current as? android.app.Activity
                val setAppLanguage: (String) -> Unit = { tag ->
                    if (editor.appLanguage != tag) {
                        editor.applyAppLanguage(tag)
                        // The write already landed (synchronous in applyAppLanguage); recreate reloads resources.
                        activity?.recreate()
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.pref_app_language_system), editor.appLanguage == "system") { setAppLanguage("system") }
                    Chip(stringResource(R.string.app_language_name_ar), editor.appLanguage == "ar") { setAppLanguage("ar") }
                    Chip(stringResource(R.string.app_language_name_en), editor.appLanguage == "en") { setAppLanguage("en") }
                }
                val systemColours = prefs.materialMode == MaterialColourMode.SYSTEM
                FieldLabel(stringResource(R.string.material_tone))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.material_system_colours), systemColours) {
                        update(prefs.copy(materialMode = MaterialColourMode.SYSTEM))
                    }
                    Chip(stringResource(R.string.material_single_tone), prefs.materialMode == MaterialColourMode.SINGLE) {
                        update(prefs.copy(materialMode = MaterialColourMode.SINGLE))
                    }
                    Chip(stringResource(R.string.material_dual_tone), prefs.materialMode == MaterialColourMode.DUAL) {
                        update(prefs.copy(materialMode = MaterialColourMode.DUAL))
                    }
                }
                if (!systemColours) {
                    key(prefs.materialMode) { MaterialColourPicker(prefs, ::update) }
                    CustomMaterialControls(prefs, ::update)
                } else if (Build.VERSION.SDK_INT < 31) {
                    Text(stringResource(R.string.material_system_fallback), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                }
                FieldLabel(stringResource(R.string.pref_corners))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((style, label) in listOf(CornerStyle.SHARP to R.string.corners_sharp, CornerStyle.ROUNDED to R.string.corners_rounded, CornerStyle.SOFT to R.string.corners_soft)) {
                        Chip(stringResource(label), prefs.cornerStyle == style) { updateHome(prefs.copy(cornerStyle = style)) }
                    }
                }
                CheckRow(stringResource(R.string.pref_start_fullscreen), editor.fullscreen) { editor.setFullscreenPref(it) }

                // xnota: the app lock — a PIN gate over the whole app, armed on cold start and, with
                // a delay chosen, after long enough in the background.
            }
            if (inCategory(SettingsCategory.DATA) && sectionShown(
                R.string.pref_app_lock,
                R.string.pref_app_lock_help_off,
                R.string.pref_app_lock_help_on,
                R.string.pin_set,
                R.string.pin_change,
                R.string.pin_remove,
                R.string.pref_app_lock_delay,
                R.string.lock_cold_start,
                R.string.lock_after_30s,
                R.string.lock_after_60s,
                R.string.lock_after_5m,
                R.string.pref_app_lock_delay_help
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_app_lock), NotaIcons.shield)
                AppLockSection(prefs, ::updateHome)

            }
            if (inCategory(SettingsCategory.WRITING) && sectionShown(
                R.string.pref_input,
                R.string.pref_finger_draws,
                R.string.pref_zoom_lock_pan,
                R.string.pan_single,
                R.string.pan_two,
                R.string.pan_none,
                R.string.pref_detect_shapes,
                R.string.pref_pen_button_hold,
                R.string.pref_pen_button_hover,
                R.string.pref_pen_button2_hold,
                R.string.pref_two_finger_tap,
                R.string.pref_three_finger_tap,
                R.string.pref_stylus_double_tap,
                R.string.pref_stylus_button_tap,
                R.string.pref_redmi_buttons,
                R.string.pen_button_1,
                R.string.pen_button_2
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_input), NotaIcons.hand)
                CheckRow(stringResource(R.string.pref_finger_draws), prefs.fingerDraws) { update(prefs.copy(fingerDraws = it)) }
                FieldLabel(stringResource(R.string.pref_zoom_lock_pan))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(stringResource(R.string.pan_single), prefs.zoomLockPan == "single") { update(prefs.copy(zoomLockPan = "single")) }
                    Chip(stringResource(R.string.pan_two), prefs.zoomLockPan == "double") { update(prefs.copy(zoomLockPan = "double")) }
                    Chip(stringResource(R.string.pan_none), prefs.zoomLockPan == "none") { update(prefs.copy(zoomLockPan = "none")) }
                }
                CheckRow(stringResource(R.string.pref_detect_shapes), prefs.detectShapes) { update(prefs.copy(detectShapes = it)) }
                FieldLabel(stringResource(R.string.pref_pen_button_hold))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    penButtonOptions.forEach { (id, label) ->
                        Chip(stringResource(label), prefs.penButtonTool == id) { update(prefs.copy(penButtonTool = id)) }
                    }
                }
                if (prefs.penButtonTool == "eraser" || prefs.penButtonTool == "pan") {
                    CheckRow(stringResource(R.string.pref_pen_button_hover), prefs.penButtonHover) {
                        update(prefs.copy(penButtonHover = it))
                    }
                }
                // The fork's two-button pens: the second barrel button can hold its own tool instead
                // of falling back to the first's.
                FieldLabel(stringResource(R.string.pref_pen_button2_hold))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    penButtonOptions.forEach { (id, label) ->
                        Chip(stringResource(label), prefs.penButton2Tool == id) { update(prefs.copy(penButton2Tool = id)) }
                    }
                }
                val tapOptions = tapGestureOptions.map { (id, res) -> id to stringResource(res) }
                FieldLabel(stringResource(R.string.pref_two_finger_tap))
                OptionDropdown(tapOptions, prefs.twoFingerTap) { update(prefs.copy(twoFingerTap = it)) }
                FieldLabel(stringResource(R.string.pref_three_finger_tap))
                OptionDropdown(tapOptions, prefs.threeFingerTap) { update(prefs.copy(threeFingerTap = it)) }
                FieldLabel(stringResource(R.string.pref_stylus_double_tap))
                OptionDropdown(tapOptions, prefs.stylusDoubleTap) { update(prefs.copy(stylusDoubleTap = it)) }
                FieldLabel(stringResource(R.string.pref_stylus_button_tap))
                OptionDropdown(tapOptions, prefs.stylusButtonTap) { update(prefs.copy(stylusButtonTap = it)) }
                FieldLabel(stringResource(R.string.pref_redmi_buttons))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.pen_button_1), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                        OptionDropdown(tapOptions, prefs.stylusButton1Tap) { update(prefs.copy(stylusButton1Tap = it)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.pen_button_2), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                        OptionDropdown(tapOptions, prefs.stylusButton2Tap) { update(prefs.copy(stylusButton2Tap = it)) }
                    }
                }

            }
            if (inCategory(SettingsCategory.WRITING) && sectionShown(
                R.string.pref_text_typing,
                R.string.pref_smart_quotes,
                R.string.pref_typewriter,
                R.string.pref_typing_sounds,
                R.string.pref_pen_haptics,
                R.string.pref_keep_screen_on,
                R.string.pref_immersive
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_text_typing), NotaIcons.keyboard)
                // The fork's typing behaviour: the flow editor's input transforms and its feedback.
                CheckRow(stringResource(R.string.pref_smart_quotes), prefs.smartQuotes) {
                    update(prefs.copy(smartQuotes = it))
                }
                CheckRow(stringResource(R.string.pref_typewriter), prefs.typewriterScrolling) {
                    update(prefs.copy(typewriterScrolling = it))
                }
                CheckRow(stringResource(R.string.pref_typing_sounds), prefs.typingSounds) {
                    update(prefs.copy(typingSounds = it))
                }
                CheckRow(stringResource(R.string.pref_pen_haptics), prefs.penHaptics) {
                    update(prefs.copy(penHaptics = it))
                }
                CheckRow(stringResource(R.string.pref_keep_screen_on), prefs.keepScreenOn) {
                    update(prefs.copy(keepScreenOn = it))
                }
                CheckRow(stringResource(R.string.pref_immersive), prefs.immersiveMode) {
                    update(prefs.copy(immersiveMode = it))
                }

            }
            if (inCategory(SettingsCategory.WRITING) && sectionShown(
                R.string.pref_ink_engine,
                R.string.pref_ink_engine_help,
                R.string.pref_stroke_filter,
                R.string.filter_ema,
                R.string.filter_one_euro,
                R.string.pref_adaptive_smoothing,
                R.string.pref_pressure_deadzone,
                R.string.pref_pressure_gamma,
                R.string.pref_pressure_smoothing,
                R.string.pref_nib_angle,
                R.string.nib_follow_travel,
                R.string.pref_highlighter_blend,
                R.string.blend_multiply,
                R.string.blend_normal,
                R.string.pref_join_strokes,
                R.string.pref_undo_coalesce
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_ink_engine), NotaIcons.zap)
                Text(stringResource(R.string.pref_ink_engine_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                FieldLabel(stringResource(R.string.pref_stroke_filter))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.filter_ema), prefs.strokeFilter == "ema") { update(prefs.copy(strokeFilter = "ema")) }
                    Chip(stringResource(R.string.filter_one_euro), prefs.strokeFilter == "one_euro") { update(prefs.copy(strokeFilter = "one_euro")) }
                }
                CheckRow(stringResource(R.string.pref_adaptive_smoothing), prefs.adaptiveSmoothing) {
                    update(prefs.copy(adaptiveSmoothing = it))
                }
                FieldLabel(stringResource(R.string.pref_pressure_deadzone))
                Slider(
                    value = prefs.pressureDeadzone.toFloat(),
                    onValueChange = { update(prefs.copy(pressureDeadzone = it.toDouble())) },
                    valueRange = 0f..0.20f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_pressure_gamma))
                Slider(
                    value = prefs.pressureGamma.toFloat(),
                    onValueChange = { update(prefs.copy(pressureGamma = it.toDouble())) },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_pressure_smoothing))
                Slider(
                    value = prefs.pressureSmoothing.toFloat(),
                    onValueChange = { update(prefs.copy(pressureSmoothing = it.toInt())) },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_nib_angle))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.nib_follow_travel), prefs.nibAngleLockDeg < 0.0) {
                        update(prefs.copy(nibAngleLockDeg = -1.0))
                    }
                    for (deg in listOf(0.0, 45.0, 90.0, 135.0)) {
                        Chip("${deg.toInt()}°", prefs.nibAngleLockDeg == deg) { update(prefs.copy(nibAngleLockDeg = deg)) }
                    }
                }
                FieldLabel(stringResource(R.string.pref_highlighter_blend))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.blend_multiply), prefs.highlighterBlend == "multiply") { update(prefs.copy(highlighterBlend = "multiply")) }
                    Chip(stringResource(R.string.blend_normal), prefs.highlighterBlend == "normal") { update(prefs.copy(highlighterBlend = "normal")) }
                }
                CheckRow(stringResource(R.string.pref_join_strokes), prefs.joinStrokes) {
                    update(prefs.copy(joinStrokes = it))
                }
                CheckRow(stringResource(R.string.pref_undo_coalesce), prefs.undoCoalesce) {
                    update(prefs.copy(undoCoalesce = it))
                }

            }
            if (inCategory(SettingsCategory.WRITING) && sectionShown(
                R.string.pref_new_notes,
                R.string.pref_filename_template,
                R.string.pref_filename_template_help,
                R.string.pref_next_note_named
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_new_notes), NotaIcons.filePlus)
                FieldLabel(stringResource(R.string.pref_filename_template))
                Text(
                    stringResource(R.string.pref_filename_template_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = prefs.newNoteNameTemplate,
                    onValueChange = { update(prefs.copy(newNoteNameTemplate = it)) },
                    singleLine = true,
                    placeholder = { Text(NameTemplate.DEFAULT) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.width(280.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NAME_TEMPLATE_PRESETS.forEach { t ->
                        Chip(t, prefs.newNoteNameTemplate == t) { update(prefs.copy(newNoteNameTemplate = t)) }
                    }
                }
                Text(
                    stringResource(R.string.pref_next_note_named, "${editor.newNoteStem(emptySet())}.xnote"),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )

            }
            if (inCategory(SettingsCategory.READING) && sectionShown(
                R.string.pref_page,
                R.string.pref_adaptive_ink,
                R.string.pref_adaptive_ink_help,
                R.string.pref_default_page_size,
                R.string.width_mm,
                R.string.height_mm,
                R.string.pref_orientation,
                R.string.orientation_portrait,
                R.string.orientation_landscape,
                R.string.pref_hide_page_borders,
                R.string.pref_side_margin_px,
                R.string.pref_page_numbers,
                R.string.page_numbers_off,
                R.string.page_numbers_centre,
                R.string.page_numbers_right,
                R.string.pref_page_colour,
                R.string.pref_page_colour_follows_theme
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_page), NotaIcons.page)
                FieldLabel(stringResource(R.string.pref_adaptive_ink))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    adaptiveInkOptions.forEach { (id, label) ->
                        Chip(stringResource(label), prefs.adaptiveInk == id) { update(prefs.copy(adaptiveInk = id)) }
                    }
                }
                Text(
                    stringResource(R.string.pref_adaptive_ink_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                FieldLabel(stringResource(R.string.pref_default_page_size))
                SizeDropdown(prefs.defaultPageSize) { it?.let { size -> update(prefs.copy(defaultPageSize = size)) } }
                if (prefs.defaultPageSize == PageSize.CUSTOM) {
                    // A custom page is taken as typed, so the orientation chips have nothing to say
                    // about it and are left out rather than shown doing nothing.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MillimetreField(stringResource(R.string.width_mm), prefs.customPageWidthMm) {
                            update(prefs.copy(customPageWidthMm = it))
                        }
                        MillimetreField(stringResource(R.string.height_mm), prefs.customPageHeightMm) {
                            update(prefs.copy(customPageHeightMm = it))
                        }
                    }
                } else {
                    FieldLabel(stringResource(R.string.pref_orientation))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.orientation_portrait), prefs.defaultPageOrientation == Orientation.PORTRAIT) {
                            update(prefs.copy(defaultPageOrientation = Orientation.PORTRAIT))
                        }
                        Chip(stringResource(R.string.orientation_landscape), prefs.defaultPageOrientation == Orientation.LANDSCAPE) {
                            update(prefs.copy(defaultPageOrientation = Orientation.LANDSCAPE))
                        }
                    }
                }
                CheckRow(stringResource(R.string.pref_hide_page_borders), prefs.hidePageBorders) {
                    update(prefs.copy(hidePageBorders = it))
                }
                FieldLabel(stringResource(R.string.pref_side_margin_px, prefs.sideMargin.toInt()))
                Slider(
                    value = prefs.sideMargin.toFloat(),
                    onValueChange = { update(prefs.copy(sideMargin = it.toDouble())) },
                    valueRange = 0f..64f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_page_numbers))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.page_numbers_off), prefs.pageNumbers == 0) {
                        update(prefs.copy(pageNumbers = 0))
                    }
                    Chip(stringResource(R.string.page_numbers_centre), prefs.pageNumbers == 1) {
                        update(prefs.copy(pageNumbers = 1))
                    }
                    Chip(stringResource(R.string.page_numbers_right), prefs.pageNumbers == 2) {
                        update(prefs.copy(pageNumbers = 2))
                    }
                }
                FieldLabel(stringResource(R.string.pref_page_colour))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwatchCircles(editor, SwatchRole.PAPER, prefs.pageColor) { update(prefs.copy(pageColor = it)) }
                }
                CheckRow(stringResource(R.string.pref_page_colour_follows_theme), prefs.pageColor == null) {
                    update(prefs.copy(pageColor = if (it) null else editor.swatches(SwatchRole.PAPER).first()))
                }

            }
            if (inCategory(SettingsCategory.INTERFACE) && sectionShown(
                R.string.pref_home_explorer,
                R.string.pref_home_explorer_help,
                R.string.pref_default_layout,
                R.string.pref_switcher_layouts,
                R.string.pref_sidebar_sections,
                R.string.recent,
                R.string.pinned,
                R.string.toolbar_colours,
                R.string.smart_folders,
                R.string.trash,
                R.string.pref_home_opens_to,
                R.string.top_folder,
                R.string.last_folder,
                R.string.recent_and_pinned,
                R.string.pref_dates,
                R.string.pref_calendar,
                R.string.date_gregorian,
                R.string.date_hijri,
                R.string.pref_digits,
                R.string.digit_auto,
                R.string.digit_latin,
                R.string.digit_arabic,
                R.string.pref_tapping_file,
                R.string.tap_opens,
                R.string.tap_previews,
                R.string.pref_keep_deleted,
                R.string.off,
                R.string.until_emptied,
                R.string.pref_colour_names,
                R.string.name_a_colour,
                R.string.pref_per_folder_views,
                R.string.pref_show_folder_counts,
                R.string.pref_show_extensions,
                R.string.pref_storage,
                R.string.storage_scan,
                R.string.pref_writing_pulse,
                R.string.words_today_goal,
                R.string.words_today_plain,
                R.string.pref_daily_word_goal,
                R.string.pref_daily_word_goal_off
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_home_explorer), NotaIcons.home)
                Text(
                    stringResource(R.string.pref_home_explorer_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                FieldLabel(stringResource(R.string.pref_default_layout))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerLayout.entries.forEach { l -> Chip(stringResource(l.labelRes), editor.explorerView.layout == l) { editor.setDefaultLayout(l) } }
                }
                FieldLabel(stringResource(R.string.pref_switcher_layouts))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplorerLayout.entries.forEach { l ->
                        val on = l in prefs.switcherLayouts
                        ExplorerChip(stringResource(l.labelRes), on, icon = if (on) NotaIcons.check else NotaIcons.plus) {
                            val next = if (on) prefs.switcherLayouts - l else prefs.switcherLayouts + l
                            if (next.isNotEmpty()) updateHome(prefs.copy(switcherLayouts = ExplorerLayout.entries.filter { it in next }))
                        }
                    }
                }
                FieldLabel(stringResource(R.string.pref_sidebar_sections))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun section(label: String, on: Boolean, flip: () -> Preferences) =
                        ExplorerChip(label, on, icon = if (on) NotaIcons.check else NotaIcons.plus) { updateHome(flip()) }
                    section(stringResource(R.string.recent), prefs.sidebarRecent) { prefs.copy(sidebarRecent = !prefs.sidebarRecent) }
                    section(stringResource(R.string.pinned), prefs.sidebarPinned) { prefs.copy(sidebarPinned = !prefs.sidebarPinned) }
                    section(stringResource(R.string.toolbar_colours), prefs.sidebarColours) { prefs.copy(sidebarColours = !prefs.sidebarColours) }
                    section(stringResource(R.string.smart_folders), prefs.sidebarSmartFolders) { prefs.copy(sidebarSmartFolders = !prefs.sidebarSmartFolders) }
                    if (prefs.trashDays != 0) section(stringResource(R.string.trash), prefs.sidebarTrash) { prefs.copy(sidebarTrash = !prefs.sidebarTrash) }
                }
                FieldLabel(stringResource(R.string.pref_home_opens_to))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.top_folder), prefs.homeOpensTo == "top") { updateHome(prefs.copy(homeOpensTo = "top")) }
                    Chip(stringResource(R.string.last_folder), prefs.homeOpensTo == "last") { updateHome(prefs.copy(homeOpensTo = "last")) }
                    Chip(stringResource(R.string.recent_and_pinned), prefs.homeOpensTo == "shelves") { updateHome(prefs.copy(homeOpensTo = "shelves")) }
                }
                val words = rememberExplorerWords(prefs, editor.appLanguage)
                FieldLabel(stringResource(R.string.pref_dates))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(words.daysAgo(2), prefs.dateStyle == "relative") { updateHome(prefs.copy(dateStyle = "relative")) }
                    Chip("${words.shortWeekday(java.time.DayOfWeek.WEDNESDAY)} ${clockText(words, java.time.LocalTime.of(17, 30), true)}", prefs.dateStyle == "day") { updateHome(prefs.copy(dateStyle = "day")) }
                    Chip(words.dayMonthYear(16, java.time.Month.SEPTEMBER, 2026), prefs.dateStyle == "date") { updateHome(prefs.copy(dateStyle = "date")) }
                }
                // xnota: the calendar the explorer writes dates in, and the digits it writes numbers
                // with — both display-only (nothing on disk changes), so they apply like the date style.
                FieldLabel(stringResource(R.string.pref_calendar))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.date_gregorian), prefs.dateSystem == "gregorian") { updateHome(prefs.copy(dateSystem = "gregorian")) }
                    Chip(stringResource(R.string.date_hijri), prefs.dateSystem == "hijri") { updateHome(prefs.copy(dateSystem = "hijri")) }
                }
                FieldLabel(stringResource(R.string.pref_digits))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.digit_auto), prefs.digitSystem == "auto") { updateHome(prefs.copy(digitSystem = "auto")) }
                    Chip(stringResource(R.string.digit_latin), prefs.digitSystem == "latin") { updateHome(prefs.copy(digitSystem = "latin")) }
                    Chip(stringResource(R.string.digit_arabic), prefs.digitSystem == "arabic") { updateHome(prefs.copy(digitSystem = "arabic")) }
                }
                FieldLabel(stringResource(R.string.pref_tapping_file))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.tap_opens), !prefs.tapPreviews) { updateHome(prefs.copy(tapPreviews = false)) }
                    Chip(stringResource(R.string.tap_previews), prefs.tapPreviews) { updateHome(prefs.copy(tapPreviews = true)) }
                }
                FieldLabel(stringResource(R.string.pref_keep_deleted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.off), prefs.trashDays == 0) { updateHome(prefs.copy(trashDays = 0)) }
                    Chip(pluralStringResource(R.plurals.days_count, 7, 7), prefs.trashDays == 7) { updateHome(prefs.copy(trashDays = 7)) }
                    Chip(pluralStringResource(R.plurals.days_count, 30, 30), prefs.trashDays == 30) { updateHome(prefs.copy(trashDays = 30)) }
                    Chip(stringResource(R.string.until_emptied), prefs.trashDays == Preferences.TRASH_FOREVER) { updateHome(prefs.copy(trashDays = Preferences.TRASH_FOREVER)) }
                }
                if (editor.browseRoot != null) {
                    FieldLabel(stringResource(R.string.pref_colour_names))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        editor.colorNames.entries.sortedBy { it.value.lowercase() }.forEach { (c, name) ->
                            Row(
                                Modifier.clip(MaterialTheme.shapes.small).background(palette.surface.toComposeColor())
                                    .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
                                    .clickable { namingColor = c }.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(codeTint(c, palette)))
                                Text(name, color = palette.text.toComposeColor(), fontSize = 14.sp, maxLines = 1)
                            }
                        }
                        var picking by remember { mutableStateOf(false) }
                        Box {
                            ExplorerChip(stringResource(R.string.name_a_colour), false, icon = NotaIcons.plus) { picking = true }
                            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                                ColorCodeMenuContent { c -> picking = false; if (c != null) namingColor = c }
                            }
                        }
                    }
                }
                CheckRow(stringResource(R.string.pref_per_folder_views), prefs.perFolderViews) { updateHome(prefs.copy(perFolderViews = it)) }
                CheckRow(stringResource(R.string.pref_show_folder_counts), prefs.showFolderCounts) { updateHome(prefs.copy(showFolderCounts = it)) }
                CheckRow(stringResource(R.string.pref_show_extensions), prefs.showExtensions) { updateHome(prefs.copy(showExtensions = it)) }
                // The fork's storage readout: the notes root's total size, scanned on IO and keyed to the
                // root so changing folders re-measures while nothing else recomposes it.
                val notesRoot = editor.browseRoot
                if (notesRoot != null) {
                    val used by androidx.compose.runtime.produceState<Long?>(null, notesRoot) {
                        value = withContext(Dispatchers.IO) { editor.storageUsedBytes(notesRoot) }
                    }
                    FieldLabel(stringResource(R.string.pref_storage))
                    Text(
                        used?.let { formatSize(it, words::digits) } ?: stringResource(R.string.storage_scan),
                        color = palette.textDim.toComposeColor(),
                        fontSize = 13.sp,
                    )
                }
                // The fork's writing pulse: words written today (against the daily goal when one is
                // set) and the writing streak over the recorded edit days.
                editor.prefsVersion
                val goal = prefs.dailyWordGoal
                val todayWords = editor.wordsToday()
                val streak = editor.writingStreak()
                FieldLabel(stringResource(R.string.pref_writing_pulse))
                Text(
                    if (goal > 0) stringResource(R.string.words_today_goal, todayWords, goal, streak)
                    else stringResource(R.string.words_today_plain, todayWords, streak),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 13.sp,
                )
                // The daily goal: 0 turns the chip into the plain reading; 250..5000 words covers the
                // serious writing habits without a spinner to mistype.
                FieldLabel(
                    if (prefs.dailyWordGoal == 0) stringResource(R.string.pref_daily_word_goal) + " · " + stringResource(R.string.pref_daily_word_goal_off)
                    else stringResource(R.string.pref_daily_word_goal),
                )
                Slider(
                    value = prefs.dailyWordGoal.toFloat(),
                    onValueChange = { updateHome(prefs.copy(dailyWordGoal = (Math.round(it / 50.0) * 50).toInt().coerceIn(0, 5000))) },
                    valueRange = 0f..2000f,
                    modifier = Modifier.width(280.dp),
                )

            }
            if (inCategory(SettingsCategory.DATA) && sectionShown(
                R.string.pref_backup,
                R.string.pref_backup_help,
                R.string.backup_no_folder,
                R.string.backup_folder_set,
                R.string.backup_last_at,
                R.string.backup_choose,
                R.string.backup_change,
                R.string.backup_forget,
                R.string.backup_every,
                R.string.backup_never,
                R.string.backup_daily,
                R.string.backup_weekly,
                R.string.backup_passphrase,
                R.string.backup_passphrase_set,
                R.string.backup_passphrase_on,
                R.string.backup_passphrase_clear,
                R.string.backup_passphrase_help,
                R.string.backup_now,
                R.string.backup_failed,
                R.string.backup_restore,
                R.string.backup_verify,
                R.string.backup_verify_none,
                R.string.backup_verify_fail,
                R.string.backup_verify_no_manifest,
                R.string.backup_restore_help,
                R.string.backup_none_yet,
                R.string.backup_nothing_to_restore,
                R.string.backup_decrypt_failed,
                R.string.cancel
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_backup), NotaIcons.archive)
                Text(
                    stringResource(R.string.pref_backup_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                BackupSection(editor, onPickBackupRoot, prefs) { updateHome(prefs.copy(backupEvery = it)) }

            }
            if (inCategory(SettingsCategory.READING) && sectionShown(
                R.string.pref_reading,
                R.string.pref_reader_font_scale,
                R.string.pref_reader_line_height,
                R.string.reader_sepia
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_reading), NotaIcons.bookOpen)
                // The fork's reading kit: the reader pane adjusts these live; here they are settable
                // directly, for settling in before the first read.
                FieldLabel(stringResource(R.string.pref_reader_font_scale))
                Slider(
                    value = prefs.readerFontScale.toFloat(),
                    onValueChange = { updateHome(prefs.copy(readerFontScale = (Math.round(it * 10) / 10.0).coerceIn(0.8, 2.0))) },
                    valueRange = 0.8f..2.0f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_reader_line_height))
                Slider(
                    value = prefs.readerLineHeight.toFloat(),
                    onValueChange = { updateHome(prefs.copy(readerLineHeight = (Math.round(it * 20) / 20.0).coerceIn(1.2, 2.4))) },
                    valueRange = 1.2f..2.4f,
                    modifier = Modifier.width(280.dp),
                )
                CheckRow(stringResource(R.string.reader_sepia), prefs.readerSepia) {
                    updateHome(prefs.copy(readerSepia = it))
                }

            }
            if (inCategory(SettingsCategory.READING) && sectionShown(R.string.pref_focus, R.string.pref_focus_minutes, R.string.pref_focus_break)) SettingsCard {
                SectionTitle(stringResource(R.string.pref_focus), NotaIcons.target)
                FieldLabel(stringResource(R.string.pref_focus_minutes))
                Slider(
                    value = prefs.focusMinutes.toFloat(),
                    onValueChange = { updateHome(prefs.copy(focusMinutes = Math.round(it))) },
                    valueRange = 5f..90f,
                    modifier = Modifier.width(280.dp),
                )
                FieldLabel(stringResource(R.string.pref_focus_break))
                Slider(
                    value = prefs.focusBreak.toFloat(),
                    onValueChange = { updateHome(prefs.copy(focusBreak = Math.round(it))) },
                    valueRange = 1f..30f,
                    modifier = Modifier.width(280.dp),
                )

            }
            if (inCategory(SettingsCategory.ADVANCED) && sectionShown(
                R.string.pref_qibla,
                R.string.qibla_lat,
                R.string.qibla_lon,
                R.string.qibla_bearing,
                R.string.qibla_distance,
                R.string.qibla_help,
                R.string.qibla_reset
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_qibla), NotaIcons.compass)
                // The fork's Qibla pane: coordinates typed by hand, the bearing drawn on a compass
                // rose — see QiblaPane for what it shows and why it never asks for location.
                QiblaPane(prefs.qiblaLat, prefs.qiblaLon) { lat, lon ->
                    updateHome(prefs.copy(qiblaLat = lat, qiblaLon = lon))
                }

            }
            if (inCategory(SettingsCategory.READING) && sectionShown(R.string.pref_pdf_export, R.string.pref_pdf_heading_bookmarks)) SettingsCard {
                SectionTitle(stringResource(R.string.pref_pdf_export), NotaIcons.exportDoc)
                CheckRow(stringResource(R.string.pref_pdf_heading_bookmarks), prefs.pdfHeadingBookmarks) {
                    updateHome(prefs.copy(pdfHeadingBookmarks = it))
                }

            }
            if (inCategory(SettingsCategory.ADVANCED) && sectionShown(
                R.string.pref_performance,
                R.string.pref_max_cache_px,
                R.string.pref_cache_help,
                R.string.pref_disable_front_buffering,
                R.string.pref_front_buffering_help,
                R.string.pref_code_highlighting,
                R.string.pref_default_code_language,
                R.string.pref_code_language_help,
                R.string.code_plain,
                R.string.pref_code_theme,
                R.string.pref_code_theme_help,
                R.string.custom_theme,
                R.string.reset,
                R.string.import_theme
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_performance), NotaIcons.gauge)
                FieldLabel(stringResource(R.string.pref_max_cache_px, prefs.maxCacheResolution))
                Slider(
                    value = prefs.maxCacheResolution.toFloat(),
                    onValueChange = { update(prefs.copy(maxCacheResolution = Math.round(it))) },
                    valueRange = 1024f..4096f,
                    steps = 5,
                    modifier = Modifier.width(280.dp),
                )
                Text(
                    stringResource(R.string.pref_cache_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                CheckRow(stringResource(R.string.pref_disable_front_buffering), prefs.disableFrontBuffering) {
                    update(prefs.copy(disableFrontBuffering = it))
                }
                Text(
                    stringResource(R.string.pref_front_buffering_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )

                if (editor.treeSitterAvailable) {
                    // Inline, so a hairline at a whisper (the card border already frames the section).
                    HorizontalDivider(color = palette.border.toComposeColor().copy(alpha = 0.6f))
                    SectionTitle(stringResource(R.string.pref_code_highlighting), NotaIcons.code)

                    val langOptions = editor.scmLanguages().map { it to it }
                    FieldLabel(stringResource(R.string.pref_default_code_language))
                    Text(
                        stringResource(R.string.pref_code_language_help),
                        color = palette.textDim.toComposeColor(),
                        fontSize = 12.sp,
                    )
                    OptionDropdown(listOf("plain" to stringResource(R.string.code_plain)) + langOptions, prefs.defaultCodeLanguage) {
                        update(prefs.copy(defaultCodeLanguage = it))
                    }

                    FieldLabel(stringResource(R.string.pref_code_theme))
                    Text(
                        stringResource(R.string.pref_code_theme_help),
                        color = palette.textDim.toComposeColor(),
                        fontSize = 12.sp,
                    )
                    if (editor.hasCustomCodeTheme) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                prefs.codeThemeName ?: stringResource(R.string.custom_theme),
                                color = palette.text.toComposeColor(),
                                fontSize = 13.sp,
                            )
                            TextButton(onClick = { editor.resetCodeTheme() }) { Text(stringResource(R.string.reset), fontSize = 13.sp) }
                        }
                    }
                    TextButton(onClick = { onImportCodeTheme() }) { Text(stringResource(R.string.import_theme), fontSize = 13.sp) }
                }

            }
            if (inCategory(SettingsCategory.ADVANCED) && sectionShown(
                R.string.pref_fonts,
                R.string.pref_fonts_help,
                R.string.import_font,
                R.string.mono_suffix,
                R.string.remove
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_fonts), NotaIcons.fontFace)
                Text(
                    stringResource(R.string.pref_fonts_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                TextButton(onClick = { onImportFont() }) { Text(stringResource(R.string.import_font), fontSize = 13.sp) }
                for (font in editor.customFonts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            font.label,
                            color = palette.text.toComposeColor(),
                            fontSize = 13.sp,
                            style = TextStyle(fontFamily = font.face.toComposeFamily()),
                        )
                        if (font.mono) {
                            Text(stringResource(R.string.mono_suffix), color = palette.accent.toComposeColor(), fontSize = 11.sp)
                        }
                        TextButton(onClick = { editor.removeCustomFont(font.face) }) { Text(stringResource(R.string.remove), fontSize = 12.sp) }
                    }
                }

            }
            if (inCategory(SettingsCategory.WRITING) && sectionShown(
                R.string.pref_toolbar,
                R.string.pref_toolbar_style,
                R.string.toolbar_docked,
                R.string.toolbar_floating,
                R.string.pref_toolbar_position,
                R.string.edge_top,
                R.string.edge_bottom,
                R.string.edge_left,
                R.string.edge_right,
                R.string.pref_toolbar_size,
                R.string.toolbar_size_compact,
                R.string.toolbar_size_regular,
                R.string.toolbar_size_comfortable,
                R.string.pref_toolbar_colours_n,
                R.string.reset,
                R.string.pref_toolbar_help
            )) SettingsCard {
                SectionTitle(stringResource(R.string.pref_toolbar), NotaIcons.columns)
                // The look, like the swatch count below, is one setting for both bars.
                val look = prefs.toolbarLook
                FieldLabel(stringResource(R.string.pref_toolbar_style))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.toolbar_docked), !look.floating) { updateHome(prefs.copy(toolbarLook = look.copy(floating = false))) }
                    Chip(stringResource(R.string.toolbar_floating), look.floating) { updateHome(prefs.copy(toolbarLook = look.copy(floating = true))) }
                }
                FieldLabel(stringResource(R.string.pref_toolbar_position))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((position, label) in listOf(
                        ToolbarPosition.TOP to R.string.edge_top,
                        ToolbarPosition.BOTTOM to R.string.edge_bottom,
                        ToolbarPosition.LEFT to R.string.edge_left,
                        ToolbarPosition.RIGHT to R.string.edge_right,
                    )) {
                        Chip(stringResource(label), look.position == position) { updateHome(prefs.copy(toolbarLook = look.copy(position = position))) }
                    }
                }
                FieldLabel(stringResource(R.string.pref_toolbar_size))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((size, label) in listOf(
                        ToolbarSize.COMPACT to R.string.toolbar_size_compact,
                        ToolbarSize.REGULAR to R.string.toolbar_size_regular,
                        ToolbarSize.COMFORTABLE to R.string.toolbar_size_comfortable,
                    )) {
                        Chip(stringResource(label), look.size == size) { updateHome(prefs.copy(toolbarLook = look.copy(size = size))) }
                    }
                }
                FieldLabel(stringResource(R.string.pref_toolbar_colours_n, editor.toolbarColorCount))
                Slider(
                    value = editor.toolbarColorCount.toFloat(),
                    onValueChange = { editor.applyToolbarColorCount(Math.round(it)) },
                    valueRange = 1f..InkPalette.MAX_SWATCHES.toFloat(),
                    steps = InkPalette.MAX_SWATCHES - 2,
                    modifier = Modifier.width(280.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip("xnote", selected = !canvasTab) { showTab(false) }
                    Chip("xcanvas", selected = canvasTab) { showTab(true) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            applyEdited(if (canvasTab) ToolbarLayout.CANVAS_DEFAULT else ToolbarLayout.DEFAULT)
                        },
                    ) { Text(stringResource(R.string.reset), fontSize = 13.sp) }
                }
                Text(
                    stringResource(R.string.pref_toolbar_help),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
                ToolbarCustomizerBody(
                    layout = layout,
                    dragItem = dragItem,
                    dropTarget = dropTarget,
                    dragSection = dragSection,
                    sectionDropTarget = sectionDropTarget,
                    chipBounds = chipBounds,
                    sectionBounds = sectionBounds,
                    sectionGrabBounds = sectionGrabBounds,
                    onToggle = { s, i -> applyEdited(editedLayout().toggleVisible(s, i)) },
                    onDelete = { s -> applyEdited(editedLayout().removeSection(s)) },
                    onAdd = { applyEdited(editedLayout().addSection()) },
                    onDragStart = { item, local ->
                        dragItem = item
                        dragGrab = local
                        dragFinger = (chipBounds[item]?.topLeft ?: Offset.Zero) + local
                        retarget()
                    },
                    onDrag = { delta -> dragFinger += delta; retarget() },
                    onDrop = {
                        val target = dropTarget
                        val moving = dragItem
                        if (target != null && moving != null) {
                            val cur = editedLayout()
                            val fSec = cur.sections.indexOfFirst { s -> s.entries.any { it.item == moving } }
                            val fIdx = if (fSec >= 0) cur.sections[fSec].entries.indexOfFirst { it.item == moving } else -1
                            if (fSec >= 0 && fIdx >= 0) {
                                applyEdited(cur.moveItem(fSec, fIdx, target.first, target.second))
                            }
                        }
                        dragItem = null
                        dropTarget = null
                    },
                    onCancel = {
                        dragItem = null
                        dropTarget = null
                    },
                    onSectionDragStart = { sec, local ->
                        dragSection = sec
                        val grab = sectionGrabBounds[sec]?.topLeft ?: sectionBounds[sec]?.topLeft ?: Offset.Zero
                        val card = sectionBounds[sec]?.topLeft ?: Offset.Zero
                        dragFinger = grab + local
                        dragGrab = dragFinger - card
                        retargetSection()
                    },
                    onSectionDrag = { delta -> dragFinger += delta; retargetSection() },
                    onSectionDrop = {
                        val from = dragSection
                        val insertAt = sectionDropTarget
                        if (from != null && insertAt != null) {
                            val cur = editedLayout()
                            val to = (if (insertAt > from) insertAt - 1 else insertAt).coerceIn(0, cur.sections.lastIndex)
                            if (to != from) applyEdited(cur.moveSection(from, to))
                        }
                        dragSection = null
                        sectionDropTarget = null
                    },
                    onSectionCancel = {
                        dragSection = null
                        sectionDropTarget = null
                    },
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }
        // The dragged chip/section's floating copy: a pane-root sibling so the scroll never clips it.
        val gx = (dragFinger.x - dragGrab.x - paneTopLeft.x).toInt()
        val gy = (dragFinger.y - dragGrab.y - paneTopLeft.y).toInt()
        val ghostChip = dragItem
        val ghostSecIdx = dragSection
        if (ghostChip != null) {
            Box(Modifier.offset { IntOffset(gx, gy) }) { ToolbarDragGhost(ghostChip) }
        } else if (ghostSecIdx != null) {
            val sectionG = (if (canvasTab) editor.canvasToolbarLayout else editor.toolbarLayout)
                .sections.getOrNull(ghostSecIdx)
            val widthG = sectionBounds[ghostSecIdx]?.width
            if (sectionG != null && widthG != null) {
                Box(Modifier.offset { IntOffset(gx, gy) }) { SectionCardGhost(sectionG, widthG) }
            }
        }
    }

    // While dragging near a vertical edge of the scroll viewport, ease the list along so items
    // off-screen can be reached without lifting the finger.
    LaunchedEffect(dragItem != null || dragSection != null) {
        while (dragItem != null || dragSection != null) {
            val band = 90f
            val y = dragFinger.y
            val delta = when {
                y < viewport.top + band -> -((viewport.top + band - y) / band) * 14f
                y > viewport.bottom - band -> ((y - (viewport.bottom - band)) / band) * 14f
                else -> 0f
            }
            if (delta != 0f) {
                scrollState.scrollBy(delta)
                if (dragItem != null) retarget() else retargetSection()
            }
            delay(16L)
        }
    }
}

/**
 * The preferences search fold: the note search's own loose Arabic folding (hamza alefs and
 * ta-marbuta to their bare partners, tashkeel stepped over, Arabic-Indic digits both ways), case
 * folded — the query and each section's title/row labels both pass through it, so a match means
 * what a reader would call a match.
 */
private fun prefSearchFold(s: String): String {
    if (s.isEmpty()) return s
    val out = StringBuilder(s.length)
    for (c in s) {
        val f = com.xnota.core.search.SearchFold.fold(c, matchCase = false)
        if (f != com.xnota.core.search.SearchFold.IGNORE) out.append(f)
    }
    return out.toString().trim()
}

/**
 * One settings-card: the reference design's section wrapper (.settings-card) — rounded, panel-
 * backed, hairline-bordered, padded, with the pane's own row rhythm inside. Purely a wrapper:
 * everything emitted into it is the section's own rows, byte for byte.
 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(palette.menuBg.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector? = null) {
    // The hub's section headers carry their line icon beside the title, dim like the help lines
    // so the title stays dominant; the title itself is the reference recipe's 14sp W700.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) {
            Icon(icon, null, tint = LocalPalette.current.textDim.toComposeColor(), modifier = Modifier.size(18.dp))
        }
        Text(text, color = LocalPalette.current.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = LocalPalette.current.accent.toComposeColor(), fontSize = 13.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomMaterialControls(prefs: Preferences, update: (Preferences) -> Unit) {
    val palette = LocalPalette.current
    val styles = listOf(
        MaterialStyle.TONAL_SPOT to R.string.material_style_soft,
        MaterialStyle.VIBRANT to R.string.material_style_vivid,
        MaterialStyle.FIDELITY to R.string.material_style_original,
        MaterialStyle.EXPRESSIVE to R.string.material_style_playful,
        MaterialStyle.FRUIT_SALAD to R.string.material_style_fresh,
        MaterialStyle.RAINBOW to R.string.material_style_clean,
        MaterialStyle.NEUTRAL to R.string.material_style_muted,
        MaterialStyle.MONOCHROME to R.string.material_style_grayscale,
    )
    FieldLabel(stringResource(R.string.material_style))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { (style, label) ->
            Chip(stringResource(label), prefs.materialStyle == style) {
                update(prefs.copy(materialStyle = style))
            }
        }
    }
    val description = when (prefs.materialStyle) {
        MaterialStyle.TONAL_SPOT -> R.string.material_style_soft_description
        MaterialStyle.VIBRANT -> R.string.material_style_vivid_description
        MaterialStyle.FIDELITY -> R.string.material_style_original_description
        MaterialStyle.EXPRESSIVE -> R.string.material_style_playful_description
        MaterialStyle.FRUIT_SALAD -> R.string.material_style_fresh_description
        MaterialStyle.RAINBOW -> R.string.material_style_clean_description
        MaterialStyle.NEUTRAL -> R.string.material_style_muted_description
        MaterialStyle.MONOCHROME -> R.string.material_style_grayscale_description
    }
    Text(stringResource(description), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
    val percent = Math.round(prefs.materialContrast * 100).toInt()
    FieldLabel(stringResource(R.string.material_contrast, if (percent > 0) "+$percent%" else "$percent%"))
    Slider(
        value = prefs.materialContrast.toFloat(),
        onValueChange = { update(prefs.copy(materialContrast = Math.round(it * 10) / 10.0)) },
        valueRange = -1f..1f,
        steps = 19,
        modifier = Modifier.width(280.dp),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) palette.selectionBackground.toComposeColor() else palette.surface.toComposeColor())
            .border(1.dp, if (selected) palette.accent.toComposeColor() else palette.border.toComposeColor(), MaterialTheme.shapes.small)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) palette.selectionForeground.toComposeColor() else palette.text.toComposeColor(),
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One chip of the hub's category row: the reference design's selected-pill recipe — a rounded
 * pill that fills with a wash of the accent and rings itself with the accent's own border when
 * picked, and sits as a quiet surface-backed hairline chip otherwise.
 */
@Composable
private fun CategoryChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) palette.accentAlpha(36).toComposeColor() else palette.surface.toComposeColor())
            .border(1.dp, if (selected) palette.accent.toComposeColor() else palette.border.toComposeColor(), MaterialTheme.shapes.medium)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) palette.accent.toComposeColor() else palette.textDim.toComposeColor(),
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            color = if (selected) palette.accent.toComposeColor() else palette.textDim.toComposeColor(),
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun ColorDot(color: Color, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val label = Rgba.toHex(Rgba.fromArgb(color.toArgb()))
    Box(
        Modifier
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .then(if (selected) Modifier.border(2.dp, palette.accent.toComposeColor(), CircleShape) else Modifier)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, palette.border.toComposeColor(), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** A spectrum wheel signals that this dot opens the full picker. */
private val spectrumBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
    ),
)

/**
 * A spectrum dot that opens [grid], a popup of colour swatches — shared by the accent and
 * page-colour rows. Until a colour outside the row's presets is chosen the dot shows the
 * spectrum wheel; once one is, it fills with that colour and reads as selected.
 */
@Composable
internal fun ColorPickerDot(
    current: Rgba?,
    custom: Boolean,
    onPick: (Rgba) -> Unit,
    dismissOnPick: Boolean = true,
    enabled: Boolean = true,
    grid: @Composable (onDismiss: () -> Unit, onPick: (Rgba) -> Unit) -> Unit,
) {
    val palette = LocalPalette.current
    val label = stringResource(R.string.material_custom_colour)
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(30.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .semantics { contentDescription = label }
                .then(if (custom) Modifier.border(2.dp, palette.accent.toComposeColor(), CircleShape) else Modifier)
                .padding(4.dp)
                .clip(CircleShape)
                .then(if (custom && current != null) Modifier.background(current.toComposeColor()) else Modifier.background(spectrumBrush))
                .border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable(enabled = enabled) { open = true },
        )
        // A live picker (e.g. the page/ink popup) edits across several taps, so it stays open until a
        // tap outside; a one-shot grid (the accent swatches) closes the moment a colour is chosen.
        if (open) grid({ open = false }, { onPick(it); if (dismissOnPick) open = false })
    }
}

/** Where each [SwatchRole]'s circles are kept: the settings file, shared by every row of that role. */
interface SwatchStore {
    fun swatches(role: SwatchRole): List<Rgba>
    fun setSwatch(role: SwatchRole, index: Int, color: Rgba)
}

/**
 * A role's five colour circles. A tap applies one; a tap on the one already [selected] opens the
 * picker and recolours that circle, as the toolbar's pen swatches do. Emits the circles straight
 * into the caller's row, so a Default chip can sit before them.
 */
@Composable
internal fun SwatchCircles(store: SwatchStore, role: SwatchRole, selected: Rgba?, onPick: (Rgba) -> Unit) {
    var swatches by remember(role) { mutableStateOf(store.swatches(role)) }
    var editing by remember { mutableStateOf<Int?>(null) }
    swatches.forEachIndexed { i, c ->
        Box {
            ColorDot(c.toComposeColor(), selected == c) { if (selected == c) editing = i else onPick(c) }
            if (editing == i) PageColorGridPopup(c, onDismiss = { editing = null }) { picked ->
                swatches = swatches.toMutableList().also { it[i] = picked }
                store.setSwatch(role, i, picked)
                onPick(picked)
            }
        }
    }
}

/** One tappable colour cell in a picker grid. */
/**
 * The colour-code picker shown inside a note/folder's overflow menu: a None row to clear the colour,
 * then the picker's full matrix (pale tints through near-black plus the greyscale row). [onPick] is
 * called with null for None. Rendered directly inside the menu's own dropdown column.
 */
@Composable
internal fun ColorCodeMenuContent(onPick: (Rgba?) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clip(MaterialTheme.shapes.extraSmall).clickable { onPick(null) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(2.dp))
                    .border(1.dp, palette.border.toComposeColor(), RoundedCornerShape(2.dp)),
            )
            Text(stringResource(R.string.none), color = palette.text.toComposeColor(), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        FullSwatchGrid { onPick(it) }
    }
}

/**
 * Page/pattern colour picker: the shared [ColorPickerPopup] (Swatches + Spectrum tabs, full
 * 13×13 range from pale tint to near-black plus a greyscale row, and HEX/RGB fields), so paper-like
 * and muted page backgrounds are reachable, not just the saturated ones. It keeps no recents list.
 */
@Composable
internal fun PageColorGridPopup(initial: Rgba?, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    ColorPickerPopup(initial = initial, recents = emptyList(), onDismiss = onDismiss, onPick = onPick)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(2.dp))
        Text(label, color = LocalPalette.current.text.toComposeColor(), fontSize = 14.sp)
    }
}

/**
 * The app lock's own rows: the PIN controls (set, change, remove — the stored value is always
 * a fresh salted digest, never the PIN) and the re-arm delay. "Cold start only" is the no-delay
 * mode: the gate arms when the app is recreated from process death, and backgrounding never
 * re-locks; the other choices re-arm once the app has stayed away that long.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppLockSection(prefs: Preferences, update: (Preferences) -> Unit) {
    val palette = LocalPalette.current
    var editingPin by remember { mutableStateOf(false) }

    Text(
        stringResource(if (prefs.appLockPin.isEmpty()) R.string.pref_app_lock_help_off else R.string.pref_app_lock_help_on),
        color = palette.textDim.toComposeColor(),
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prefs.appLockPin.isEmpty()) {
            TextButton(onClick = { editingPin = true }) { Text(stringResource(R.string.pin_set)) }
        } else {
            TextButton(onClick = { editingPin = true }) { Text(stringResource(R.string.pin_change)) }
            // Remove asks nothing and just clears the stored digest — the lock is gone at once.
            TextButton(onClick = { update(prefs.copy(appLockPin = PinHash.set(prefs.appLockPin, null))) }) {
                Text(stringResource(R.string.pin_remove))
            }
        }
    }

    FieldLabel(stringResource(R.string.pref_app_lock_delay))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((seconds, label) in listOf(
            0 to R.string.lock_cold_start,
            30 to R.string.lock_after_30s,
            60 to R.string.lock_after_60s,
            300 to R.string.lock_after_5m,
        )) {
            Chip(stringResource(label), prefs.appLockDelay == seconds) { update(prefs.copy(appLockDelay = seconds)) }
        }
    }
    Text(stringResource(R.string.pref_app_lock_delay_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)

    if (editingPin) {
        PinSetDialog(
            title = stringResource(if (prefs.appLockPin.isEmpty()) R.string.pin_set else R.string.pin_change),
            onConfirm = { pin ->
                editingPin = false
                update(prefs.copy(appLockPin = PinHash.set(prefs.appLockPin, pin)))
            },
            onDismiss = { editingPin = false },
        )
    }
}

/**
 * The set/change PIN dialog: two masked fields over the number keyboard, digits only. The
 * confirm button stays disabled until both match and the PIN is 4–6 digits — the same bounds
 * the gate accepts — and a mismatch shows [R.string.pin_mismatch] on the confirm field.
 */
@Composable
private fun PinSetDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm
    fun digitsOnly(raw: String) = raw.filter { it.isDigit() }.take(6)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pin_digits_help), color = LocalPalette.current.textDim.toComposeColor(), fontSize = 12.sp)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pin_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = digitsOnly(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pin_confirm_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.pin_mismatch)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..6 && pin == confirm,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * One side of a custom page, in millimetres. The field keeps whatever is typed so a number can be
 * cleared and retyped; only a value that parses inside the settable range reaches the preference.
 */
@Composable
private fun MillimetreField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(formatMm(value)) }
    // Adopt an outside change (Reset to defaults) without ever rewriting what is being typed.
    LaunchedEffect(value) { if (text.trim().toDoubleOrNull() != value) text = formatMm(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            parseMm(raw)?.let(onChange)
        },
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.width(136.dp),
    )
}

/**
 * Folder backup & restore: pick a folder (any SAF tree — a device folder, or a Drive folder the
 * system provider syncs), choose a cadence, snapshot now, or merge-restore a snapshot. The
 * section is entirely driven by [Editor.backupRoot]/[Editor.backupVersion] and reports progress
 * through the editor's message line, matching how the rest of the pane gives feedback.
 *
 * A session passphrase can be armed for encrypted backups: it lives in a plain `remember` pair
 * below — it survives recomposition but dies with the pane (leaving preferences clears it) and
 * with the process, and it is never written to Settings or any JSON. While armed, Backup Now
 * writes AES-GCM blobs, restore decrypts them, and Verify checks them against the manifest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupSection(
    editor: Editor,
    onPickBackupRoot: () -> Unit,
    prefs: Preferences,
    setBackupEvery: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val ctx = LocalContext.current
    // Refresh the folder/last-backup lines whenever a backup lands or the folder changes.
    val version = editor.backupVersion
    val backupRoot = editor.backupRoot
    val lastBackupAt = remember(version) { editor.lastBackupAt }
    var working by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<com.xnota.platform.BackupManager.Entry>>(emptyList()) }
    var showRestores by remember { mutableStateOf(false) }
    // The session passphrase — in memory only: it survives recomposition, dies with the pane
    // (and with the app). It travels solely into BackupManager calls made from this section.
    var passInput by remember { mutableStateOf("") }
    var sessionPass by remember { mutableStateOf<String?>(null) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (backupRoot == null) stringResource(R.string.backup_no_folder)
                else stringResource(R.string.backup_folder_set),
                color = palette.textDim.toComposeColor(),
                fontSize = 12.sp,
            )
            if (backupRoot != null && lastBackupAt != null) {
                Text(
                    stringResource(
                        R.string.backup_last_at,
                        java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastBackupAt)),
                    ),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                )
            }
        }
        Chip(stringResource(if (backupRoot == null) R.string.backup_choose else R.string.backup_change), true) { onPickBackupRoot() }
        if (backupRoot != null) {
            Chip(stringResource(R.string.backup_forget), false) { editor.clearBackupRoot() }
        }
    }
    FieldLabel(stringResource(R.string.backup_every))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.backup_never), prefs.backupEvery == "never") { setBackupEvery("never") }
        Chip(stringResource(R.string.backup_daily), prefs.backupEvery == "daily") { setBackupEvery("daily") }
        Chip(stringResource(R.string.backup_weekly), prefs.backupEvery == "weekly") { setBackupEvery("weekly") }
    }
    if (backupRoot != null) {
        FieldLabel(stringResource(R.string.backup_passphrase))
        if (sessionPass == null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_passphrase), fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Chip(stringResource(R.string.backup_passphrase_set), passInput.isNotBlank()) {
                    sessionPass = passInput
                    passInput = ""
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.backup_passphrase_on),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Chip(stringResource(R.string.backup_passphrase_clear), true) {
                    sessionPass = null
                    passInput = ""
                }
            }
        }
        Text(stringResource(R.string.backup_passphrase_help), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.backup_now), working.not()) {
                working = true
                editor.backupNow(sessionPass) { n ->
                    working = false
                    editor.message = when (n) {
                        null -> ctx.getString(R.string.backup_failed)
                        else -> ctx.resources.getQuantityString(R.plurals.backup_done, n, n)
                    }
                }
            }
            Chip(stringResource(R.string.backup_restore), working.not()) {
                working = true
                editor.listBackups { list ->
                    working = false
                    backups = list
                    showRestores = true
                }
            }
            Chip(stringResource(R.string.backup_verify), working.not()) {
                working = true
                editor.verifyBackup(sessionPass) { outcome ->
                    working = false
                    editor.message = when (outcome) {
                        null -> ctx.getString(R.string.backup_verify_none)
                        is com.xnota.platform.BackupManager.VerifyOutcome.Ok ->
                            ctx.resources.getQuantityString(R.plurals.backup_verify_ok, outcome.files, outcome.files)
                        is com.xnota.platform.BackupManager.VerifyOutcome.Bad ->
                            ctx.getString(R.string.backup_verify_fail, outcome.first)
                        com.xnota.platform.BackupManager.VerifyOutcome.NoManifest ->
                            ctx.getString(R.string.backup_verify_no_manifest)
                    }
                }
            }
        }
    }
    if (showRestores) {
        Dialog(onDismissRequest = { showRestores = false }) {
            // The app's dialog-chrome recipe on a hand-rolled container: rounded extra-large, the
            // menu surface, a hairline border — title 14sp W700, body 11.5sp dim.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = palette.menuBg.toComposeColor(),
                border = BorderStroke(1.dp, palette.border.toComposeColor()),
            ) {
                Column(Modifier.widthIn(max = 360.dp).padding(16.dp)) {
                    Text(stringResource(R.string.backup_restore), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_restore_help), fontSize = 11.5.sp, color = palette.textDim.toComposeColor())
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (backups.isEmpty()) {
                            Text(stringResource(R.string.backup_none_yet), fontSize = 12.sp, color = palette.textDim.toComposeColor())
                        }
                        for (b in backups) {
                            val label = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(b.timestampMs))
                            Chip(
                                "$label  (${ctx.resources.getQuantityString(R.plurals.backup_files, b.fileCount, b.fileCount)})",
                                false,
                            ) {
                                showRestores = false
                                working = true
                                editor.restoreBackup(b, sessionPass) { outcome ->
                                    working = false
                                    editor.message = when (outcome) {
                                        is com.xnota.platform.BackupManager.RestoreOutcome.Done ->
                                            if (outcome.count == 0) ctx.getString(R.string.backup_nothing_to_restore)
                                            else ctx.resources.getQuantityString(R.plurals.backup_restored, outcome.count, outcome.count)
                                        is com.xnota.platform.BackupManager.RestoreOutcome.DecryptFailed ->
                                            ctx.getString(R.string.backup_decrypt_failed)
                                        com.xnota.platform.BackupManager.RestoreOutcome.Failed ->
                                            ctx.getString(R.string.backup_failed)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Chip(stringResource(R.string.cancel), true) { showRestores = false }
                }
            }
        }
    }
}

@Composable
private fun OptionDropdown(options: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    val label = options.firstOrNull { it.first == selectedId }?.second ?: options.first().second
    Box {
        Box(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = palette.text.toComposeColor(), fontSize = 14.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
