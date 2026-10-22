package com.xnota.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.xnota.R
import com.xnota.core.model.PageEdge
import com.xnota.core.model.PageSize
import com.xnota.core.pal.FontFace
import com.xnota.core.text.TableBorders
import com.xnota.core.tools.MarkupMode
import com.xnota.core.tools.Tool
import com.xnota.core.tools.ToolbarItem
import com.xnota.platform.FontCatalog
import com.xnota.settings.DigitSystem
import com.xnota.settings.ExplorerLayout
import com.xnota.settings.FolderPlacement
import com.xnota.settings.GroupBy
import com.xnota.settings.Preferences
import java.time.DayOfWeek
import java.time.Month

// Display names for core enums, which stay free of Android resources.

@get:StringRes
val Tool.labelRes: Int
    get() = when (this) {
        Tool.PEN -> R.string.tool_pen
        Tool.DASHED -> R.string.tool_dashed
        Tool.CALLIGRAPHY -> R.string.tool_calligraphy
        Tool.SPEED -> R.string.tool_speed
        Tool.TAPER -> R.string.tool_taper
        Tool.HIGHLIGHTER -> R.string.tool_highlighter
        Tool.NEON -> R.string.tool_neon
        Tool.RAINBOW -> R.string.tool_rainbow
        Tool.PENCIL -> R.string.tool_pencil
        Tool.ERASER -> R.string.tool_eraser
        Tool.PAN -> R.string.tool_pan
        Tool.SELECT -> R.string.tool_select
        Tool.LASSO -> R.string.tool_lasso
        Tool.SCREENSHOT -> R.string.tool_screenshot
        Tool.SHAPE -> R.string.tool_shape
        Tool.FILL -> R.string.tool_fill
        Tool.TEXT -> R.string.tool_text
        Tool.TEXT_BOX -> R.string.tool_text_box
        Tool.IMAGE -> R.string.tool_image
        Tool.MARKUP -> R.string.tool_markup
    }

@get:StringRes
val MarkupMode.labelRes: Int
    get() = when (this) {
        MarkupMode.SELECT -> R.string.tool_select
        MarkupMode.HIGHLIGHT -> R.string.markup_highlight
        MarkupMode.UNDERLINE -> R.string.underline
        MarkupMode.STRIKEOUT -> R.string.strikethrough
        MarkupMode.SQUIGGLY -> R.string.markup_squiggly
    }


@get:StringRes
val ToolbarItem.labelRes: Int
    get() = when (this) {
        ToolbarItem.HOME -> R.string.toolbar_home
        ToolbarItem.TITLE -> R.string.toolbar_title
        ToolbarItem.SIDEBAR -> R.string.toolbar_sidebar
        ToolbarItem.PEN -> R.string.tool_pen
        ToolbarItem.DASHED -> R.string.tool_dashed
        ToolbarItem.CALLIGRAPHY -> R.string.tool_calligraphy
        ToolbarItem.SPEED -> R.string.tool_speed
        ToolbarItem.TAPER -> R.string.tool_taper
        ToolbarItem.HIGHLIGHTER -> R.string.tool_highlighter
        ToolbarItem.NEON -> R.string.tool_neon
        ToolbarItem.RAINBOW -> R.string.tool_rainbow
        ToolbarItem.PENCIL -> R.string.tool_pencil
        ToolbarItem.ERASER -> R.string.tool_eraser
        ToolbarItem.PAN -> R.string.tool_pan
        ToolbarItem.SELECT -> R.string.tool_select
        ToolbarItem.LASSO -> R.string.tool_lasso
        ToolbarItem.SCREENSHOT -> R.string.tool_screenshot
        ToolbarItem.WAND -> R.string.tool_wand
        ToolbarItem.SHAPE -> R.string.tool_shape
        ToolbarItem.FILL -> R.string.tool_fill
        ToolbarItem.RULER -> R.string.tool_ruler
        ToolbarItem.TEXT -> R.string.tool_text
        ToolbarItem.TEXT_BOX -> R.string.tool_text_box
        ToolbarItem.MARKUP -> R.string.tool_markup
        ToolbarItem.IMAGE -> R.string.tool_image
        ToolbarItem.UNDO -> R.string.undo
        ToolbarItem.REDO -> R.string.redo
        ToolbarItem.PAGE_NAV -> R.string.toolbar_page_nav
        ToolbarItem.STYLES -> R.string.toolbar_styles
        ToolbarItem.MARGINS -> R.string.toolbar_margins
        ToolbarItem.VIEW -> R.string.toolbar_view
        ToolbarItem.ZOOM -> R.string.toolbar_zoom
        ToolbarItem.FIT -> R.string.toolbar_fit
        ToolbarItem.ZOOM_LOCK -> R.string.toolbar_zoom_lock
        ToolbarItem.FULLSCREEN -> R.string.toolbar_fullscreen
        ToolbarItem.COLORS -> R.string.toolbar_colours
        ToolbarItem.WAYPOINTS -> R.string.toolbar_waypoints
        ToolbarItem.MINIMAP -> R.string.toolbar_minimap
    }

