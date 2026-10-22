package com.xnota.core.util

import com.xnota.core.model.Document
import com.xnota.core.model.ImageItem
import com.xnota.core.model.ShapeItem
import com.xnota.core.model.Stroke
import com.xnota.core.model.TextItem
import kotlin.math.hypot

/**
 * What the note statistics panel shows (the fork): page and item counts by kind, the stroke count,
 * and the total ink length — the arc length of every stroke's samples, in document pixels. Pure
 * over the model, so it is unit-testable and safe to run anywhere.
 */
class DocumentStats(
    val pages: Int,
    val strokes: Int,
    val shapes: Int,
    val texts: Int,
    val images: Int,
    /** Total path length of every stroke's samples, in document px. */
    val inkLength: Double,
) {
    /** Every item counted, strokes included. */
    val items: Int get() = strokes + shapes + texts + images

    companion object {
        /** Count a document's pages and items and sum its ink length. Hidden items count too. */
        fun of(doc: Document): DocumentStats {
            var strokes = 0
            var shapes = 0
            var texts = 0
            var images = 0
            var ink = 0.0
            for (page in doc.pages) {
                for (item in page.items) {
                    when (item) {
                        is Stroke -> {
                            strokes++
                            ink += strokeLength(item)
                        }
                        is ShapeItem -> shapes++
                        is TextItem -> texts++
                        is ImageItem -> images++
                        else -> {}
                    }
                }
            }
            return DocumentStats(doc.pages.size, strokes, shapes, texts, images, ink)
        }

        /** The polyline length of a stroke's samples, in document px. */
        private fun strokeLength(s: Stroke): Double {
            val n = s.sampleCount
            var length = 0.0
            for (i in 1 until n) {
                length += hypot(s.xAt(i) - s.xAt(i - 1), s.yAt(i) - s.yAt(i - 1))
            }
            return length
        }
    }
}
