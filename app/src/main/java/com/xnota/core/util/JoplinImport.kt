package com.xnota.core.util

/**
 * Joplin export-folder reading. Joplin's "Markdown (RAW)" export is a folder of `.md`
 * files, each opening with a front-matter block of `key: value` lines (Joplin writes a
 * colon-prefixed form, `:title: …`; a standard `---`-fenced YAML form is accepted too).
 * This parses a provided listing of (filename, content) pairs — the caller walks the
 * picked folder — and returns the notes with their front-matter peeled off.
 */
object JoplinImport {

    /** One Joplin note as XNota can take it in. */
    data class JoplinNote(
        val filename: String,
        val title: String,
        /** Epoch ms when the front-matter had a parseable stamp, else 0. */
        val created: Long,
        val updated: Long,
        /** The body: everything after the front-matter, entities untouched (it is Markdown). */
        val body: String,
    )

    private val FRONT_LINE = Regex("^\\s*:?(title|created|updated|id|parent_id|is_todo|lat|lon|altitude|user_created_time|user_updated_time|source|source_application|order|markup)\\s*:\\s*(.*)$")
    private val FENCE = Regex("^---+$")
    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})?$")

    /** Reads [files] (name, content) pairs; non-Markdown names are skipped. */
    fun parse(files: List<Pair<String, String>>): List<JoplinNote> =
        files.filter { it.first.lowercase().endsWith(".md") || it.first.lowercase().endsWith(".markdown") }
            .map { (name, content) -> parseOne(name, content) }

    /** One file's front-matter and body. */
    fun parseOne(filename: String, content: String): JoplinNote {
        val lines = content.replace("\r\n", "\n").split('\n')
        var i = 0
        var title = ""
        var created = 0L
        var updated = 0L
        // An opening fence (YAML style) is consumed and its closing fence ends the block.
        if (lines.firstOrNull()?.let { FENCE.matches(it) } == true) i = 1
        val start = i
        while (i < lines.size) {
            val m = FRONT_LINE.find(lines[i]) ?: break
            val key = m.groupValues[1]
            var value = m.groupValues[2].trim()
            if (value.length >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))
                )) value = value.substring(1, value.length - 1)
            when (key) {
                "title" -> title = value
                "created" -> created = stamp(value)
                "updated" -> updated = stamp(value)
            }
            i++
            // a fenced front-matter closes at its own fence line
            if (start == 1 && FENCE.matches(lines.getOrNull(i) ?: "")) { i++; break }
        }
        val body = lines.drop(i).joinToString("\n").trim('\n').trim()
        return JoplinNote(filename, title, created, updated, body)
    }

    /** Joplin writes ms timestamps; ISO-8601 stamps are parsed to ms. */
    private fun stamp(value: String): Long {
        value.toLongOrNull()?.let { return if (it > 0) it else 0L }
        val m = ISO.find(value) ?: return 0L
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(
            m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(),
            m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt(),
        )
        return cal.timeInMillis + (m.groupValues[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toInt() ?: 0)
    }
}
