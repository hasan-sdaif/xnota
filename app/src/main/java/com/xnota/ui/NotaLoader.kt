package com.xnota.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen launch splash, carrying the app's one true mark: the brand mark artwork itself
 * ([R.drawable.xnota_brand_mark] — the same PNG the navigation, the About pane and the launcher
 * crop wear), not a redrawn stand-in. The mark settles in first, the "Nota" wordmark it
 * completes follows, and the tagline fades up last — one unhurried entrance that holds its
 * final state while the session restores, then the whole stage fades out (see MainActivity).
 * The stage is the fixed brand dark #0C1818, matching [com.xnota.R.color.loader_bg] exactly,
 * so the system splash hands off with no seam. All colours here are artwork constants, like
 * the mark's own tile — the launch is brand, not themeable chrome.
 */
@Composable
fun NotaLoader(modifier: Modifier = Modifier) {
    val cfg = LocalConfiguration.current
    // Scale with the short edge but cap it, so phones render big without tablets ballooning.
    val shortEdge = minOf(cfg.screenWidthDp, cfg.screenHeightDp)
    val markSide = (shortEdge * 0.30f).coerceAtMost(150f).dp

    // The entrance, as three one-shot curves: the mark settles (fade + lift + a breath of
    // scale), the wordmark follows shortly after, the tagline last. Each holds its end state,
    // so however long the restore takes the splash never loops or blinks.
    val mark = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { mark.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing)) }
        delay(180)
        launch { word.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing)) }
        delay(300)
        tag.animateTo(1f, tween(durationMillis = 400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier.fillMaxSize().background(Color(0xFF0C1818)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.xnota_brand_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(markSide)
                    .graphicsLayer {
                        alpha = mark.value
                        val s = 0.94f + 0.06f * mark.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - mark.value) * 14.dp.toPx()
                    },
            )
            Spacer(Modifier.height(22.dp))
            // The mark supplies the X; the wordmark beneath it completes the lockup.
            Text(
                text = "Nota",
                color = Color(0xFFF0F6F3),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.65).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 8.dp.toPx()
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                color = Color(0xFF8FA8A0),
                fontSize = 13.sp,
                modifier = Modifier.graphicsLayer { alpha = tag.value },
            )
        }
    }
}
