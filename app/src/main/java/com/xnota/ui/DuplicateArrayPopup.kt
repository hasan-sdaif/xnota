package com.xnota.ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.canvas.DuplicateArrays
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/**
 * The selection menu's "Duplicate array…" popup: [DuplicateArrays]' two ways to copy a selection
 * around itself — Radial, copies fanned over a span about the selection box's centre, and Grid,
 * rows × columns stepping out from the selection's own top-left with a gap. The count line says
 * how many copies Apply will land; the copies arrive selected, as one undo step, exactly the way
 * plain duplicate's one copy does.
 *
 * Structure follows [AlignPopup]: a dropdown anchored where it was opened, its title, then the
 * controls that do the work.
 */
@Composable
fun DuplicateArrayPopup(host: SelectionMenuHost, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var radial by remember { mutableStateOf(true) }
    var copies by remember { mutableStateOf(6) }
    var span by remember { mutableStateOf(360f) }
    var rows by remember { mutableStateOf(2) }
    var cols by remember { mutableStateOf(2) }
    var gap by remember { mutableStateOf(16f) }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(246.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
            PopupTitle(stringResource(R.string.duplicate_array))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip(stringResource(R.string.array_radial), radial) { radial = true }
                ModeChip(stringResource(R.string.array_grid), !radial) { radial = false }
            }
            val held = host.selectionItemCount
            val made: Int
            if (radial) {
                ArrayCountRow(stringResource(R.string.array_copies), copies, DuplicateArrays.MIN_COPIES..DuplicateArrays.MAX_COPIES) { copies = it }
                ArraySliderRow(stringResource(R.string.array_span), span, 30f..360f, "°") { span = it }
                made = held * copies.coerceIn(DuplicateArrays.MIN_COPIES, DuplicateArrays.MAX_COPIES)
            } else {
                ArrayCountRow(stringResource(R.string.array_rows), rows, 1..GRID_MAX) { rows = it }
                ArrayCountRow(stringResource(R.string.array_cols), cols, 1..GRID_MAX) { cols = it }
                ArraySliderRow(stringResource(R.string.array_gap), gap, 0f..100f, "") { gap = it }
                // Row 0 col 0 is the original's own slot, so the grid's copies are the rest of the cells.
                made = held * (rows.coerceIn(1, GRID_MAX) * cols.coerceIn(1, GRID_MAX) - 1)
            }
            Text(
                pluralStringResource(R.plurals.array_count_n, made, made),
                color = palette.textDim.toComposeColor(),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            HorizontalDivider(color = palette.border.toComposeColor(), modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.width(218.dp)) {
                TextButton(onClick = {
                    if (radial) host.duplicateRadialArray(copies, span.toDouble())
                    else host.duplicateGridArray(rows, cols, gap.toDouble())
                    onDismiss()
                }) { Text(stringResource(R.string.array_apply)) }
            }
        }
    }
}

/** The most rows or columns the grid steppers offer; the pure layer still clamps to its own bound. */
private const val GRID_MAX = 8

/** One array count: a label, a minus/plus pair, and the count between them (as ShapePopup's counts). */
@Composable
private fun ArrayCountRow(label: String, count: Int, range: IntRange, onChange: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text.toComposeColor(), fontSize = 12.sp, modifier = Modifier.width(120.dp))
        ArrayStepButton("−", enabled = count > range.first) { onChange(count - 1) }
        Text("$count", color = palette.text.toComposeColor(), fontSize = 14.sp, modifier = Modifier.width(36.dp).padding(horizontal = 8.dp))
        ArrayStepButton("+", enabled = count < range.last) { onChange(count + 1) }
    }
}

@Composable
private fun ArrayStepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .border(1.dp, palette.border.toComposeColor(), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = (if (enabled) palette.text else palette.textDim).toComposeColor(), fontSize = 16.sp)
    }
}

/** One array slider, its value shown beside its label with the [unit] the row is counted in. */
@Composable
private fun ArraySliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    val palette = LocalPalette.current
    Column {
        Text(
            "$label  ${"%.0f".format(value)}$unit",
            color = palette.text.toComposeColor(),
            fontSize = 12.sp,
        )
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}
