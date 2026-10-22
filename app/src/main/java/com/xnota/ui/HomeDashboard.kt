package com.xnota.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/** One quick-action card of the home dashboard: the glyph it shows, its label's string resource and the create mode it starts (null imports a PDF instead). */
internal data class DashboardQuickAction(val icon: ImageVector, @StringRes val labelRes: Int, val mode: CreateMode?)

/**
 * The dashboard header's quick actions, as data: the four cards the reference design's home opens
 * with, in display order, riding the create menu's own label strings and glyphs so the header can
 * never drift from the menu that owns the same actions.
 */
internal val dashboardQuickActions: List<DashboardQuickAction> = listOf(
    DashboardQuickAction(NotaIcons.edit, R.string.new_note_menu, CreateMode.FILE),
    DashboardQuickAction(NotaIcons.canvas, R.string.new_canvas_menu, CreateMode.CANVAS),
    DashboardQuickAction(NotaIcons.importDoc, R.string.import_pdf, null),
    DashboardQuickAction(NotaIcons.newFolder, R.string.new_folder_menu, CreateMode.FOLDER),
)

/**
 * The one-line description under a quick card's title, keyed by the action's create mode (null is
 * the PDF import). A mapping, not a field, so the pure action list above stays exactly what the
 * menu and the tests read and the descriptions can never drift out of order with it.
 */
@StringRes
internal fun quickActionSub(mode: CreateMode?): Int = when (mode) {
    CreateMode.FILE -> R.string.quick_sub_note
    CreateMode.CANVAS -> R.string.quick_sub_canvas
    null -> R.string.quick_sub_import
    CreateMode.FOLDER -> R.string.quick_sub_folder
    CreateMode.NONE -> R.string.quick_sub_note
}

/** The home dashboard's four numbers, as counted from the entries the recent shelf actually lists. */
internal data class HomeStats(
    /** Files that are not PDFs: the notebooks and quick notes. */
    val notes: Int,
    val pdfs: Int,
    /** Pages summed over the entries that report a page count; 0 when none of them does. */
    val pages: Int,
    /** The newest last-modified time among the entries, 0 when the list is empty. */
    val lastModified: Long,
)

/**
 * Counts the shelf's own data — no invented numbers: every card shows something the recent listing
 * really carries. Folders are skipped (Recent never lists them, and the counts shouldn't grow if
 * that ever changes); page counts come in through [pagesOf] so the caller stays free of the meta
 * cache, and the PDF test is a lambda for the same reason.
 */
internal fun homeStats(entries: List<BrowseEntry>, isPdf: (BrowseEntry) -> Boolean, pagesOf: (BrowseEntry) -> Int): HomeStats {
    var notes = 0
    var pdfs = 0
    var pages = 0
    var last = 0L
    for (e in entries) {
        if (e.isDir) continue
        if (isPdf(e)) pdfs++ else notes++
        pages += pagesOf(e)
        if (e.modified > last) last = e.modified
    }
    return HomeStats(notes, pdfs, pages, last)
}

/**
 * The home screen's whole presentation, the reference design's opening block: the hero card with
 * its notebook and its two actions, the four numbers counted off the recent shelf, then the quick
 * cards as row tiles with a line of description each. Shown only where the shelves gate already
 * shows it (the home root); the explorer beneath keeps its own structure.
 */
@Composable
internal fun HomePresentation(
    onCreateMode: (CreateMode) -> Unit,
    onImportPdf: () -> Unit,
    recents: List<RecentEntry>,
    kindOf: (BrowseEntry) -> EntryKind,
    pagesOf: (BrowseEntry) -> Int,
    lastActivity: String,
    digits: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HomeHero(onCreateMode, onImportPdf)
        Spacer(Modifier.height(12.dp))
        HomeStatsRow(recents.map { it.entry }, kindOf, pagesOf, lastActivity, digits)
        Spacer(Modifier.height(12.dp))
        HomeQuickActions(onCreateMode, onImportPdf)
    }
}

/**
 * The reference's hero: an eyebrow over a two-tone title and its copy, the primary "start a
 * notebook" and the quiet "import PDF" actions, and — once the pane is wide enough — the tilted
 * emerald notebook standing in for the app itself.
 */
@Composable
private fun HomeHero(onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = MaterialTheme.shapes.extraLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 560.dp
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.menuBg.toComposeColor())
                .background(Brush.linearGradient(listOf(palette.accentAlpha(25).toComposeColor(), Color.Transparent)))
                .border(1.dp, palette.accentAlpha(46).toComposeColor(), shape)
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent.toComposeColor()),
                    )
                    Text(
                        stringResource(R.string.hero_eyebrow),
                        color = palette.accent.toComposeColor(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W800,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val partA = stringResource(R.string.hero_title_a)
                val partB = stringResource(R.string.hero_title_b)
                val title = remember(partA, partB) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.text.toComposeColor(), fontSize = 24.sp, fontWeight = FontWeight.W800)) { append(partA) }
                        append(" ")
                        withStyle(SpanStyle(color = palette.accent.toComposeColor(), fontSize = 24.sp, fontWeight = FontWeight.W800)) { append(partB) }
                    }
                }
                Text(title)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.hero_copy),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.5.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The primary action: a notebook of one's own, straight into the create flow.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accent.toComposeColor())
                            .clickable { onCreateMode(CreateMode.FILE) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(NotaIcons.edit, null, tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.hero_start),
                            color = palette.onAccent.toComposeColor(),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                    // The quiet action: bring a PDF in to read and mark up.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface.toComposeColor())
                            .border(1.dp, palette.border.toComposeColor(), RoundedCornerShape(12.dp))
                            .clickable(onClick = onImportPdf)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(NotaIcons.importDoc, null, tint = palette.textDim.toComposeColor(), modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.import_pdf),
                            color = palette.textDim.toComposeColor(),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.W600,
                        )
                    }
                }
            }
            if (wide) {
                Spacer(Modifier.width(26.dp))
                HeroNotebook()
            }
        }
    }
}

