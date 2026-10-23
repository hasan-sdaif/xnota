package com.xnota.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.util.PinHash
import com.xnota.ui.brand.XNotaBrandLockup
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/** The gate's PIN length bounds: it accepts 4–6 digits and auto-verifies once six are in. */
private const val PIN_MIN = 4
private const val PIN_MAX = 6

/** How long a wrong PIN's clear-and-shake runs. */
private const val PIN_SHAKE_MS = 360

/**
 * The app lock's gate (the fork): shown in place of the whole app UI while the lock is armed.
 * A 3×4 keypad collects a 4–6 digit PIN against [stored] (never the PIN itself — see
 * [PinHash]); a wrong entry clears, shakes and says so, and the sixth digit verifies by
 * itself so long PINs need no extra tap.
 *
 * [digits] shapes the keypad's digit glyphs for display (the fork's Arabic-Indic digit
 * system); identity when Latin digits are in force.
 */
@Composable
fun PinGate(
    stored: String,
    digits: (String) -> String,
    onUnlocked: () -> Unit,
) {
    val palette = LocalPalette.current
    var pin by remember { mutableStateOf("") }
    var wrongCount by remember { mutableStateOf(0) }
    val shake = remember { Animatable(0f) }

    // A wrong attempt: clear the entry, shake the mask, and show the complaint until the
    // next digit lands (pin goes empty then fills again).
    LaunchedEffect(wrongCount) {
        if (wrongCount == 0) return@LaunchedEffect
        pin = ""
        shake.snapTo(0f)
        shake.animateTo(
            0f,
            keyframes {
                durationMillis = PIN_SHAKE_MS
                0f at 0
                -16f at 40
                16f at 90
                -12f at 140
                12f at 190
                -6f at 240
                0f at 300
            },
        )
    }

    fun attempt(candidate: String) {
        if (PinHash.verify(candidate, stored)) onUnlocked()
        else wrongCount++
    }

    fun tap(d: String) {
        if (pin.length >= PIN_MAX) return
        val next = pin + d
        pin = next
        if (next.length == PIN_MAX) attempt(next)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg.toComposeColor())
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The brand lockup: the same X-nib mark the sidebar and About wear.
        XNotaBrandLockup(markSize = 64.dp, showTagline = false)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pin_enter), color = palette.textDim.toComposeColor(), fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        // The masked entry: a dot per typed digit, dim sockets for the rest.
        Row(
            Modifier.graphicsLayer { translationX = shake.value },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (i in 0 until PIN_MAX) {
                val filled = i < pin.length
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) palette.accent.toComposeColor()
                            else palette.surface.toComposeColor(),
                        )
                        .border(
                            1.dp,
                            if (filled) palette.accent.toComposeColor() else palette.border.toComposeColor(),
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // The complaint holds its slot even while hidden, so the keypad never jumps when a
        // wrong attempt lands or the next digit clears it.
        if (wrongCount > 0 && pin.isEmpty()) {
            Text(stringResource(R.string.pin_wrong), color = palette.danger.toComposeColor(), fontSize = 13.sp)
        } else {
            Spacer(Modifier.height(19.dp))
        }
        Spacer(Modifier.height(20.dp))

        // The 3×4 keypad: 1–9, a blank corner, 0, and the erase key.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (col in 0 until 3) {
                        when (row * 3 + col) {
                            9 -> Spacer(Modifier.size(width = 74.dp, height = 58.dp)) // blank corner
                            11 -> KeyCell(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                                Icon(
                                    NotaIcons.backspace,
                                    stringResource(R.string.pin_backspace),
                                    tint = palette.textDim.toComposeColor(),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            else -> {
                                val d = if (row * 3 + col == 10) "0" else (row * 3 + col + 1).toString()
                                KeyCell(onClick = { tap(d) }) {
                                    Text(
                                        digits(d),
                                        color = palette.text.toComposeColor(),
                                        fontSize = 21.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Short PINs (4–5 digits) verify from the button; six verify themselves above.
        TextButton(onClick = { attempt(pin) }, enabled = pin.length >= PIN_MIN) {
            Text(stringResource(R.string.pin_unlock))
        }
    }
}

/** One keypad key: a ≥48dp touch target with the digit (or icon) centred in it. */
@Composable
private fun KeyCell(onClick: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(width = 74.dp, height = 58.dp)
            .clip(MaterialTheme.shapes.small)
            .background(palette.surface.toComposeColor())
            .border(1.dp, palette.border.toComposeColor(), MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
