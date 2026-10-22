package com.xnota.core.text

/** How one exported line of a note is to be carried into Markdown / plain text / HTML. */
enum class LineStyle { HEADING, PARAGRAPH, BULLET, ORDERED, CHECKED, UNCHECKED, QUOTE, CODE }

/**
 * One line of a note's text as an export walks it: [style] says what the line is, [text]
 * is its plain content, [indent] counts list nesting levels (0 = top), and [level] is a
 * heading's 1..6.
 */
data class ExportLine(val style: LineStyle, val text: String, val indent: Int = 0, val level: Int = 1)

/**
 * A document's lines to Markdown, plain text, or HTML. Pure over a list of [ExportLine]s
 * so the formats are unit-testable without a document model: the caller walks its pages
 * and flow (headings, paragraphs, list items, checklists) and hands the lines over.
 *
 * The three formats agree on content order and differ only in markup: Markdown keeps
 * check states as `- [x]` / `- [ ]`; plain text uses `•`, `1.` and `[ ]` markers; HTML
 * escapes entities and nests real `<ul>` lists.
 */
object NoteExporters {

    /** Which run a line belongs to: blocks separate themselves, runs separate from each other. */
    private fun family(style: LineStyle): Int = when (style) {
        LineStyle.HEADING, LineStyle.PARAGRAPH -> 0
        LineStyle.BULLET, LineStyle.CHECKED, LineStyle.UNCHECKED -> 1 // all "- " marker lists
        LineStyle.ORDERED -> 2
        LineStyle.QUOTE -> 3
        LineStyle.CODE -> 4
    }

    // --- Markdown ---

    /** [lines] as a Markdown document, titled [title] as its first `#` heading. */
    fun markdown(title: String, lines: List<ExportLine>): String {
        val out = StringBuilder()
        if (title.isNotBlank()) out.append("# ").append(title).append("\n\n")
        var code = false
        for (i in lines.indices) {
            val line = lines[i]
            if (code && line.style != LineStyle.CODE) { out.append("```\n\n"); code = false }
            val prev = if (i > 0) lines[i - 1] else null
            // A run (list/quote/code) gets a blank line when it starts after another run;
            // headings and paragraphs end in a blank line of their own already.
            if (prev != null && family(prev.style) != family(line.style) &&
                prev.style != LineStyle.HEADING && prev.style != LineStyle.PARAGRAPH
            ) out.append('\n')
            when (line.style) {
                LineStyle.HEADING -> out.append("#".repeat(line.level.coerceIn(1, 6))).append(' ')
                    .append(line.text).append("\n\n")
                LineStyle.PARAGRAPH -> out.append(line.text).append("\n\n")
                LineStyle.BULLET -> out.append(indentMd(line.indent)).append("- ").append(line.text).append('\n')
                LineStyle.ORDERED -> {
                    // Numbering runs within a consecutive stretch of ordered lines at one indent.
                    var n = 0
                    var j = i
                    while (j >= 0 && lines[j].style == LineStyle.ORDERED && lines[j].indent == line.indent) { n++; j-- }
                    out.append(indentMd(line.indent)).append(n).append(". ").append(line.text).append('\n')
                }
                LineStyle.CHECKED -> out.append(indentMd(line.indent)).append("- [x] ").append(line.text).append('\n')
                LineStyle.UNCHECKED -> out.append(indentMd(line.indent)).append("- [ ] ").append(line.text).append('\n')
                LineStyle.QUOTE -> out.append("> ").append(line.text).append('\n')
                LineStyle.CODE -> {
                    if (!code) { out.append("```\n"); code = true }
                    out.append(line.text).append('\n')
                }
            }
        }
        if (code) out.append("```\n")
        return out.toString().trimEnd('\n') + "\n"
    }

    private fun indentMd(level: Int): String = "  ".repeat(level.coerceAtLeast(0))

    // --- plain text ---

