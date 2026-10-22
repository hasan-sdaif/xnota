package com.xnota.platform

import android.graphics.Path
import com.xnota.core.geometry.Rect
import com.xnota.core.pal.FontSpec
import com.xnota.core.pal.LineMetrics
import com.xnota.core.pal.TextFlags
import com.xnota.core.pal.TextMeasurer
import com.xnota.core.vector.VectorContour

/** Measures text with the same layout pipeline the renderer draws with. */
class AndroidTextMeasurer : TextMeasurer {

    override fun measure(text: String, font: FontSpec, wrapWidth: Double, flags: TextFlags): Rect {
        val probe = text.ifEmpty { " " }
        val paint = AndroidText.textPaint(font)
        val layout = AndroidText.layout(probe, wrapWidth.toInt(), paint, flags)
        // Width is fixed to the wrap width; height follows the text.
        return Rect(0.0, 0.0, wrapWidth, layout.height.toDouble())
    }

    override fun lineHeight(font: FontSpec): Double = AndroidText.lineHeight(font)

    override fun metrics(font: FontSpec): LineMetrics {
        val fm = AndroidText.textPaint(font).fontMetrics
        // Paint ascent is negative (above baseline); expose both as positive distances.
        return LineMetrics(ascent = -fm.ascent.toDouble(), descent = fm.descent.toDouble())
    }

    override fun outline(text: String, font: FontSpec, wrapWidth: Double, flags: TextFlags): List<VectorContour> {
        if (text.isBlank()) return emptyList()
        val paint = AndroidText.textPaint(font)
        val layout = AndroidText.layout(text, wrapWidth.toInt(), paint, flags)
        val out = ArrayList<VectorContour>()
        val path = Path()
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            var end = layout.getLineEnd(line)
            while (end > start && text[end - 1].isWhitespace()) end--
            if (end <= start) continue
            path.reset()
            // Where the layout itself draws the line, so the outline lands on the screen's glyphs.
            paint.getTextPath(text, start, end, layout.getLineLeft(line), layout.getLineBaseline(line).toFloat(), path)
            out += GlyphPaths.contours(path, paint.textSize.toDouble())
        }
        return out
    }

    override fun advances(text: String, font: FontSpec): DoubleArray {
        if (text.isEmpty()) return DoubleArray(0)
        val widths = FloatArray(text.length)
        AndroidText.textPaint(font).getTextWidths(text, widths)
        return DoubleArray(text.length) { widths[it].toDouble() }
    }
}
