package com.xnota.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * The home screen's dashboard header, the reference design's opening block above the shelves: a
 * welcome line with its dim subtitle, then the quick actions as rounded cards — new note, new
 * canvas, import PDF, new folder. Wide panes deal the four cards equal widths; narrow ones keep
 * them at a fixed width and scroll the row instead.
 */
@Composable
internal fun DashboardHeader(onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_welcome_title), color = palette.text.toComposeColor(), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(R.string.home_welcome_subtitle), color = palette.textDim.toComposeColor(), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    dashboardQuickActions.forEach { action -> QuickActionCard(action, onCreateMode, onImportPdf, Modifier.weight(1f)) }
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    dashboardQuickActions.forEach { action -> QuickActionCard(action, onCreateMode, onImportPdf, Modifier.width(132.dp)) }
                }
            }
        }
    }
}

/** One quick-action card: the accent glyph over its label in a bordered, surface-backed, rounded box — the reference's card look. */
@Composable
private fun QuickActionCard(action: DashboardQuickAction, onCreateMode: (CreateMode) -> Unit, onImportPdf: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = MaterialTheme.shapes.medium
    val mode = action.mode
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(palette.menuBg.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), shape)
            .clickable { if (mode != null) onCreateMode(mode) else onImportPdf() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, null, tint = palette.accent.toComposeColor(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(
            stringResource(action.labelRes),
            color = palette.text.toComposeColor(),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