/**
 * The decorative notebook of the hero's wide layout: a tilted dark-emerald cover, its mark and
 * three hairlines of "writing". Purely visual — clipped, and carrying no meaning a screen reader
 * needs (the title beside it says everything).
 */
@Composable
private fun HeroNotebook(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(topStart = 9.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 9.dp)
    val line = palette.textDim.withAlpha(56).toComposeColor()
    Box(
        modifier
            .graphicsLayer { rotationZ = -7f }
            .size(width = 108.dp, height = 140.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0D3B2C), Color(0xFF061A16))))
            .border(1.dp, palette.accentAlpha(102).toComposeColor(), shape)
            .padding(16.dp),
    ) {
        Column {
            Text("X.", color = palette.accent.toComposeColor(), fontSize = 24.sp, fontWeight = FontWeight.W900)
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth(0.82f).height(1.dp).background(line))
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth(0.82f).height(1.dp).background(line))
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth(0.82f).height(1.dp).background(line))
        }
    }
}

/** One stat of the row: its glyph, its label's resource and the string already shaped for display. */
private data class StatCardModel(val icon: ImageVector, @StringRes val labelRes: Int, val value: String)

/**
 * The four real numbers: what the recent shelf holds — notebooks and notes, PDFs, the pages they
 * report (falling back to a plain item count when nothing reports pages), and when something was
 * last touched. A row of four on wide panes, a 2×2 grid below that, exactly as the reference deals
 * its stats.
 */
@Composable
private fun HomeStatsRow(
    entries: List<BrowseEntry>,
    kindOf: (BrowseEntry) -> EntryKind,
    pagesOf: (BrowseEntry) -> Int,
    lastActivity: String,
    digits: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val stats = remember(entries, kindOf, pagesOf) { homeStats(entries, { kindOf(it) == EntryKind.PDF }, pagesOf) }
    val cards = listOf(
        StatCardModel(NotaIcons.bookOpen, R.string.stat_files, digits(stats.notes.toString())),
        StatCardModel(NotaIcons.pdfFile, R.string.stat_pdfs, digits(stats.pdfs.toString())),
        if (stats.pages > 0) StatCardModel(NotaIcons.pages, R.string.stat_pages, digits(stats.pages.toString()))
        else StatCardModel(NotaIcons.pages, R.string.stat_items, digits((stats.notes + stats.pdfs).toString())),
        StatCardModel(NotaIcons.clock, R.string.stat_last_activity, lastActivity.ifBlank { "—" }),
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { c -> StatCard(c, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(cards[0], Modifier.weight(1f))
                    StatCard(cards[1], Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(cards[2], Modifier.weight(1f))
                    StatCard(cards[3], Modifier.weight(1f))
                }
            }
        }
    }
}

/** One stat card: the glyph on its tinted tile beside the label and the big number. */
@Composable
private fun StatCard(card: StatCardModel, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier
            .clip(shape)
            .background(palette.menuBg.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(palette.accentAlpha(23).toComposeColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(card.icon, null, tint = palette.accent.toComposeColor(), modifier = Modifier.size(18.dp))
        }
        Column {
            Text(
                stringResource(card.labelRes),
                color = palette.textDim.toComposeColor(),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.value,
                color = palette.text.toComposeColor(),
                fontSize = 19.sp,
                fontWeight = FontWeight.W800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The quick actions as the reference's row cards: the glyph on its bordered emerald tile, the
 * title over a line of description, a chevron at the far end. Equal widths on wide panes, a fixed
 * width and a scrolling row below that — the dealing the old header already used.
 */
@Composable
private fun HomeQuickActions(onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dashboardQuickActions.forEach { action -> QuickActionCard(action, onCreateMode, onImportPdf, Modifier.weight(1f)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dashboardQuickActions.forEach { action -> QuickActionCard(action, onCreateMode, onImportPdf, Modifier.width(210.dp)) }
            }
        }
    }
}

/** One quick-action card: the accent glyph on its tile, the label with its description, and the forward chevron. */
@Composable
private fun QuickActionCard(action: DashboardQuickAction, onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(17.dp)
    val tile = RoundedCornerShape(14.dp)
    val mode = action.mode
    Row(
        modifier
            .heightIn(min = 74.dp)
            .clip(shape)
            .background(palette.menuBg.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), shape)
            .clickable { if (mode != null) onCreateMode(mode) else onImportPdf() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(tile)
                .background(palette.accentAlpha(30).toComposeColor())
                .border(1.dp, palette.accentAlpha(41).toComposeColor(), tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, null, tint = palette.accent.toComposeColor(), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(action.labelRes),
                color = palette.text.toComposeColor(),
                fontSize = 13.sp,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(quickActionSub(mode)),
                color = palette.textDim.toComposeColor(),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DirectionalIcon(NotaIcons.next, null, tint = palette.textDim.toComposeColor(), modifier = Modifier.size(16.dp))
    }
}
