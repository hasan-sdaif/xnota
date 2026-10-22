package com.xnota.core.util

import javax.xml.parsers.DocumentBuilderFactory

/**
 * Evernote `.enex` export reading: the note titles, created/updated stamps, tags and —
 * with the ENML tags stripped — plain text of each note. Pure string/XML parsing, so it
 * runs anywhere (the import wizard hands it a picked file's text).
 *
 * The export's DTD references are dropped before parsing: they point at Evernote's site,
 * which a parser must never go and fetch. CDATA note content is decoded by the parser
 * itself; the tag strip that follows turns `<div>`/`<br/>` into newlines so paragraphs
 * survive, and decodes the handful of entities Evernote leaves inside CDATA.
 */
object EnexImport {

    /** One Evernote note as XNota can take it in. */
    data class EnexNote(
        val title: String,
        /** Parsed from `yyyyMMdd'T'HHmmss'Z'`; 0 when absent or malformed. */
        val created: Long,
        val updated: Long,
        /** The note body with ENML tags stripped to plain text. */
        val text: String,
        val tags: List<String>,
    )

    /** The ENEX timestamp format, UTC to the second. */
    private val STAMP = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.ROOT)
        .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }

    private val DOCTYPE = Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE)

    /** Parses every `<note>` of an `.enex` document; an unparseable file yields nothing. */
    fun parse(xml: String): List<EnexNote> {
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(DOCTYPE.replace(xml, "").byteInputStream())
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<EnexNote>()
        val notes = doc.getElementsByTagName("note")
        for (i in 0 until notes.length) {
            val note = notes.item(i) as? org.w3c.dom.Element ?: continue
            val title = child(note, "title")?.trim().orEmpty()
            val created = stamp(child(note, "created"))
            val updated = stamp(child(note, "updated"))
            val tags = ArrayList<String>()
            val tagNodes = note.getElementsByTagName("tag")
            for (t in 0 until tagNodes.length) (tagNodes.item(t) as? org.w3c.dom.Element)
                ?.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let { tags.add(it) }
            val text = stripEnml(child(note, "content").orEmpty())
            out.add(EnexNote(title, created, updated, text, tags))
        }
        return out
    }

    /** The trimmed text of [parent]'s first direct child called [name] (CDATA included). */
    private fun child(parent: org.w3c.dom.Element, name: String): String? {
        val nodes = parent.getElementsByTagName(name)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent
    }

    /** An ENEX timestamp to epoch milliseconds, 0 when absent or malformed. */
    private fun stamp(s: String?): Long = if (s.isNullOrBlank()) 0L else
        runCatching { STAMP.parse(s.trim()).time }.getOrDefault(0L)

    /**
     * ENML (a note's XML body) to plain text: `<div>` and `<br/>` break lines, other tags
     * fall away, the basic entities decode, and runs of blank lines collapse to one.
     */
    fun stripEnml(content: String): String {
        var s = content
            .replace(Regex("<en-note[^>]*>|</en-note>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<div[^>]*>|<br\\s*/?>|<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</div>|</p>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<en-todo[^>]*/>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "") // every other tag (en-crypt, en-media, spans…) goes
        s = decodeEntities(s)
        return s.replace("\r", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim('\n')
            .trim()
    }

    /** The entities Evernote leaves inside note bodies (`&amp;` last so nothing double-decodes). */
    fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
