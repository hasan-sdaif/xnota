package com.xnota.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.util.IncomingNote
import com.xnota.core.util.evernoteNotesOf
import com.xnota.core.util.joplinNotesOf
import com.xnota.core.util.keepNotesOf
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor

/** Which app a note migration was asked to come from. */
enum class ImportKind { EVERNOTE, KEEP, JOPLIN }

/**
 * The import wizard's chooser (the fork): three big options — Evernote, Google Keep,
 * Joplin — each opening the SAF picker that the app's export asks for. Lives over the explorer
 * so the pick lands in the folder on screen once it is committed.
 */
@Composable
internal fun ImportWizardDialog(
    onImport: (ImportKind) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_wizard_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // An option closes the chooser before its picker opens, so nothing is left
                // overlaying the folder the import lands in.
                fun pick(kind: ImportKind) = { onDismiss(); onImport(kind) }
                ImportOption(NotaIcons.note, R.string.import_from_evernote, R.string.import_evernote_desc, pick(ImportKind.EVERNOTE))
                ImportOption(NotaIcons.checkboxItem, R.string.import_from_keep, R.string.import_keep_desc, pick(ImportKind.KEEP))
                ImportOption(NotaIcons.textBox, R.string.import_from_joplin, R.string.import_joplin_desc, pick(ImportKind.JOPLIN))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One of the wizard's options: icon, label and the one-line description of what is picked. */
@Composable
private fun ImportOption(icon: ImageVector, label: Int, description: Int, onPick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.accent.toComposeColor(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.text.toComposeColor())
            Text(stringResource(description), fontSize = 12.sp, color = palette.textDim.toComposeColor())
        }
    }
}

// --- the SAF picks as migrating notes (the reading half runs on IO; the parsing is pure) ---

/** A picked file's whole text, or null when it could not be read. */
private fun readTextOf(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

/** The storage provider's file name of [uri], if it has one. */
private fun displayNameOf(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0) c.getString(i) else null
        } else null
    }
}.getOrNull()

/**
 * The picked `.enex` as migrating notes, or null when the file itself was unreadable (an
 * unparseable export comes back empty, not null — the caller's wording tells them apart).
 */
internal fun readEvernoteImport(resolver: ContentResolver, uri: Uri): List<IncomingNote>? =
    readTextOf(resolver, uri)?.let(::evernoteNotesOf)

/** Every picked Keep file's notes; unreadable files and files that are neither shape fall away. */
internal fun readKeepImport(resolver: ContentResolver, uris: List<Uri>): List<IncomingNote> =
    uris.flatMap { uri -> readTextOf(resolver, uri)?.let(::keepNotesOf) ?: emptyList() }

/** Every picked Joplin `.md` as a migrating note, named from its front matter or its file. */
internal fun readJoplinImport(resolver: ContentResolver, uris: List<Uri>): List<IncomingNote> =
    joplinNotesOf(uris.mapNotNull { uri ->
        readTextOf(resolver, uri)?.let { text -> (displayNameOf(resolver, uri) ?: "Note.md") to text }
    })
