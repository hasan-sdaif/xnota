package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.model.Rgba
import com.xnota.settings.Preferences
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** How fast the reader's auto-scroll travels: a comfortable reading pace. */
private val AUTOSCROLL_DP_PER_S = 40.dp

/** The sepia the reader's tint leans its paper toward. */
private val SEPIA = Color(0xFFF1E3C4)

/** The reader's paper palette (the custom reading paper colours). */
private val READER_PAPERS = listOf(
    Rgba(255, 248, 240, 255), // warm white (the default)
    Rgba(255, 255, 255, 255), // plain white
    Rgba(244, 232, 210, 255), // cream
    Rgba(233, 226, 216, 255), // grey paper
    Rgba(34, 38, 42, 255), // night
    Rgba(28, 32, 28, 255), // green-black
)

/** The line heights the reader's step chips settle on. */
private val LINE_HEIGHTS = listOf(1.2, 1.45, 1.7, 2.0, 2.4)

/**
 * Reading mode (the fork): the note's text alone, read-only, on comfortable paper — every other
 * control hidden behind one thin exit bar. The A-/A+ chips and the line-height menu carry the
 * persisted reader prefs; sepia, the paper palette and auto-scroll round out the reading kit.
 * A tap anywhere stops auto-scroll.
 */
@Composable
fun ReadingPane(editor: Editor) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    editor.prefsVersion // prefs changes (A-/A+, paper) land live
    editor.contentVersion // the note itself changed under the reader
    val prefs = editor.preferences
    var autoscroll by remember { mutableStateOf(false) }
    var paperOpen by remember { mutableStateOf(false) }
    var lineOpen by remember { mutableStateOf(false) }
    var brightOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val autoscrollPx = with(density) { AUTOSCROLL_DP_PER_S.toPx() } / 60f

    // The reader's paper: the persisted reading colour, tinted sepia when that chip is on.
    val paper = if (prefs.readerSepia) {
        lerp(prefs.readerPaperColor.toComposeColor(), SEPIA, 0.8f)
    } else {
        prefs.readerPaperColor.toComposeColor()
    }
    // Ink the paper can actually show: dark on light paper, light on dark.
    val ink = if (paper.luminance() > 0.5f) Color(0xFF2E2A24) else Color(0xFFEDE7DC)

    // Auto-scroll: one frame's worth of travel at a time, until the end or a tap.
    LaunchedEffect(autoscroll) {
        if (!autoscroll) return@LaunchedEffect
        while (isActive && autoscroll) {
            delay(1000 / 60)
            val next = scrollState.value + autoscrollPx
            scrollState.scrollTo(next.roundToInt())
            if (!scrollState.canScrollForward) {
                autoscroll = false
                break
            }
        }
    }

    Column(Modifier.fillMaxSize().background(paper).statusBarsPadding().imePadding()) {
        // The thin exit bar: everything reading mode still shows.
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tooltip(stringResource(R.string.exit_reading)) {
                Icon(
                    NotaIcons.close, stringResource(R.string.exit_reading),
                    tint = ink, modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.small)
                        .clickable { editor.exitReadingMode() }.padding(5.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                editor.state.document.displayName ?: stringResource(R.string.reading_mode),
                color = ink.copy(alpha = 0.75f),
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { editor.setReaderPref { it.copy(readerFontScale = (it.readerFontScale - 0.1).coerceAtLeast(0.8)) } }) {
                Text("A-", color = ink, fontSize = 13.sp)
            }
            TextButton(onClick = { editor.setReaderPref { it.copy(readerFontScale = (it.readerFontScale + 0.1).coerceAtMost(2.0)) } }) {
                Text("A+", color = ink, fontSize = 13.sp)
            }
            Box {
                TextButton(onClick = { lineOpen = true }) {
                    Text("×${LINE_HEIGHTS.minBy { kotlin.math.abs(it - prefs.readerLineHeight) }}", color = ink, fontSize = 13.sp)
                }
                DropdownMenu(expanded = lineOpen, onDismissRequest = { lineOpen = false }) {
                    for (h in LINE_HEIGHTS) {
                        DropdownMenuItem(
                            text = { Text("×$h" + if (h == prefs.readerLineHeight) " ✓" else "") },
                            onClick = { lineOpen = false; editor.setReaderPref { it.copy(readerLineHeight = h) } },
                        )
                    }
                }
            }
            Tooltip(stringResource(R.string.reader_brightness)) {
                TextButton(onClick = { brightOpen = !brightOpen }) {
                    Text(
                        "%" + if (prefs.readerBrightness < 1.0) "${(prefs.readerBrightness * 100).roundToInt()}" else "",
                        color = if (brightOpen || prefs.readerBrightness < 1.0) ink else ink.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                    )
                }
            }
            Tooltip(stringResource(R.string.reader_sepia)) {
                Icon(
                    NotaIcons.palette, stringResource(R.string.reader_sepia),
                    tint = if (prefs.readerSepia) ink else ink.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.small)
                        .clickable { editor.setReaderPref { it.copy(readerSepia = !it.readerSepia) } }.padding(5.dp),
                )
            }
            Box {
                Tooltip(stringResource(R.string.reader_paper)) {
                    Icon(
                        NotaIcons.palette, stringResource(R.string.reader_paper),
                        tint = ink, modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.small)
                            .clickable { paperOpen = true }.padding(5.dp),
                    )
                }
                DropdownMenu(expanded = paperOpen, onDismissRequest = { paperOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (c in READER_PAPERS) {
                                    Box(
                                        Modifier
                                            .size(if (c == prefs.readerPaperColor) 26.dp else 20.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(c.toComposeColor())
                                            .clickable {
                                                paperOpen = false
                                                editor.setReaderPref { it.copy(readerPaperColor = c) }
                                            },
                                    )
                                }
                            }
                        },
                        onClick = {},
                    )
                }
            }
            Tooltip(stringResource(R.string.reader_autoscroll)) {
                Icon(
                    NotaIcons.arrowDown, stringResource(R.string.reader_autoscroll),
                    tint = if (autoscroll) palette.accent.toComposeColor() else ink.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.small)
                        .clickable { autoscroll = !autoscroll }.padding(5.dp),
                )
            }
        }
        if (brightOpen) {
            // The brightness row: the slider the % chip reveals, settled straight into the pref.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reader_brightness),
                    color = ink.copy(alpha = 0.75f), fontSize = 12.sp,
                )
                Slider(
                    value = prefs.readerBrightness.toFloat(),
                    onValueChange = {
                        editor.setReaderPref { p -> p.copy(readerBrightness = it.toDouble()) }
                    },
                    valueRange = Preferences.READER_BRIGHTNESS_MIN.toFloat()..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(
                    "${(prefs.readerBrightness * 100).roundToInt()}%",
                    color = ink, fontSize = 12.sp,
                )
            }
        }
        if (autoscroll) {
            Text(
                stringResource(R.string.reader_autoscroll_hint),
                color = ink.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            )
        }
        // The text: comfortable typography on the chosen paper, a measure a long line enjoys.
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { if (autoscroll) autoscroll = false } }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fontSize = (17.0 * prefs.readerFontScale).sp
                Text(
                    remember(editor.contentVersion) { editor.readingText() },
                    style = TextStyle(
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * prefs.readerLineHeight).sp,
                        color = ink,
                    ),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
            // The dim: brightness under 100% lays a black veil of alpha (1 − b) over the reading
            // surface alone — the exit bar keeps its full contrast, and taps/scroll pass through.
            val dim = 1.0 - prefs.readerBrightness
            if (dim > 0.001) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim.toFloat())))
            }
        }
    }
}
