package com.xnota.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/**
 * The reusable identity pieces shared by every branded surface — the native brand mark (the X and
 * fountain-pen nib cropped from the app's own adaptive launcher artwork), the "Nota" wordmark that
 * mark completes, and the low-noise divider. The mark's tile is the one place literal brand
 * gradient values are allowed (it is artwork, like the launcher itself); everything interactive
 * reads the live palette so the accent stays the user's own single choice of colour.
 */
object XNotaBrand {
    /** The mark tile's gradient — artwork constants, not themeable chrome. */
    val Forest = Color(0xFF0B2119)
    val Ink = Color(0xFF07110F)
}

/** The brand mark: the launcher's X + nib on a forest→ink gradient tile with an accent ring. */
@Composable
fun XNotaBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String = stringResource(R.string.app_name),
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(if (size >= 60.dp) 21.dp else 13.dp)
    Box(
        modifier
            .size(size)
            .shadow(if (size >= 60.dp) 10.dp else 4.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(XNotaBrand.Forest, XNotaBrand.Ink)))
            .border(1.dp, palette.accent.toComposeColor().copy(alpha = 0.40f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.xnota_brand_mark),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(size * 0.105f),
        )
    }
}

/** X mark + "Nota" wordmark for navigation, About, and compact app surfaces; the mark supplies the X. */
@Composable
fun XNotaBrandLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    showTagline: Boolean = false,
    centered: Boolean = false,
) {
    val palette = LocalPalette.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (markSize >= 60.dp) 14.dp else 10.dp),
    ) {
        XNotaBrandMark(size = markSize)
        Column(
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(if (showTagline) 2.dp else 0.dp),
        ) {
            Text(
                text = "Nota",
                color = palette.text.toComposeColor(),
                fontSize = if (markSize >= 60.dp) 34.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.65).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showTagline) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    color = palette.textDim.toComposeColor(),
                    fontSize = if (markSize >= 60.dp) 13.sp else 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A low-noise horizontal accent separating branded navigation surfaces. */
@Composable
fun XNotaBrandDivider(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    palette.accent.toComposeColor().copy(alpha = 0.28f),
                    palette.border.toComposeColor().copy(alpha = 0.6f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

/** The slim accent marker a selected navigation row carries on its trailing edge. */
@Composable
fun XNotaBrandEdgeMarker(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier.width(3.dp).height(18.dp).clip(CircleShape)
            .background(palette.accent.toComposeColor()),
    )
}
