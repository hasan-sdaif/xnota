package com.xnota.ui

import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xnota.R

/**
 * Fullscreen launch animation: the XNota brand splash ([R.drawable.nota_loader] — a
 * 24-frame ~1.9s [AnimationDrawable]) in which the mint pen draws the X-nib mark,
 * the wordmark XNota / إكس نوتا settles in and the tagline fades up. Centred on the
 * brand stage #0C1818, matching the frames' background exactly, so there's no edge
 * seam. Shown while the session restores, then faded out.
 */
@Composable
fun NotaLoader(modifier: Modifier = Modifier) {
    val cfg = LocalConfiguration.current
    // Scale with the short edge but cap it, so phones render big without tablets ballooning.
    val shortEdge = minOf(cfg.screenWidthDp, cfg.screenHeightDp)
    val side = minOf(shortEdge * 0.55f, 288f).dp
    Box(
        modifier.fillMaxSize().background(Color(0xFF0C1818)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { setImageResource(R.drawable.nota_loader) } },
            update = { iv -> (iv.drawable as? AnimationDrawable)?.let { if (!it.isRunning) it.start() } },
            modifier = Modifier.size(side),
        )
    }
}
