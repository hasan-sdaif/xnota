package com.xnota.core.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Keep Takeout reading. Takeout hands Keep notes over as either
 * JSON (a list of note objects) or one HTML file per note; both are
 * accepted here, title and text first — labels, pinning and timestamps
 * when the source has them. Pure parsing over the picked files' text.
 */
object KeepImport {

    /** One Keep note as XNota can take it in. */
    data class KeepNote(
        val title: String,
        val text: String,
        val labels: List<String>,
        val pinned: Boolean,
        /** Epoch ms when the source carried a timestamp, else 0. */
        val created: Long,
        val updated: Long,
    )

    /**
     * Parses a Takeout Keep JSON export: an array of note objects with `title`, `text`,
     * `labels` (objects with a `name`, or plain strings), `pinned`, and `createdTimestampUsec`
     * / `updatedTimestampUsec`. Unreadable JSON yields nothing.
     */
    fun parseJson(json: String): List<KeepNote> {
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<KeepNote>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(noteFromJson(o))
        }
        return out
    }

    private fun noteFromJson(o: JSONObject): KeepNote {
        val title = o.optString("title", "").trim()
        val text = o.optString("text", "").trim()
        val labels = ArrayList<String>()
        val ls = o.optJSONArray("labels")
        if (ls != null) {
            for (j in 0 until ls.length()) {
                val label = ls.optJSONObject(j)?.optString("name")
                    ?: ls.optString(j, null)
                label?.trim()?.takeIf { it.isNotEmpty() }?.let { labels.add(it) }
            }
        }
        return KeepNote(
            title = title,
            text = text,
            labels = labels,
            pinned = o.optBoolean("pinned", false),
            created = usecToMs(o.optLong("createdTimestampUsec", 0L)),
            updated = usecToMs(o.optLong("updatedTimestampUsec", 0L)),
        )
    }

    /** Keep's timestamps are microseconds; values that already look like milliseconds pass through. */
    private fun usecToMs(v: Long): Long = when {
        v <= 0L -> 0L
        v > 100_000_000_000_000L -> v / 1000L // microseconds since epoch (≈ > year 5138 in ms)
        else -> v // already milliseconds
    }

    /**
     * Parses one note of the HTML export: the `<title>` is the note's title and the body's
     * text (tags stripped, entities decoded) is its content. A body that opens by repeating
     * the title drops that first line so the note is not stated twice.
     */
    fun parseHtml(html: String): KeepNote {
        val dotAll = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        val title = Regex("<title[^>]*>(.*?)</title>", dotAll)
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val body = Regex("<body[^>]*>(.*?)</body>", dotAll)
            .find(html)?.groupValues?.get(1) ?: html
        // The head — its title above all — never leaks into the note's text.
        val bodyNoHead = body.replace(Regex("<head[^>]*>.*?</head>", dotAll), "")
        var text = EnexImport.decodeEntities(
            bodyNoHead.replace(Regex("<br\\s*/?>|</div>|</p>|</li>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), ""),
        ).replace("\r", "")
        text = text.replace(Regex("\n{3,}"), "\n\n").trim()
        // Keep's HTML body opens with the title line; keep it out of the text.
        if (title.isNotEmpty() && text.startsWith(title)) {
            text = text.removePrefix(title).trim('\n').trim()
        }
        return KeepNote(title, text, emptyList(), pinned = false, created = 0L, updated = 0L)
    }
}
