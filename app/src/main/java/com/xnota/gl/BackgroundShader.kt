package com.xnota.gl

import android.opengl.GLES30
import com.xnota.core.infinite.BackgroundPattern
import com.xnota.core.infinite.CanvasBackground
import com.xnota.core.model.PagePattern
import com.xnota.core.model.Rgba

/**
 * The canvas ruling, drawn procedurally over the whole viewport in one fragment shader. Nothing
 * about it is cached or rasterized, so it is exactly as sharp at 64x as at 0.02x, and a pinch
 * changes only two uniforms.
 *
 * The shader never sees a content coordinate. [BackgroundPattern] resolves the ruling to a period
 * and a phase already in device pixels, both small numbers, which is what keeps the pattern exact
 * on a canvas panned a long way from the origin: float32 would have lost a world coordinate, but
 * `scroll mod period` survives it.
 *
 * Two levels draw each frame, the base one solid and the half-period one fading in as you zoom, so
 * the grid subdivides continuously instead of popping between scales.
 */
class BackgroundShader(contextGen: Int) {

    private val program = GlProgram.build(VERTEX_SRC, FRAGMENT_SRC, contextGen)
    val contextGen: Int get() = program.contextGen

    fun release() = program.release()

    /**
     * Draw the paper and its ruling over the full viewport. Assumes the framebuffer is already
     * bound and the viewport set; leaves blending enabled with the standard alpha function.
     */
    fun draw(
        background: CanvasBackground,
        resolved: BackgroundPattern.Resolved,
        paper: Rgba,
        viewportW: Int,
        viewportH: Int,
    ) {
        program.use()
        program.set("uViewport", viewportW.toFloat(), viewportH.toFloat())
        program.set("uPaper", paper.r / 255f, paper.g / 255f, paper.b / 255f, paper.a / 255f)
        val ink = background.patternColor
        program.set("uInk", ink.r / 255f, ink.g / 255f, ink.b / 255f, ink.a / 255f)
        program.set("uMode", modeOf(background.pattern))
        program.set("uPeriod", resolved.periodPx.toFloat())
        program.set("uPhase", resolved.phaseXPx.toFloat(), resolved.phaseYPx.toFloat())
        program.set("uSubPeriod", resolved.subPeriodPx.toFloat())
        program.set("uSubPhase", resolved.subPhaseXPx.toFloat(), resolved.subPhaseYPx.toFloat())
        program.set("uSubAlpha", resolved.subdivisionAlpha.toFloat())
        program.set("uLineWidth", BackgroundPattern.LINE_WIDTH_PX.toFloat())
        program.set("uDotRadius", BackgroundPattern.DOT_RADIUS_PX.toFloat())
        // The fork's Arabic rulings: the khatt every-fourth-line lattice needs the phase of the
        // 4 × period (the base phase loses the period count). The naskh alif line's offset is a
        // fraction of whatever period the shader is handed, so it scales with the subdivision too.
        program.set("uQuadPhaseY", resolved.quadPhaseYPx.toFloat())

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        // A single oversized triangle covers the viewport with no vertex buffer at all: the
        // positions come from gl_VertexID, so there is nothing to allocate or rebuild.
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun modeOf(pattern: PagePattern): Int = when (pattern) {
        PagePattern.NONE -> MODE_NONE
        PagePattern.LINES -> MODE_LINES
        PagePattern.DOTS -> MODE_DOTS
        PagePattern.GRID -> MODE_GRID
        PagePattern.KHATT -> MODE_KHATT
        // Ruqaa guides are baselines alone, so the lines shader is it exactly; the wide pitch
        // comes from the resolve (the spacing is a cell).
        PagePattern.RUQAA -> MODE_LINES
        PagePattern.NASKH -> MODE_NASKH
    }

    companion object {
        const val MODE_NONE = 0
        const val MODE_LINES = 1
        const val MODE_DOTS = 2
        const val MODE_GRID = 3
        const val MODE_KHATT = 4
        const val MODE_NASKH = 5

        private val VERTEX_SRC = """#version 300 es
            void main() {
                // Fullscreen triangle from the vertex index; covers clip space with no attributes.
                float x = float((gl_VertexID & 1) << 2) - 1.0;
                float y = float((gl_VertexID & 2) << 1) - 1.0;
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT_SRC = """#version 300 es
            precision highp float;

            uniform vec2 uViewport;
            uniform vec4 uPaper;
            uniform vec4 uInk;
            uniform int uMode;
            uniform float uPeriod;
            uniform vec2 uPhase;
            uniform float uSubPeriod;
            uniform vec2 uSubPhase;
            uniform float uSubAlpha;
            uniform float uLineWidth;
            uniform float uDotRadius;
            // The fork's Arabic rulings: see the matching setters in draw().
            uniform float uQuadPhaseY;

            out vec4 fragColor;

            // Device-pixel distance from p to the nearest lattice line of this period.
            float axisDistance(float p, float period, float phase) {
                if (period <= 0.0) return 1e9;
                float q = mod(p + phase, period);
                return min(q, period - q);
            }

            // Coverage of a mark whose edge sits at 'radius', antialiased over one pixel.
            float coverage(float distance, float radius) {
                return 1.0 - smoothstep(radius - 0.5, radius + 0.5, distance);
            }

            float patternAt(vec2 p, float period, vec2 phase, float heavyScale) {
                float halfW = uLineWidth * 0.5;
                if (uMode == 1) {
                    return coverage(axisDistance(p.y, period, phase.y), halfW);
                }
                if (uMode == 2) {
                    vec2 d = vec2(axisDistance(p.x, period, phase.x),
                                  axisDistance(p.y, period, phase.y));
                    return coverage(length(d), uDotRadius);
                }
                if (uMode == 3) {
                    float cx = coverage(axisDistance(p.x, period, phase.x), halfW);
                    float cy = coverage(axisDistance(p.y, period, phase.y), halfW);
                    return max(cx, cy);
                }
                if (uMode == 4) {
                    // Khatt: the square grid, with the baseline every fourth line double width.
                    // The heavy line rides the uPeriod lattice only (heavyScale is 0 on the
                    // subdivided pass, whose 4 × period is a different lattice), so it stays
                    // put however far the grid subdivides.
                    float cx = coverage(axisDistance(p.x, period, phase.x), halfW);
                    float cy = coverage(axisDistance(p.y, period, phase.y), halfW);
                    float heavy = coverage(axisDistance(p.y, period * 4.0, uQuadPhaseY), uLineWidth) * heavyScale;
                    return max(max(cx, cy), heavy);
                }
                if (uMode == 5) {
                    // Naskh: a baseline with a lighter alif-height line above it, the two-line
                    // measure. The alif sits 0.35 of the period up, scaling with the subdivision.
                    float base = coverage(axisDistance(p.y, period, phase.y), halfW);
                    float alif = coverage(axisDistance(p.y + period * 0.35, period, phase.y), halfW) * 0.55;
                    return max(base, alif);
                }
                return 0.0;
            }

            void main() {
                fragColor = uPaper;
                if (uMode == 0) return;
                // GL counts y up from the bottom; the viewport transform counts it down from the top.
                vec2 p = vec2(gl_FragCoord.x, uViewport.y - gl_FragCoord.y);

                float base = patternAt(p, uPeriod, uPhase, 1.0);
                float sub = patternAt(p, uSubPeriod, uSubPhase, 0.0) * uSubAlpha;
                // The subdivision sits under the base level, so a shared line stays one line
                // rather than compounding into a darker one.
                float mask = max(base, sub);
                if (mask <= 0.0) return;

                float a = uInk.a * mask;
                fragColor = vec4(mix(uPaper.rgb, uInk.rgb, a), 1.0);
            }
        """.trimIndent()
    }
}