@get:StringRes
val PageEdge.labelRes: Int
    get() = when (this) {
        PageEdge.LEFT -> R.string.edge_left
        PageEdge.RIGHT -> R.string.edge_right
        PageEdge.TOP -> R.string.edge_top
        PageEdge.BOTTOM -> R.string.edge_bottom
    }

@get:StringRes
val TableBorders.labelRes: Int
    get() = when (this) {
        TableBorders.ALL -> R.string.table_borders_all
        TableBorders.OUTER -> R.string.table_borders_outer
        TableBorders.HORIZONTAL -> R.string.table_borders_rows
        TableBorders.NONE -> R.string.table_borders_none
    }

@Composable
fun pageSizeLabel(size: PageSize): String = when (size) {
    PageSize.CUSTOM -> stringResource(R.string.page_size_custom)
    PageSize.LETTER -> stringResource(R.string.page_size_letter)
    PageSize.LEGAL -> stringResource(R.string.page_size_legal)
    PageSize.SLIDE_16_9 -> stringResource(R.string.page_size_slide_16_9)
    PageSize.SLIDE_4_3 -> stringResource(R.string.page_size_slide_4_3)
    else -> size.displayName
}

@Composable
fun fontLabel(face: FontFace): String = when (face) {
    FontFace.SANS -> stringResource(R.string.font_sans)
    FontFace.SERIF -> stringResource(R.string.font_serif)
    FontFace.MONO -> stringResource(R.string.font_mono)
    FontFace.HAND -> stringResource(R.string.font_hand)
    else -> FontCatalog.label(face)
}

@get:StringRes
val ExplorerLayout.labelRes: Int
    get() = when (this) {
        ExplorerLayout.GRID -> R.string.layout_grid
        ExplorerLayout.GALLERY -> R.string.layout_gallery
        ExplorerLayout.LIST -> R.string.layout_list
        ExplorerLayout.COLUMNS -> R.string.layout_columns
        ExplorerLayout.TIMELINE -> R.string.layout_timeline
    }

@get:StringRes
val GroupBy.labelRes: Int
    get() = when (this) {
        GroupBy.NONE -> R.string.none
        GroupBy.DATE -> R.string.group_date
        GroupBy.KIND -> R.string.group_kind
        GroupBy.COLOUR -> R.string.colour
    }

/** The group-by chip's label: "Grouped by date", or "No grouping". */
@get:StringRes
val GroupBy.chipRes: Int
    get() = when (this) {
        GroupBy.NONE -> R.string.no_grouping
        GroupBy.DATE -> R.string.grouped_by_date
        GroupBy.KIND -> R.string.grouped_by_kind
        GroupBy.COLOUR -> R.string.grouped_by_colour
    }

@get:StringRes
val FolderPlacement.labelRes: Int
    get() = when (this) {
        FolderPlacement.TOP -> R.string.folders_on_top
        FolderPlacement.MIXED -> R.string.folders_mixed
        FolderPlacement.HIDDEN -> R.string.folders_hidden
    }

/**
 * [ExplorerWords] read from string resources.
 *
 * [arabicDigits] shapes every number this class formats for display (Arabic-Indic ٠-٩, the
 * [Preferences.digitSystem] pref — never anything written to disk); [hijri] switches the date
 * wording to the Hijri calendar ([Preferences.dateSystem] pref), whose month names come from
 * the `hijri_months` array. Gregorian month names keep coming from `months`/`months_short`.
 */
