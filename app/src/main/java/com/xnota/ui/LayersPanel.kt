package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.model.CanvasItem
import com.xnota.core.model.ImageItem
import com.xnota.core.model.ShapeItem
import com.xnota.core.model.Stroke
import com.xnota.core.model.TextItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/** One row of the layers panel: the item plus everything the row shows about it. */
data class LayerRow(
    val item: CanvasItem,
    /** A short human summary: "Stroke · 42 pts", "Rectangle", the text's first words. */
    val summary: String,
    val hidden: Boolean,
    val locked: Boolean,
)

/** What the layers panel needs from whichever editor is open. */
interface LayersHost {
    /** The current surface's items, topmost first (z order as the user sees it). */
    fun layersRows(): List<LayerRow>

    /** Hide or reveal [item]; one undo step, applied immediately. */
    fun setLayerHidden(item: CanvasItem, hidden: Boolean)

    /** Lock or unlock [item]; one undo step, applied immediately. */
    fun setLayerLocked(item: CanvasItem, locked: Boolean)

    /** Move [item] one step towards the viewer (up in z, up in the panel). One undo step. */
    fun moveLayerUp(item: CanvasItem)

    /** Move [item] one step away from the viewer. One undo step. */
    fun moveLayerDown(item: CanvasItem)

    /** Select [item] on the canvas and close the panel, so a row is a shortcut to the thing. */
    fun pickLayerItem(item: CanvasItem)
}

/**
 * The layers panel (the fork): the current page or canvas as a z-ordered list — every item with
 * its eye (hidden), its lock, a step up and down in z, and a tap that selects the thing itself on
 * the canvas. The whole point is that hiding is *forgetting on purpose with a way back*, so the
 * panel is deliberately boring: a flat list, no nesting, no groups, no "layers" to create — the
 * items are the layers.
 */
@Composable
fun LayersPanel(host: LayersHost, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val rows = host.layersRows()
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(palette.panel.toComposeColor())
            .width(320.dp)
            .heightIn(max = 520.dp)
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.layers_title),
                color = palette.text.toComposeColor(),
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(NotaIcons.close, stringResource(R.string.close_pane), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = palette.border.toComposeColor())
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.layers_empty),
                color = palette.textDim.toComposeColor(),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    LayerRowView(host, row, canRaise = index > 0, canSink = index < rows.size - 1, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun LayerRowView(
    host: LayersHost,
    row: LayerRow,
    canRaise: Boolean,
    canSink: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { host.pickLayerItem(row.item); onDismiss() }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            layerIcon(row.item),
            contentDescription = null,
            tint = if (row.hidden) palette.textDim.toComposeColor() else palette.text.toComposeColor(),
            modifier = Modifier.size(16.dp),
        )
        Text(
            row.summary,
            color = if (row.hidden) palette.textDim.toComposeColor() else palette.text.toComposeColor(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { host.moveLayerUp(row.item) },
            enabled = canRaise,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(NotaIcons.prev, stringResource(R.string.raise_layer), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(14.dp))
        }
        IconButton(
            onClick = { host.moveLayerDown(row.item) },
            enabled = canSink,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(NotaIcons.prev, stringResource(R.string.sink_layer), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f })
        }
        IconButton(
            onClick = { host.setLayerLocked(row.item, !row.locked) },
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                if (row.locked) NotaIcons.lock else NotaIcons.unlock,
                stringResource(R.string.lock),
                tint = if (row.locked) palette.accent.toComposeColor() else palette.textDim.toComposeColor(),
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = { host.setLayerHidden(row.item, !row.hidden) },
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                if (row.hidden) NotaIcons.eyeOff else NotaIcons.eye,
                stringResource(R.string.hide),
                tint = if (row.hidden) palette.accent.toComposeColor() else palette.textDim.toComposeColor(),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun layerIcon(item: CanvasItem) = when (item) {
    is Stroke -> NotaIcons.highlight
    is ShapeItem -> NotaIcons.shape
    is TextItem -> NotaIcons.text
    is ImageItem -> NotaIcons.image
    else -> NotaIcons.shape
}
