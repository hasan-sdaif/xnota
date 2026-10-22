package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.geometry.AlignMode
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/**
 * The selection menu's Align popup: the six ways to line a selection up against its own bounds
 * (a lone item against its page), and the two ways to spread it out. Every op is one undo step and
 * keeps the selection, so the popup can stay open while the result is judged — one op, look, another.
 *
 * Structure follows [ShapeConfigPopup]: a dropdown anchored where it was opened, its title, then
 * the icon rows that do the work.
 */
@Composable
fun AlignPopup(host: SelectionMenuHost, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(246.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
            PopupTitle(stringResource(R.string.title_align))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlignChip(NotaIcons.alignEdgeLeft, R.string.align_left) { host.alignSelection(AlignMode.LEFT) }
                AlignChip(NotaIcons.alignCentreH, R.string.align_centre_h) { host.alignSelection(AlignMode.CENTER_H) }
                AlignChip(NotaIcons.alignEdgeRight, R.string.align_right) { host.alignSelection(AlignMode.RIGHT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                AlignChip(NotaIcons.alignEdgeTop, R.string.align_top) { host.alignSelection(AlignMode.TOP) }
                AlignChip(NotaIcons.alignCentreV, R.string.align_centre_v) { host.alignSelection(AlignMode.CENTER_V) }
                AlignChip(NotaIcons.alignEdgeBottom, R.string.align_bottom) { host.alignSelection(AlignMode.BOTTOM) }
            }
            HorizontalDivider(color = palette.border.toComposeColor(), modifier = Modifier.padding(vertical = 8.dp))
            PopupTitle(stringResource(R.string.title_distribute))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlignChip(NotaIcons.distributeH, R.string.distribute_h, enabled = host.canDistributeSelection()) {
                    host.distributeSelection(vertical = false)
                }
                AlignChip(NotaIcons.distributeV, R.string.distribute_v, enabled = host.canDistributeSelection()) {
                    host.distributeSelection(vertical = true)
                }
            }
            Text(
                stringResource(R.string.align_hint),
                color = palette.textDim.toComposeColor(),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One align/distribute op: an icon chip with its label as the tooltip, dimmed when it can't act. */
@Composable
private fun AlignChip(icon: ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val text = stringResource(label)
    Tooltip(text) {
        Box(
            Modifier
                .size(56.dp, 44.dp)
                .clip(CircleShape)
                .background(palette.surface.toComposeColor())
                .border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = if (enabled) palette.text.toComposeColor() else palette.textDim.toComposeColor().copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
