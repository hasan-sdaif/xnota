package com.xnota.core.util

/** Where each document of a batch PDF export lands in its zip: picked folders keep their trees, picked documents sit at the top. */
object ExportZip {

    /** A folder or document as the explorer lists it: [key] identifies it however it was reached, [source] is what gets exported. */
    class Node(
        val name: String,
        val key: String,
        val source: String,
        val isDir: Boolean,
        val children: List<Node> = emptyList(),
    )

    /** One zip entry, made from the item called [name]: a folder when [source] is null, its [path] ending in '/', else a file of [source]. */
    data class Entry(val path: String, val source: String?, val name: String)

    /**
     * The zip's entries for [picked], each folder before what it holds. A document is named by its
     * stem plus [ext]; a null [ext] keeps each file's own name, which is what a bulk export of the
     * note files themselves wants (a backup-style archive of "Note.xnote" files).
     */
    fun plan(picked: List<Node>, ext: String? = ".pdf"): List<Entry> {
        // A pick that sits inside another picked folder lands only there.
        val inside = HashSet<String>()
        for (n in picked) if (n.isDir) collectKeys(n.children, inside)
        val seen = HashSet<String>()
        val tops = picked.filter { it.key !in inside && seen.add(it.key) }
        return ArrayList<Entry>().also { place(tops, "", it, ext) }
    }

    private fun collectKeys(nodes: List<Node>, into: MutableSet<String>) {
        for (n in nodes) {
            into.add(n.key)
            if (n.isDir) collectKeys(n.children, into)
        }
    }

    private fun place(nodes: List<Node>, prefix: String, out: MutableList<Entry>, ext: String?) {
        val taken = HashSet<String>()
        for (n in nodes.sortedWith(compareBy<Node>({ !it.isDir }, { it.name.lowercase() }, { it.name }))) {
            if (n.isDir) {
                val dir = "$prefix${unique(segment(n.name), "", taken)}/"
                out.add(Entry(dir, null, n.name))
                place(n.children, dir, out, ext)
            } else {
                val stem = if (ext == null) n.name else DocumentKind.stripSuffix(n.name)
                out.add(Entry(prefix + unique(segment(stem), ext ?: "", taken), n.source, n.name))
            }
        }
    }

    /** [stem] + [ext], numbered "stem (2)" and up while a sibling has that name in any case, as most file systems compare. */
    private fun unique(stem: String, ext: String, taken: MutableSet<String>): String {
        var name = "$stem$ext"
        var n = 2
        while (!taken.add(name.lowercase())) name = "$stem (${n++})$ext"
        return name
    }

    /** [name] as a single path segment: no separators or control characters, and never "." or "..". */
    private fun segment(name: String): String {
        val s = buildString { for (c in name) append(if (c == '/' || c == '\\' || c < ' ') '_' else c) }
        return if (s.isBlank() || s == "." || s == "..") "_" else s
    }
}
