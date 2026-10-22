package com.xnota.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Folder backup & restore over SAF (XNota).
 *
 * "Cloud" without a single line of network code: the backup folder is whatever tree the user
 * granted — a folder on the device, an SD card, or a Google Drive / OneDrive folder, which the
 * system picker surfaces like any other folder and the provider syncs on its own schedule.
 * A backup is a plain, timestamped sub-folder of full note files:
 *
 * ```
 * <backup-root>/
 *   XNota-Backup-20261022-1530/
 *     Note 1.xnote
 *   XNota-Backup-20261023-0915/
 *     Note 1.xnote          (a full snapshot, not a delta)
 *     Notebook/Note 2.xnote (folder structure preserved)
 * ```
 *
 * Plain files rather than a zip on purpose: a user can browse them, copy one back by hand, or
 * point the explorer at the folder and open a note straight from its backup. Every backup is a
 * complete snapshot, so restoring never depends on the previous one; restore is a *merge*
 * (missing files come back, existing files are left alone — restoring over a live note can
 * never destroy newer work).
 */
object BackupManager {

    const val PREFIX = "XNota-Backup-"
    private const val MANIFEST = "xnota-backup.json"

    /** One backup folder under the backup root. */
    class Entry(val name: String, val docId: String, val fileCount: Int, val timestampMs: Long)

