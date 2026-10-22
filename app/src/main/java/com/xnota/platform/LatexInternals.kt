// The LaTeX library keeps its layout and drawing Kotlin-internal; this file alone reaches past that.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.xnota.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import com.hrm.latex.parser.IncrementalLatexParser
import com.hrm.latex.parser.model.LatexNode
import com.hrm.latex.renderer.font.MathFontProvider
import com.hrm.latex.renderer.font.MathFontProviderFactory
import com.hrm.latex.renderer.layout.LatexRenderResult
import com.hrm.latex.renderer.layout.LatexRenderer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexFontFamilies
import com.hrm.latex.renderer.model.defaultLatexFontFamilies
import com.hrm.latex.renderer.model.toContext

/**
 * The few calls that set a formula once and then both measure and draw it from that one layout,
 * rather than through the library's public measurer and exporter, which each lay it out again. The
 * library marks them internal, so they are kept to this one file, where a library update that moves
 * them fails the build here rather than somewhere far away.
 */
object LatexInternals {

    /** The library's default fonts, read through the composition exactly as its own composables do. */
    @Composable
    fun defaultFonts(): LatexFontFamilies = defaultLatexFontFamilies()

    /** [latex] parsed; empty when nothing in it sets. */
    fun parse(latex: String): List<LatexNode> =
        IncrementalLatexParser().apply { append(latex) }.getCurrentDocument().children

    /** The font metrics every [layout] shares; the library builds a fresh one per formula otherwise. */
    internal fun metrics(fonts: LatexFontFamilies): Any = MathFontProviderFactory.create(fonts)

    /** [nodes] laid out, as a handle for the calls below; null when they set nothing. */
    internal fun layout(
        nodes: List<LatexNode>,
        config: LatexConfig,
        fonts: LatexFontFamilies,
        metrics: Any?,
        text: TextMeasurer,
        density: Density,
    ): Any? {
        if (nodes.isEmpty()) return null
        val r = LatexRenderer.measure(nodes, config.toContext(false, fonts, metrics as MathFontProvider?), text, density)
        return r.takeIf { it.layout.width > 0f && it.layout.height > 0f }
    }

    internal fun width(layout: Any): Float = (layout as LatexRenderResult).canvasWidth

    internal fun height(layout: Any): Float = (layout as LatexRenderResult).canvasHeight

    /** How far below the top of the box [width] and [height] describe the formula's baseline sits. */
    internal fun baseline(layout: Any): Float = (layout as LatexRenderResult).let { it.layout.baseline + it.verticalPadding }

    /** Draw [layout] into [scope] as the library's renderer does over a transparent background. */
    internal fun draw(scope: DrawScope, layout: Any) {
        val r = layout as LatexRenderResult
        r.layout.draw(scope, r.horizontalPadding, r.verticalPadding)
    }

    /**
     * Whether [nodes] paint in colours of their own rather than only the text colour: `\color` and
     * its kin, links, and commands the library does not know, which it marks in red.
     */
    fun ownColors(nodes: List<LatexNode>): Boolean =
        nodes.any { root -> root.fold(false) { found, n -> found || n.ownsColor() } }

    private fun LatexNode.ownsColor(): Boolean = when (this) {
        is LatexNode.Color, is LatexNode.ColorBox, is LatexNode.Hyperlink, is LatexNode.Command -> true
        is LatexNode.Enclose -> "mathcolor" in attributes || "mathbackground" in attributes
        else -> false
    }
}
