package com.xnota.core.text

/**
 * The flow's paragraphs as [ExportLine]s: the bridge between the editor's rich-text model
 * ([Paragraph]) and the pure exporters ([NoteExporters]). One paragraph becomes one line:
 * heading levels, list kinds, check states, indentation and code lines carry straight over.
 * The flow has no block-quote counterpart, so no line ever maps to [LineStyle.QUOTE]; a
 * table's cell paragraphs export as the plain paragraphs they hold.
 */
object FlowExport {

    /** [paragraphs] as export lines, in order. */
    fun lines(paragraphs: List<Paragraph>): List<ExportLine> = paragraphs.map { p ->
        when {
            p.headingLevel > 0 -> ExportLine(LineStyle.HEADING, p.plainText(), level = p.headingLevel.coerceIn(1, Paragraph.MAX_HEADING))
            p.codeLang != null -> ExportLine(LineStyle.CODE, p.plainText())
            p.list == ListKind.BULLET -> ExportLine(LineStyle.BULLET, p.plainText(), indent = p.indent)
            p.list == ListKind.ORDERED -> ExportLine(LineStyle.ORDERED, p.plainText(), indent = p.indent)
            p.list == ListKind.CHECK ->
                if (p.checked) ExportLine(LineStyle.CHECKED, p.plainText(), indent = p.indent)
                else ExportLine(LineStyle.UNCHECKED, p.plainText(), indent = p.indent)
            else -> ExportLine(LineStyle.PARAGRAPH, p.plainText())
        }
    }
}