    /** [lines] as plain text with simple markers: `•`, `1.`, `[ ]`/`[x]`, `>`, indented code. */
    fun plainText(title: String, lines: List<ExportLine>): String {
        val out = StringBuilder()
        if (title.isNotBlank()) out.append(title).append("\n\n")
        for ((i, line) in lines.withIndex()) {
            when (line.style) {
                LineStyle.HEADING -> out.append(line.text).append("\n\n")
                LineStyle.PARAGRAPH -> out.append(line.text).append("\n\n")
                LineStyle.BULLET -> out.append(pad(line.indent)).append("• ").append(line.text).append('\n')
                LineStyle.ORDERED -> {
                    var n = 0
                    var j = i
                    while (j >= 0 && lines[j].style == LineStyle.ORDERED && lines[j].indent == line.indent) { n++; j-- }
                    out.append(pad(line.indent)).append(n).append(". ").append(line.text).append('\n')
                }
                LineStyle.CHECKED -> out.append(pad(line.indent)).append("[x] ").append(line.text).append('\n')
                LineStyle.UNCHECKED -> out.append(pad(line.indent)).append("[ ] ").append(line.text).append('\n')
                LineStyle.QUOTE -> out.append("> ").append(line.text).append('\n')
                LineStyle.CODE -> out.append("    ").append(line.text).append('\n')
            }
        }
        return out.toString().trimEnd('\n') + "\n"
    }

    private fun pad(level: Int): String = "    ".repeat(level.coerceAtLeast(0))

    // --- HTML ---

    /** [lines] as a standalone HTML document, entities escaped, lists nested for real. */
    fun html(title: String, lines: List<ExportLine>): String {
        val out = StringBuilder()
        out.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>")
            .append(escape(title)).append("</title>\n</head>\n<body>\n")
        if (title.isNotBlank()) out.append("<h1>").append(escape(title)).append("</h1>\n")
        var listDepth = -1 // open <ul> nesting depth; -1 while no list is open
        var liOpen = false
        var quote = false
        var code = false
        var para = StringBuilder()
        fun closePara() {
            if (para.isNotEmpty()) out.append("<p>").append(para).append("</p>\n")
            para = StringBuilder()
        }
        fun closeList() {
            while (listDepth >= 0) {
                if (liOpen) out.append("</li>\n")
                out.append("</ul>\n")
                listDepth--
                liOpen = listDepth >= 0 // an enclosing item is still open
            }
            liOpen = false
        }
        fun closeQuote() {
            if (quote) { out.append("</blockquote>\n"); quote = false }
        }
        fun closeCode() {
            if (code) { out.append("</code></pre>\n"); code = false }
        }
        for (line in lines) {
            if (line.style != LineStyle.PARAGRAPH) closePara()
            if (line.style != LineStyle.QUOTE) closeQuote()
            if (line.style != LineStyle.CODE) closeCode()
            if (line.style != LineStyle.BULLET && line.style != LineStyle.ORDERED &&
                line.style != LineStyle.CHECKED && line.style != LineStyle.UNCHECKED
            ) closeList() // a block never nests inside the list's last <li>
            when (line.style) {
                LineStyle.HEADING -> {
                    val h = line.level.coerceIn(1, 6)
                    out.append("<h").append(h).append('>').append(escape(line.text)).append("</h").append(h).append(">\n")
                }
                LineStyle.PARAGRAPH -> {
                    if (para.isNotEmpty()) para.append("<br>\n")
                    para.append(escape(line.text))
                }
                LineStyle.BULLET, LineStyle.ORDERED, LineStyle.CHECKED, LineStyle.UNCHECKED -> {
                    // One nesting step per item: an indent jump nests one level at a time.
                    val want = if (listDepth < 0) 0 else (line.indent.coerceAtLeast(0)).coerceAtMost(listDepth + 1)
                    while (listDepth > want) {
                        if (liOpen) out.append("</li>\n")
                        out.append("</ul>\n")
                        listDepth--
                        liOpen = true // the enclosing item at this depth is the open one now
                    }
                    if (want > listDepth) { out.append("<ul>\n"); listDepth = want }
                    else if (liOpen) out.append("</li>\n")
                    when (line.style) {
                        LineStyle.CHECKED -> out.append("<li><input type=\"checkbox\" checked disabled> ")
                        LineStyle.UNCHECKED -> out.append("<li><input type=\"checkbox\" disabled> ")
                        else -> out.append("<li>")
                    }
                    out.append(escape(line.text)).append('\n')
                    liOpen = true
                }
                LineStyle.QUOTE -> {
                    if (!quote) { out.append("<blockquote>\n"); quote = true }
                    out.append(escape(line.text)).append("<br>\n")
                }
                LineStyle.CODE -> {
                    if (!code) { out.append("<pre><code>"); code = true }
                    out.append(escape(line.text)).append('\n')
                }
            }
        }
        closePara(); closeList(); closeQuote(); closeCode()
        out.append("</body>\n</html>\n")
        return out.toString()
    }

    /** The five entities a text line can smuggle into HTML. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
