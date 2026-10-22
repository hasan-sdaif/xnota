package com.xnota.core.text

/**
 * The import wizard's text landing (the fork): the mirror of [FlowExport], turning the
 * body a migrating note brings into flow paragraphs. Evernote and Keep bodies are plain
 * text — one paragraph per line, blank lines kept as empty paragraphs so the note's
 * spacing survives — while a Joplin body is Markdown, so it parses with the same reader
 * "Paste as Markdown" uses (headings, lists, check items, code fences).
 */
object FlowImport {

    /** Plain text (Evernote / Keep) as paragraphs: one per line, blanks included. */
    fun plainParagraphs(text: String): List<Paragraph> =
        text.replace("\r\n", "\n").replace("\r", "\n").split('\n').map { line ->
            Paragraph(if (line.isEmpty()) mutableListOf() else mutableListOf(Run(line)))
        }

    /** A Markdown body (Joplin) as paragraphs, the same shapes typing them makes. */
    fun markdownParagraphs(markdown: String): List<Paragraph> = MarkdownParser.parse(markdown)
}
