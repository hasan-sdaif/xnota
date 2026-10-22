package com.xnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/**
 * The flow text's find & replace bar (the fork): a compact panel docked above the keyboard with
 * the needle, the replacement, case/whole-word/diacritic toggles, next/prev stepping through the
 * matches (selecting each on the page), and replace-all as one undo step. The word counter lives
 * on its trailing edge so the bar doubles as the text's statistics readout.
 */
@Composable
fun FlowFindBar(editor: Editor, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var needle by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var ignoreDiacritics by remember { mutableStateOf(true) }
    var matchCount by remember { mutableStateOf(-1) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun refreshCount() {
        matchCount = if (needle.isEmpty()) -1 else editor.flowText.findAll(needle, caseSensitive, wholeWord, ignoreDiacritics).size
    }

    fun step(forward: Boolean) {
        if (needle.isEmpty()) return
        val caret = editor.flowCaretGlobal()
        val hit = if (forward) {
            editor.flowText.findNext(needle, caret + 1, caseSensitive, wholeWord, ignoreDiacritics)
        } else {
            editor.flowText.findPrev(needle, caret, caseSensitive, wholeWord, ignoreDiacritics)
        }
        if (hit != null) {
            editor.flowText.selectGlobal(hit)
            refreshCount()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.panel.toComposeColor())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FindField(needle, { needle = it; refreshCount() }, stringResource(R.string.find_replace), focus, Modifier.weight(1f))
            FindField(replacement, { replacement = it }, stringResource(R.string.replace), null, Modifier.weight(1f))
            TextButton(onClick = { step(true) }) { Text(stringResource(R.string.find_next)) }
            TextButton(onClick = { step(false) }) { Text(stringResource(R.string.find_prev)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_pane)) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Toggle(stringResource(R.string.match_case), caseSensitive) { caseSensitive = it; refreshCount() }
            Toggle(stringResource(R.string.whole_word), wholeWord) { wholeWord = it; refreshCount() }
            Toggle(stringResource(R.string.ignore_diacritics), ignoreDiacritics) { ignoreDiacritics = it; refreshCount() }
            if (matchCount >= 0) {
                Text(
                    pluralStringResource(R.plurals.matches_n, matchCount, matchCount),
                    color = palette.textDim.toComposeColor(),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text("", Modifier.weight(1f))
            }
            TextButton(
                onClick = {
                    if (needle.isEmpty()) return@TextButton
                    matchCount = editor.flowText.replaceAllMatches(needle, replacement, caseSensitive, wholeWord, ignoreDiacritics)
                },
            ) { Text(stringResource(R.string.replace_all)) }
        }
    }
}

@Composable
private fun FindField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    focus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    TextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        modifier = modifier.height(52.dp).let { m -> focus?.let { m.focusRequester(it) } ?: m },
        placeholder = { Text(label, color = palette.textDim.toComposeColor(), fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = palette.text.toComposeColor()),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(start = 0.dp))
    }
}
