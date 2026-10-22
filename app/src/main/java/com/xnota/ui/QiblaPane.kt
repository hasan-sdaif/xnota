package com.xnota.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnota.R
import com.xnota.core.util.Qibla
import com.xnota.ui.theme.LocalPalette
import com.xnota.ui.theme.toComposeColor
import java.util.Locale

/** Where the Qibla pane's coordinates start: Manama, Bahrain (the fork's home port). */
private const val DEFAULT_LAT = 26.0667
private const val DEFAULT_LON = 50.5577

/**
 * The Qibla pane (the fork): the bearing and distance to the Kaaba from coordinates typed by
 * hand — no location permission, no device location, ever. A simple compass rose turns its
 * needle to the bearing; the coordinates persist with the rest of the preferences.
 */
@Composable
fun QiblaPane(lat: Double, lon: Double, onMove: (Double, Double) -> Unit) {
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val accent = palette.accent.toComposeColor()
    val border = palette.border.toComposeColor()

    var latText by remember(lat) { mutableStateOf(fmt(lat)) }
    var lonText by remember(lon) { mutableStateOf(fmt(lon)) }
    val bearing = Qibla.bearingDeg(lat, lon)
    val distance = Qibla.distanceKm(lat, lon)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The compass rose: a circle, the cardinal letters, and the needle at the bearing.
        Canvas(Modifier.size(128.dp)) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            drawCircle(border, style = Stroke(width = 2f), radius = r - 2f)
            drawCircle(border.copy(alpha = 0.4f), style = Stroke(width = 1f), radius = r * 0.62f)
            // The cardinal ticks.
            for (a in 0 until 360 step 30) {
                rotate(a.toFloat(), pivot = c) {
                    drawLine(
                        border,
                        Offset(r, 4f),
                        Offset(r, if (a % 90 == 0) 14f else 10f),
                        strokeWidth = if (a % 90 == 0) 3f else 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // The needle: the qibla bearing, clockwise from true north.
            rotate(bearing.toFloat(), pivot = c) {
                drawLine(accent, Offset(r, r * 0.72f), Offset(r, r * 0.2f), strokeWidth = 7f, cap = StrokeCap.Round)
                drawLine(text, Offset(r, r * 0.72f), Offset(r, r + r * 0.12f), strokeWidth = 5f, cap = StrokeCap.Round)
            }
            drawCircle(accent, radius = 5f, center = c)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QiblaField(stringResource(R.string.qibla_lat), latText, Modifier.weight(1f)) {
                    latText = it
                    it.toDoubleOrNull()?.let { v -> onMove(v.coerceIn(-90.0, 90.0), lon) }
                }
                QiblaField(stringResource(R.string.qibla_lon), lonText, Modifier.weight(1f)) {
                    lonText = it
                    it.toDoubleOrNull()?.let { v -> onMove(lat, v.coerceIn(-180.0, 180.0)) }
                }
            }
            Text(
                stringResource(R.string.qibla_bearing, Qibla.bearingLabel(lat, lon)),
                color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(
                    R.string.qibla_distance,
                    "%.0f".format(Locale.ROOT, distance),
                ),
                color = dim, fontSize = 13.sp,
            )
            Text(stringResource(R.string.qibla_help), color = dim, fontSize = 12.sp)
            Text(
                stringResource(R.string.qibla_reset),
                color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onMove(DEFAULT_LAT, DEFAULT_LON) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** A decimal coordinate field; anything it does not parse simply does not move the pin. */
@Composable
private fun QiblaField(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    val palette = LocalPalette.current
    TextField(
        value = value,
        onValueChange = { s -> onValue(s.filter { it.isDigit() || it == '.' || it == '-' }.take(12)) },
        singleLine = true,
        modifier = modifier.height(56.dp),
        placeholder = { Text(label, color = palette.textDim.toComposeColor(), fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = palette.text.toComposeColor()),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun fmt(v: Double): String =
    if (v == Math.floor(v)) v.toInt().toString() else String.format(Locale.ROOT, "%.4f", v)
