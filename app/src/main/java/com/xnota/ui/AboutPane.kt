package com.xnota.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.xnota.R
import com.xnota.platform.FontCatalog
import com.xnota.ui.icons.NotaIcons
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/hasan-sdaif/xnota"
private const val ISSUES_URL = "https://github.com/hasan-sdaif/xnota/issues/new"
private const val LICENSE_URL = "https://github.com/hasan-sdaif/xnota/blob/main/LICENSE"
private const val MIN_FILL_MS = 120L

/**
 * The About backstage pane: app identity + the one place that points users back at the
 * project. Since the app ships with no accounts or telemetry, these links are the only
 * feedback channel. The bug/feature links open GitHub's new-issue form with the body
 * pre-filled (app version + device + OS for bug reports). Back from here returns to Home.
 */
@Composable
fun AboutPane() {
    val palette = LocalPalette.current
    val ctx = LocalContext.current

    val appIcon = remember {
        runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(144, 144).asImageBitmap() }.getOrNull()
    }
    val version = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        }.getOrDefault("")
    }

    fun open(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (appIcon != null) {
                Image(appIcon, "XNota", modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
            }
            Text("XNota", color = palette.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.app_tagline),
                color = palette.textDim.toComposeColor(), fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            if (version.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Tap to copy, so a version string is easy to paste into a report.
                Tooltip(stringResource(R.string.copy_version)) {
                    Row(
                        Modifier.clickable { copyVersion(ctx, version) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.version_n, version), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(NotaIcons.copy, stringResource(R.string.copy_version), tint = palette.textDim.toComposeColor(), modifier = Modifier.size(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.help_make_better), color = palette.text.toComposeColor(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))

            // Three buttons, side by side; each fills with the selection container while pressed.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AboutButton(NotaIcons.bug, stringResource(R.string.report_bug)) { open(bugReportUrl(version)) }
                AboutButton(NotaIcons.idea, stringResource(R.string.request_feature)) { open(featureRequestUrl()) }
                AboutButton(NotaIcons.heart, stringResource(R.string.star_on_github)) { open(REPO_URL) }
            }

            Spacer(Modifier.height(26.dp))
            // Quiet, single-line growth nudge (a link, never a popup).
            Row(
                Modifier.clickable { open(REPO_URL) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.enjoying_xnotes), color = palette.textDim.toComposeColor(), fontSize = 12.sp)
                Text(stringResource(R.string.star_on_github), color = palette.accent.toComposeColor(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mit_license), color = palette.textDim.toComposeColor(), fontSize = 11.sp, modifier = Modifier.clickable { open(LICENSE_URL) })
            }

            // xnota: every bundled font ships a license notice in its asset folder
            // (OFL and the like); this is the in-app place the notices are readable from.
            Spacer(Modifier.height(18.dp))
            var openLicenses by remember { mutableStateOf(false) }
            var shownLicense by remember { mutableStateOf<Pair<String, String>?>(null) }
            Text(
                stringResource(R.string.font_licenses),
                color = palette.textDim.toComposeColor(), fontSize = 11.sp,
                modifier = Modifier.clickable { openLicenses = true },
            )
            if (openLicenses) {
                FontLicensesDialog(
                    shown = shownLicense,
                    onShow = { shownLicense = it },
                    onBack = { shownLicense = null },
                    onClose = { openLicenses = false; shownLicense = null },
                )
            }

            // The fork's own release ledger: what each wave brought, newest first.
            Spacer(Modifier.height(6.dp))
            var openChangelog by remember { mutableStateOf(false) }
            Text(
                stringResource(R.string.whats_new),
                color = palette.textDim.toComposeColor(), fontSize = 11.sp,
                modifier = Modifier.clickable { openChangelog = true },
            )
            if (openChangelog) {
                WhatsNewDialog(onClose = { openChangelog = false })
            }
        }
    }
}

/** The changelog entry ids the ledger holds, onto the string resources that word them. */
private val CHANGELOG_TEXT = mapOf(
    "rounded_image_corners" to R.string.changelog_rounded_image_corners,
    "prefs_search" to R.string.changelog_prefs_search,
    "reader_brightness" to R.string.changelog_reader_brightness,
    "page_number_stamps" to R.string.changelog_page_number_stamps,
    "sticky_preset" to R.string.changelog_sticky_preset,
    "search_history" to R.string.changelog_search_history,
    "pin_lock" to R.string.changelog_pin_lock,
    "encrypted_backups" to R.string.changelog_encrypted_backups,
    "app_imports" to R.string.changelog_app_imports,
    "text_exports" to R.string.changelog_text_exports,
    "version_history" to R.string.changelog_version_history,
    "duplicate_arrays" to R.string.changelog_duplicate_arrays,
    "measure_tool" to R.string.changelog_measure_tool,
    "ink_engine" to R.string.changelog_ink_engine,
    "brush_studio" to R.string.changelog_brush_studio,
    "layers_panel" to R.string.changelog_layers_panel,
    "align_mirror" to R.string.changelog_align_mirror,
    "arrowheads_patterns" to R.string.changelog_arrowheads_patterns,
    "reading_mode" to R.string.changelog_reading_mode,
    "focus_timer" to R.string.changelog_focus_timer,
    "find_replace" to R.string.changelog_find_replace,
    "organization" to R.string.changelog_organization,
    "smart_folders" to R.string.changelog_smart_folders,
    "calligraphy_rulings" to R.string.changelog_calligraphy_rulings,
    "hijri_dates" to R.string.changelog_hijri_dates,
    "arabic_digits" to R.string.changelog_arabic_digits,
    "loose_arabic_search" to R.string.changelog_loose_arabic_search,
    "arabic_first" to R.string.changelog_arabic_first,
    "any_folder_backups" to R.string.changelog_any_folder_backups,
    "no_accounts" to R.string.changelog_no_accounts,
)