    /** Walks [treeUri] (recursive from [parentDocId], null = the root) and lists every file. */
    private fun walk(resolver: android.content.ContentResolver, treeUri: String, parentDocId: String?): List<Pair<String, String>> {
        val tree = Uri.parse(treeUri)
        val out = mutableListOf<Pair<String, String>>() // (relative path, docId)
        fun walkDir(parent: String?, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent
                ?: DocumentsContract.getTreeDocumentId(tree))
            val cursor = resolver.query(childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ), null, null, null) ?: return
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getString(idCol)
                    val mime = it.getString(mimeCol)
                    val name = it.getString(nameCol) ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkDir(id, "$prefix$name/")
                    } else {
                        out.add("$prefix$name" to id)
                    }
                }
            }
        }
        walkDir(parentDocId, "")
        return out
    }

    private fun docUri(treeUri: String, docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), docId)

    private fun createDir(resolver: android.content.ContentResolver, treeUri: String, parentDocId: String, name: String): String? {
        val uri = runCatching {
            DocumentsContract.createDocument(resolver, docUri(treeUri, parentDocId), DocumentsContract.Document.MIME_TYPE_DIR, name)
        }.getOrNull() ?: return null
        // The provider may have de-duplicated the name; the returned URI is the truth.
        return DocumentsContract.getDocumentId(uri)
    }

    /** Copies [src] (a document URI) into [parentDocId] of [treeUri] as [name]; returns the new doc id or null. */
    private fun copyIn(resolver: android.content.ContentResolver, treeUri: String, parentDocId: String, name: String, src: Uri): String? {
        val mime = runCatching { resolver.getType(src) }.getOrNull() ?: "application/octet-stream"
        val dst = runCatching {
            DocumentsContract.createDocument(resolver, docUri(treeUri, parentDocId), mime, name)
        }.getOrNull() ?: return null
        val ok = runCatching {
            resolver.openInputStream(src)?.use { input ->
                resolver.openOutputStream(dst)?.use { output -> input.copyTo(output) } != null
            } ?: false
        }.getOrDefault(false)
        return if (ok) DocumentsContract.getDocumentId(dst) else null.also {
            runCatching { DocumentsContract.deleteDocument(resolver, dst) }
        }
    }

    /**
     * Takes a full snapshot of [notesTree] into a fresh `XNota-Backup-<timestamp>` folder under
     * [backupTree]. Returns the entry, or null if nothing could be written (the caller reports).
     * IO — call off the main thread.
     */
    fun backupNow(context: Context, notesTree: String, backupTree: String): Entry? {
        val resolver = context.contentResolver
        val notesRoot = DocumentsContract.getTreeDocumentId(Uri.parse(notesTree))
        val backupRoot = DocumentsContract.getTreeDocumentId(Uri.parse(backupTree))
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT)
            .format(java.util.Date())
        val dirId = createDir(resolver, backupTree, backupRoot, "$PREFIX$stamp") ?: return null
        val files = walk(resolver, notesTree, notesRoot)
        var written = 0
        val dirCache = mutableMapOf("" to dirId)
        for ((path, docId) in files) {
            val slash = path.lastIndexOf('/')
            val dirPart = if (slash < 0) "" else path.substring(0, slash)
            val name = if (slash < 0) path else path.substring(slash + 1)
            val parentId = dirCache.getOrPut(dirPart) {
                var cur = dirId
                for (seg in dirPart.split('/').filter { it.isNotEmpty() }) {
                    cur = findOrMakeDir(resolver, backupTree, cur, seg) ?: return null
                }
                cur
            }
            if (copyIn(resolver, backupTree, parentId, name, docUri(notesTree, docId)) != null) written++
        }
        // Manifest: human-readable summary + what a restore reads to show its count.
        runCatching {
            val manifestUri = DocumentsContract.createDocument(
                resolver, docUri(backupTree, dirId), "application/json", MANIFEST)
            manifestUri?.let { u ->
                resolver.openOutputStream(u)?.use { o ->
                    o.write("{\"app\":\"XNota\",\"files\":$written,\"taken\":${System.currentTimeMillis()}}".toByteArray())
                }
            }
        }
        return Entry("$PREFIX$stamp", dirId, written, System.currentTimeMillis())
    }

    private fun findOrMakeDir(resolver: android.content.ContentResolver, treeUri: String, parentDocId: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), parentDocId)
        resolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) {
                if (c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR && c.getString(nameCol) == name) {
                    return c.getString(idCol)
                }
            }
        }
        return createDir(resolver, treeUri, parentDocId, name)
    }

    /**
     * Lists the backup snapshots under [backupTree], newest first. IO — call off the main thread.
     */
    fun listBackups(context: Context, backupTree: String): List<Entry> {
        val resolver = context.contentResolver
        val tree = Uri.parse(backupTree)
        val root = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, root)
        val out = mutableListOf<Entry>()
        resolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) {
                if (c.getString(mimeCol) != DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = c.getString(nameCol) ?: continue
                if (!name.startsWith(PREFIX)) continue
                val stamp = name.removePrefix(PREFIX)
                val ms = runCatching {
                    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).parse(stamp)?.time
                }.getOrNull() ?: 0L
                out.add(Entry(name, c.getString(idCol), countFiles(resolver, backupTree, c.getString(idCol)), ms))
            }
        }
        return out.sortedByDescending { it.timestampMs }
    }

    private fun countFiles(resolver: android.content.ContentResolver, treeUri: String, dirDocId: String): Int =
        walk(resolver, treeUri, dirDocId).size

    /**
     * Merge-restores [entry] into [notesTree]: every file of the snapshot is copied back,
     * re-creating its folder structure; a file that already exists in the notes tree is kept
     * as-is (newer work wins), so restoring cannot lose anything. Returns how many files came
     * back. IO — call off the main thread.
     */
    fun restore(context: Context, backupTree: String, entry: Entry, notesTree: String): Int {
        val resolver = context.contentResolver
        val notesRoot = DocumentsContract.getTreeDocumentId(Uri.parse(notesTree))
        val files = walk(resolver, backupTree, entry.docId)
        val existing = walk(resolver, notesTree, notesRoot).map { it.first }.toHashSet()
        val dirCache = mutableMapOf("" to notesRoot)
        var restored = 0
        for ((path, docId) in files) {
            if (path == MANIFEST) continue
            if (path in existing) continue
            val slash = path.lastIndexOf('/')
            val dirPart = if (slash < 0) "" else path.substring(0, slash)
            val name = if (slash < 0) path else path.substring(slash + 1)
            val parentId = dirCache.getOrPut(dirPart) {
                var cur = notesRoot
                for (seg in dirPart.split('/').filter { it.isNotEmpty() }) {
                    cur = findOrMakeDir(resolver, notesTree, cur, seg) ?: return@getOrPut ""
                }
                cur
            }
            if (parentId.isEmpty()) continue
            if (copyIn(resolver, notesTree, parentId, name, docUri(backupTree, docId)) != null) restored++
        }
        return restored
    }
}
