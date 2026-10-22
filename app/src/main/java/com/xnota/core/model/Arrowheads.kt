package com.xnota.core.model

import com.xnota.core.geometry.Pt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The fork's canonical endpoint-style table for connectors and open shapes: the five classic
 * arrowhead kinds as pure geometry — given the endpoint, the unit direction the line arrives
 * along, and the stroke width, the head's outline points.
 *
 * The renderer-facing enum is [com.xnota.core.shapes.Head], which is richer (hollow and diamond
 * UML heads); this table is the five names every note app knows, and where the geometry of the
 * kinds [Head] learned from here lives: [Head.DOT] and [Head.CROSS] build their paths from these
 * outlines, and [ARROW] / [TRIANGLE] are [Head.OPEN] / [Head.FILLED].
 */
enum class Arrowhead(val id: String) {
    NONE("none"),
    ARROW("arrow"),
    TRIANGLE("triangle"),
    DOT("dot"),
    CROSS("cross");

    /**
     * The head at the line's end [end], arriving along unit [dir], for a line [width] wide. Each
     * [HeadOutline] is one run: a polyline, or a polygon when [HeadOutline.closed]. Empty for
     * [NONE] or a degenerate direction.
     */
    fun outline(end: Pt, dir: Pt, width: Double): List<HeadOutline> {
        if (this == NONE || dir.length() < 1e-9) return emptyList()
        return when (this) {
            NONE -> emptyList()
            ARROW -> {
                val len = length(width)
                val perp = dir.perp()
                val tip = end + dir * (width * 0.5)
                val back = tip - dir * len
                listOf(
                    HeadOutline(listOf(back + perp * (len * 0.5), tip, back - perp * (len * 0.5)), closed = false),
                )
            }
            TRIANGLE -> {
                val len = length(width)
                val perp = dir.perp()
                val back = end - dir * len
                listOf(
                    HeadOutline(listOf(end, back + perp * (len * 0.5), back - perp * (len * 0.5)), closed = true),
                )
            }
            DOT -> {
                val len = length(width)
                val r = len * DOT_RADIUS_FRACTION
                val c = end - dir * (len * 0.5)
                val pts = ArrayList<Pt>(DOT_SIDES)
                for (i in 0 until DOT_SIDES) {
                    val a = 2.0 * Math.PI * i / DOT_SIDES
                    pts += Pt(c.x + r * cos(a), c.y + r * sin(a))
                }
                listOf(HeadOutline(pts, closed = true))
            }
            CROSS -> {
                val len = length(width)
                val perp = dir.perp()
                val c = end - dir * (len * 0.5)
                // Unit diagonals, so each stroke runs exactly len through the crossing point
                // (the naive dir±perp corners would stretch the arms to len·√2).
                val u = (dir + perp) * (len * 0.5 / kotlin.math.sqrt(2.0))
                val v = (dir - perp) * (len * 0.5 / kotlin.math.sqrt(2.0))
                listOf(
                    HeadOutline(listOf(c + u, c - u), closed = false),
                    HeadOutline(listOf(c + v, c - v), closed = false),
                )
            }
        }
    }

    /**
     * How far short of the end the line stops so it never shows through a head with a body; the
     * open chevron and the two line-built heads let the line run to the end.
     */
    fun inset(width: Double): Double = when (this) {
        TRIANGLE -> length(width)
        DOT -> length(width) * 0.5 - length(width) * DOT_RADIUS_FRACTION
        else -> 0.0
    }

    companion object {
        fun fromId(id: String?): Arrowhead = entries.firstOrNull { it.id == id } ?: NONE

        /** A head's length along the line: never under 12 px, else three and a half widths. */
        fun length(width: Double): Double = max(12.0, width * 3.5)

        /** The dot head's radius as a share of that length. */
        const val DOT_RADIUS_FRACTION = 0.32

        /** How many sides the dot's outline polygon carries — round at any sane stroke width. */
        const val DOT_SIDES = 16
    }
}

/** One run of a head's outline: a polyline, or a closed polygon when [closed]. */
data class HeadOutline(val points: List<Pt>, val closed: Boolean)
