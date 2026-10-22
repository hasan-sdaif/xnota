package com.xnota.core.search

import com.xnota.core.model.Labelled
import com.xnota.core.model.Page
import com.xnota.core.model.TextItem
import com.xnota.core.text.FlowFrame
import com.xnota.core.text.FlowPos
import com.xnota.core.text.TextFlow

/** A note's typed text to search: the flow's paragraphs as [frame] lays them out, then each page's text boxes and shape labels. */
object TypedTexts {
    fun of(pages: List<Page>, flow: TextFlow, frame: FlowFrame?): List<TypedText> {
        val out = ArrayList<TypedText>()
        if (frame != null) {
            for ((i, para) in flow.paragraphs.withIndex()) {
                val text = para.plainText()
                if (text.isBlank()) continue
                out += TypedText(SearchTarget.Flow(i), text) { at -> frame.placedLineFor(FlowPos(i, at))?.first ?: -1 }
            }
        }
        for ((p, page) in pages.withIndex()) {
            for (item in page.items) {
                if (item is TextItem && item.text.isNotBlank()) out += TypedText(SearchTarget.Box(item), item.text) { p }
                val label = (item as? Labelled)?.label
                if (label != null && label.text.isNotBlank()) out += TypedText(SearchTarget.Label(item), label.text) { p }
            }
        }
        return out
    }
}
