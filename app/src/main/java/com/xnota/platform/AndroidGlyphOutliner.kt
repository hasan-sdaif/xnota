package com.xnota.platform

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.xnota.core.vector.GlyphOutliner
import com.xnota.core.vector.GlyphRun
import com.xnota.core.vector.GlyphStyle

/**
 * Glyph outlines through `Paint.getTextPath`, so a placed SVG's text is geometry rather than
 * pixels and stays sharp at every zoom. [GlyphPaths] reads the platform path back.
 *
 * Font matching is the platform's, which is the same matching the raster path already had. A file
 * naming a family the device lacks sets in a substitute either way.
 */
class AndroidGlyphOutliner : GlyphOutliner {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    @Synchronized
    override fun outline(text: String, style: GlyphStyle): GlyphRun? {
        if (text.isEmpty() || style.size <= 0.0) return null
        apply(style)
        path.reset()
        runCatching { paint.getTextPath(text, 0, text.length, 0f, 0f, path) }.getOrNull() ?: return null
        val contours = GlyphPaths.contours(path, style.size)
        return GlyphRun(contours, paint.measureText(text).toDouble())
    }

    @Synchronized
    override fun measure(text: String, style: GlyphStyle): Double {
        if (text.isEmpty() || style.size <= 0.0) return 0.0
        apply(style)
        return paint.measureText(text).toDouble()
    }

    private fun apply(style: GlyphStyle) {
        val face = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(style.family ?: "sans-serif", face)
        paint.textSize = style.size.toFloat()
        // Paint states tracking in ems; SVG states it in the same units as the font size.
        paint.letterSpacing = (style.letterSpacing / style.size).toFloat()
    }
}
