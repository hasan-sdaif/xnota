package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.util.FocusTimer
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The focus timer's HUD (the fork): a small chip pinned over the editor that counts the work
 * phase down, rings a soft tone at each transition, and counts the sessions finished. The
 * pure [FocusTimer] state machine does the arithmetic; this chip owns the one-second clock and
 * the start/pause/reset menu. Its lengths follow the focus prefs, applied to a fresh timer.
 */
@Composable
fun FocusHud(editor: Editor) {
    val palette = LocalPalette.current
    val prefs = editor.preferences
    val timer = remember(prefs.focusMinutes, prefs.focusBreak) {
        FocusTimer(prefs.focusMinutes * 60, prefs.focusBreak * 60)
    }
    // The timer is a plain state machine: one bump a second recomposes the chip while it runs.
    var beat by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(timer.running) {
        if (!timer.running) return@LaunchedEffect
        while (isActive) {
            delay(1000)
            val ended = timer.tick(1)
            if (ended != null) editor.softTone() // a phase finished: work or break begins
            beat++
        }
    }

    val minutes = timer.secondsRemaining / 60
    val seconds = timer.secondsRemaining % 60
    val phase = stringResource(if (timer.phase == FocusTimer.Phase.WORK) R.string.focus_work else R.string.focus_break)
    val accent = palette.accent.toComposeColor()
    val text = palette.text.toComposeColor()

    Box {
        Row(
            Modifier
                .height(32.dp)
                .clip(MaterialTheme.shapes.large)
                .background((if (timer.running) palette.selectionBackground else palette.panel).toComposeColor())
                .clickable { menuOpen = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "%d:%02d".format(minutes, seconds),
                color = if (timer.running) accent else text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(phase, color = text.copy(alpha = 0.8f), fontSize = 12.sp)
            if (timer.completedWork > 0) {
                Text(
                    pluralStringResource(R.plurals.focus_sessions_n, timer.completedWork, timer.completedWork),
                    color = palette.textDim.toComposeColor(), fontSize = 11.sp, maxLines = 1,
                )
            }
            if (timer.running) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(accent),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when {
                                timer.running -> R.string.focus_pause
                                timer.secondsRemaining == timer.phaseLength -> R.string.focus_start
                                else -> R.string.focus_resume
                            },
                        ),
                    )
                },
                onClick = {
                    menuOpen = false
                    if (timer.running) timer.pause() else timer.start()
                    beat++
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.focus_reset)) },
                onClick = { menuOpen = false; timer.reset(); beat++ },
            )
        }
    }
}