/**
 * "What's new": the fork's releases, newest first, each under its version header with its headline
 * lines — the pure [com.xnota.core.util.ForkChangelog] ledger rendered straight through the string
 * table above, so a missing word is a compile error rather than a blank line.
 */
@Composable
private fun WhatsNewDialog(onClose: () -> Unit) {
    val palette = LocalPalette.current
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.whats_new)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                for (release in com.xnota.core.util.ForkChangelog.releases) {
                    Text(
                        stringResource(R.string.changelog_version, release.version),
                        color = palette.accent.toComposeColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    for (entry in release.entries) {
                        val res = CHANGELOG_TEXT[entry.id]
                        if (res != null) {
                            Text(
                                "•  " + ctx.getString(res),
                                color = palette.text.toComposeColor(), fontSize = 12.sp, lineHeight = 17.sp,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
    )
}

/** xnota: the bundled fonts' license notices, read straight from the assets. */
@Composable
private fun FontLicensesDialog(
    shown: Pair<String, String>?,
    onShow: (Pair<String, String>) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    val ctx = LocalContext.current
    var text by remember(shown) { mutableStateOf<String?>(null) }
    LaunchedEffect(shown) {
        text = shown?.let { (name, slug) ->
            runCatching { ctx.assets.open("fonts/$slug/LICENSE.txt").bufferedReader().readText() }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(shown?.first ?: stringResource(R.string.font_licenses)) },
        text = {
            if (shown == null) {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for ((name, slug) in FontCatalog.bundledLicenseSources()) {
                        Text(
                            name,
                            color = palette.accent.toComposeColor(), fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onShow(name to slug) }.padding(vertical = 7.dp),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text ?: stringResource(R.string.font_license_loading), fontSize = 11.sp, lineHeight = 15.sp, color = palette.text.toComposeColor())
                }
            }
        },
        confirmButton = {
            if (shown == null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
            } else {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_home)) }
            }
        },
    )
}

/** A link button: line glyph above a label. Fills with the selection container while pressed. */
@Composable
private fun RowScope.AboutButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    // Keep the pressed fill visible for a minimum time so even a millisecond tap registers.
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        val scope = this
        var pressedAt = 0L
        var clearJob: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    clearJob?.cancel()
                    pressedAt = System.currentTimeMillis()
                    pressed = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val remaining = MIN_FILL_MS - (System.currentTimeMillis() - pressedAt)
                    clearJob?.cancel()
                    clearJob = scope.launch {
                        if (remaining > 0) delay(remaining)
                        pressed = false
                    }
                }
            }
        }
    }
    val accent = palette.accent.toComposeColor()
    val onAccent = palette.selectionForeground.toComposeColor()
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.medium)
            .background(if (pressed) palette.selectionBackground.toComposeColor() else Color.Transparent)
            .border(1.dp, if (pressed) accent else palette.border.toComposeColor(), MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (pressed) onAccent else accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (pressed) onAccent else palette.text.toComposeColor(),
            fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
        )
    }
}

private fun copyVersion(ctx: Context, version: String) {
    runCatching {
        val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("XNota version", "XNota $version"))
        Toast.makeText(ctx, ctx.getString(R.string.version_copied), Toast.LENGTH_SHORT).show()
    }
}

/** GitHub new-issue link with a bug template and the reporter's version/device/OS pre-filled. */
private fun bugReportUrl(version: String): String {
    val body = """
        **What happened?**


        **Steps to reproduce**
        1.
        2.

        **What did you expect instead?**


        ---
        XNota ${version.ifEmpty { "(unknown)" }}
        ${Build.MANUFACTURER} ${Build.MODEL}
        Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
    """.trimIndent()
    return "$ISSUES_URL?title=${Uri.encode("[Bug] ")}&body=${Uri.encode(body)}"
}

/** GitHub new-issue link with a lightweight feature template. */
private fun featureRequestUrl(): String {
    val body = """
        **What would you like XNota to do?**

    """.trimIndent()
    return "$ISSUES_URL?title=${Uri.encode("[Feature] ")}&body=${Uri.encode(body)}"
}