internal class ResourceWords(
    private val res: Resources,
    arabicDigits: Boolean = false,
    override val hijri: Boolean = false,
) : ExplorerWords {
    private val months = res.getStringArray(R.array.months)
    private val shortMonths = res.getStringArray(R.array.months_short)
    private val weekdays = res.getStringArray(R.array.weekdays_short)
    private val hijriMonths = res.getStringArray(R.array.hijri_months)
    private val shape: (String) -> String = if (arabicDigits) {
        { s: String -> DigitSystem.apply(s) }
    } else {
        { s: String -> s }
    }
    override fun digits(s: String): String = shape(s)
    override fun month(m: Month): String = months[m.value - 1]
    override fun shortMonth(m: Month): String = shortMonths[m.value - 1]
    override fun shortWeekday(d: DayOfWeek): String = weekdays[d.value - 1]
    override val today: String = res.getString(R.string.today)
    override val yesterday: String = res.getString(R.string.yesterday)
    override val earlierThisWeek: String = res.getString(R.string.earlier_this_week)
    override val lastWeek: String = res.getString(R.string.last_week)
    override fun earlierIn(m: Month): String = res.getString(R.string.earlier_in, month(m))
    override fun monthYear(m: Month, year: Int): String = shape(res.getString(R.string.month_year, month(m), year))
    override val justNow: String = res.getString(R.string.just_now)
    override fun minutesAgo(n: Long): String = shape(res.getString(R.string.minutes_ago, n))
    override fun hoursAgo(n: Long): String = shape(res.getString(R.string.hours_ago, n))
    override fun daysAgo(n: Long): String = shape(res.getQuantityString(R.plurals.days_ago, n.toInt(), n))
    override fun dayMonth(day: Int, m: Month): String = shape(res.getString(R.string.day_month, day, shortMonth(m)))
    override fun dayMonthYear(day: Int, m: Month, year: Int): String = shape(res.getString(R.string.day_month_year, day, shortMonth(m), year))
    override fun withTime(date: String, clock: String): String = res.getString(R.string.date_with_time, date, clock)
    override fun clock12(hour: Int, minute: Int, pm: Boolean): String =
        shape(res.getString(R.string.clock_12h, hour, minute, res.getString(if (pm) R.string.pm else R.string.am)))
    override fun opened(ago: String): String = res.getString(R.string.opened_ago, ago)
    override fun folders(n: Int): String = shape(res.getQuantityString(R.plurals.folders_count, n, n))
    override fun files(n: Int): String = shape(res.getQuantityString(R.plurals.files_count, n, n))
    override fun items(n: Int): String = shape(res.getQuantityString(R.plurals.items_count, n, n))

    // Hijri wording reuses the Gregorian format strings with hijri_months names; the digits in
    // day/year arguments are shaped like every other number above.
    override fun hijriMonth(n: Int): String = hijriMonths[n - 1]
    override fun hijriDayMonth(day: Int, month: Int): String = shape(res.getString(R.string.day_month, day, hijriMonth(month)))
    override fun hijriDayMonthYear(day: Int, month: Int, year: Int): String = shape(res.getString(R.string.day_month_year, day, hijriMonth(month), year))
    override fun hijriMonthYear(month: Int, year: Int): String = shape(res.getString(R.string.month_year, hijriMonth(month), year))
    override fun hijriEarlierIn(n: Int): String = res.getString(R.string.earlier_in, hijriMonth(n))
    override fun kinds(k: EntryKind): String = res.getString(
        when (k) {
            EntryKind.FOLDER -> R.string.kind_folders
            EntryKind.NOTE -> R.string.kind_notes
            EntryKind.PDF -> R.string.kind_pdf_notes
            EntryKind.CANVAS -> R.string.kind_canvases
        },
    )
    override fun hue(h: Hue): String = res.getString(
        when (h) {
            Hue.BLACK -> R.string.hue_black
            Hue.WHITE -> R.string.hue_white
            Hue.GREY -> R.string.hue_grey
            Hue.RED -> R.string.hue_red
            Hue.ORANGE -> R.string.hue_orange
            Hue.YELLOW -> R.string.hue_yellow
            Hue.GREEN -> R.string.hue_green
            Hue.TEAL -> R.string.hue_teal
            Hue.BLUE -> R.string.hue_blue
            Hue.PURPLE -> R.string.hue_purple
            Hue.PINK -> R.string.hue_pink
        },
    )
    override val noColour: String = res.getString(R.string.no_colour)
}

@Composable
internal fun rememberExplorerWords(prefs: Preferences, appLanguage: String): ExplorerWords {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // The digit/calendar prefs thread in through the same snapshot pattern the explorer's other
    // preferences use (Backstage reads `remember(editor.prefsVersion) { editor.preferences }` and
    // passes the result here); appLanguage rides Editor's observable copy so "auto" digits follow
    // the live app language. Only the three values the wording depends on key the remember, so
    // unrelated preference changes leave the built words object alone.
    return remember(configuration, prefs.digitSystem, prefs.dateSystem, appLanguage) {
        ResourceWords(
            context.resources,
            arabicDigits = DigitSystem.active(prefs.digitSystem, appLanguage),
            hijri = prefs.dateSystem == "hijri",
        )
    }
}
