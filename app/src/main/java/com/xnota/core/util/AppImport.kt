package com.xnota.core.util

/**
 * The import wizard's middle layer (the fork): the pure parsers' outputs normalized into one
 * shape the editor can write as documents — [IncomingNote]. Evernote notes and Keep notes
 * (either export shape) arrive as plain text with their tags/labels; Joplin bodies stay
 * Markdown so the flow can parse their headings, lists and check items. Pure, so the
 * source-specific mapping rules are unit-testable away from SAF and the document codec.
 */

/** One note migrating from another app, ready to become a document. */
data class IncomingNote(
    /** The source's title; used as the file name (sanitized where a name can't hold it). */
    val title: String,
    /** The body as it left the source: plain lines, or Markdown when [markdown]. */
    val text: String,
    /** The source's tags or labels, normalized at write time. */
    val tags: List<String>,
    /** The source's creation stamp in ms, 0 when it carried none. */
    val created: Long,
    /** True when [text] is Markdown (Joplin) and parses into styled paragraphs. */
    val markdown: Boolean,
)

/** An `.enex` document's parsed notes as [IncomingNote]s. */
fun evernoteNotesOf(xml: String): List<IncomingNote> =
    EnexImport.parse(xml).map { n ->
        IncomingNote(title = n.title, text = n.text, tags = n.tags, created = n.created, markdown = false)
    }

/**
 * One picked Keep file's notes: the JSON export first — an array of notes, or Takeout's
 * lone per-note object wrapped into a one-element array — and, when the file is not JSON,
 * the HTML export's single note (only for a file that looks like HTML, so a stray pick of
 * plain text does not turn into a junk note). A file that is neither shape yields nothing.
 */
fun keepNotesOf(text: String): List<IncomingNote> {
    val trimmed = text.trim()
    val json = when {
        trimmed.startsWith("[") -> KeepImport.parseJson(trimmed)
        trimmed.startsWith("{") -> KeepImport.parseJson("[$trimmed]")
        else -> emptyList()
    }.map { n ->
        IncomingNote(title = n.title, text = n.text, tags = n.labels, created = n.created, markdown = false)
    }
    if (json.isNotEmpty()) return json
    if (!trimmed.contains("<")) return emptyList()
    val html = KeepImport.parseHtml(text)
    if (html.title.isBlank() && html.text.isBlank()) return emptyList()
    return listOf(IncomingNote(html.title, html.text, html.labels, html.created, markdown = false))
}

/**
 * A Joplin RAW export's files as [IncomingNote]s: the front-matter title when it has one,
 * else the file's own stem; the body stays Markdown so its structure survives the import.
 * Non-Markdown files fall away inside [JoplinImport.parse].
 */
fun joplinNotesOf(files: List<Pair<String, String>>): List<IncomingNote> =
    JoplinImport.parse(files).map { n ->
        IncomingNote(
            title = n.title.ifBlank { Paths.stem(n.filename) },
            text = n.body,
            tags = emptyList(), // Joplin's front matter carries no labels
            created = n.created,
            markdown = true,
        )
    }
