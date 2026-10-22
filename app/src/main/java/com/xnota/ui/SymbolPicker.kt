package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.text.SymbolCatalog
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/** Every category's label as its localized string resource. */
private fun labelRes(label: String): Int = when (label) {
    "symbols_arabic_punct" -> R.string.symbols_arabic_punct
    "symbols_arabic_marks" -> R.string.symbols_arabic_marks
    "symbols_common" -> R.string.symbols_common
    else -> R.string.symbols_emoji
}

/**
 * The flow editor's symbol & emoji picker (the fork): the Arabic punctuation and Quranic marks a
 * keyboard leaves out, plus the everyday symbols, in tap-to-insert tiles. Pure display — picking
 * a tile tells the caller, which inserts it at the caret.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolPicker(onPick: (String) -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(312.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (category in SymbolCatalog.CATEGORIES) {
            Text(
                androidx.compose.ui.res.stringResource(labelRes(category.label)),
                color = palette.textDim.toComposeColor(),
                fontSize = 11.sp, fontWeight = FontWeight.Medium,
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (symbol in category.symbols) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(palette.surface.toComposeColor())
                            .clickable { onPick(symbol) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(symbol, fontSize = 18.sp, color = palette.text.toComposeColor())
                    }
                }
            }
        }
    }
}
