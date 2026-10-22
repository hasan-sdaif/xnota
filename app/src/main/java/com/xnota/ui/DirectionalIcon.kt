package com.xnota.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * xnota: renders one of the app's direction-indicating glyphs — the
 * undo/redo arrows, the prev/next chevrons, the back-to-home arrow — mirrored
 * automatically in RTL layouts, the way Material's AutoMirrored icons do.
 *
 * Non-directional glyphs (pens, the clock, folders, download trays…) must keep
 * using [Icon] directly: they mean the same thing whichever way the UI reads.
 *
 * Call sites that route through the shared [ToolbarIcon]/[ExplorerIcon]
 * wrappers pass their `mirrored = true` flag instead of using this directly.
 */
@Composable
fun DirectionalIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector,
        contentDescription,
        modifier.then(if (rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
        tint,
    )
}
