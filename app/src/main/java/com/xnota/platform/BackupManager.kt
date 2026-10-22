package com.xnota.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.xnota.core.crypto.NoteCrypto
import com.xnota.core.util.BackupDigest

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
 *     manifest.sha256       (integrity manifest; see below)
 * ```
 *
 * Plain files rather than a zip on purpose: a user can browse them, copy one back by hand, or
 * point the explorer at the folder and open a note straight from its backup. Every backup is a
 * complete snapshot, so restoring never depends on the previous one; restore is a *merge*
 * (missing files come back, existing files are left alone — restoring over a live note can
 * never destroy newer work).
 *
 * ## Encrypted backups & integrity (the one rule)
 *
 * When [backupNow] is handed a passphrase, every copied file **and** the `manifest.sha256`
 * integrity manifest become [NoteCrypto] blobs ("XN1" + salt + iv + AES-256-GCM); without one,
 * everything is written plain exactly as before. One rule, stated once: *the manifest is
 * encrypted exactly when the files are* — a snapshot is all-plain or all-encrypted by the
 * passphrase of the moment, never mixed, and a restore tells the two apart file-by-file from
 * the magic header alone. The manifest's digests are of the ORIGINAL plaintext bytes (what a
 * restore gives back, not what sits on disk), serialized with [BackupDigest.serialize]'s
 * `hex␣␣name` lines. The `xnota-backup.json` summary (app name, file count, timestamp — no
 * note content) stays plain either way, so a folder of snapshots can still be told apart
 * without the passphrase.
 */
object BackupManager {

    const val PREFIX = "XNota-Backup-"
    private const val MANIFEST = "xnota-backup.json"

    /** The integrity manifest beside a snapshot's files: name -> SHA-256 of the plaintext. */
    private const val DIGEST = "manifest.sha256"

    /** One backup folder under the backup root. */
    class Entry(val name: String, val docId: String, val fileCount: Int, val timestampMs: Long)

    /** How a restore went. */
    sealed class RestoreOutcome {
        /** [count] files came back (0 = everything was already there). */
        class Done(val count: Int) : RestoreOutcome()

        /** A file would not decrypt — no session passphrase, or a wrong one. Everything this
         *  restore had written was deleted again, so the notes tree is unchanged; [file] names
         *  the file that stopped it. Never partial garbage. */
        class DecryptFailed(val file: String) : RestoreOutcome()

        /** The trees could not be read or written at all. */
        object Failed : RestoreOutcome()
    }

    /** How a verification pass went. */
    sealed class VerifyOutcome {
        /** Every file matched the manifest — [files] of them. */
        class Ok(val files: Int) : VerifyOutcome()

        /** The first thing that did not check out: a file name, or [DIGEST] itself when the
         *  manifest is the thing that would not read or decrypt. */
        class Bad(val first: String) : VerifyOutcome()

        /** The snapshot carries no manifest to verify against — written before verification
         *  existed, or the manifest write failed. */
        object NoManifest : VerifyOutcome()
    }

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

    /** A document's whole content, or null when it cannot be read. Note files are small, so a
     *  full read is the honest price of digesting and encrypting them. */
    private fun readAll(resolver: android.content.ContentResolver, uri: Uri): ByteArray? =
        runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    /** Writes [bytes] as a new document [name] under [parentDocId]; returns the new doc id or
     *  null (a half-written document is deleted again, so a failed write leaves nothing). */
    private fun writeIn(resolver: android.content.ContentResolver, treeUri: String, parentDocId: String, name: String, mime: String, bytes: ByteArray): String? {
        val dst = runCatching {
            DocumentsContract.createDocument(resolver, docUri(treeUri, parentDocId), mime, name)
        }.getOrNull() ?: return null
        val ok = runCatching {
            resolver.openOutputStream(dst)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        return if (ok) DocumentsContract.getDocumentId(dst) else null.also {
            runCatching { DocumentsContract.deleteDocument(resolver, dst) }
        }
    }

    /** [blob] as-is when it is plain; decrypted with [passphrase] when it carries the "XN1"
     *  magic; null when it is encrypted and the passphrase is missing or wrong. */
    private fun decryptIfEncrypted(blob: ByteArray, passphrase: String?): ByteArray? =
        if (!NoteCrypto.isEncrypted(blob)) blob
        else passphrase?.let { NoteCrypto.decrypt(blob, it).getOrNull() }

    /**
     * Takes a full snapshot of [notesTree] into a fresh `XNota-Backup-<timestamp>` folder under
     * [backupTree]. With a [passphrase], every copied file and the integrity manifest come out
     * as NoteCrypto blobs (the one rule in the class doc). Returns the entry, or null if
     * nothing could be written (the caller reports). IO — call off the main thread.
     */
    fun backupNow(context: Context, notesTree: String, backupTree: String, passphrase: String? = null): Entry? {
        val resolver = context.contentResolver
        val notesRoot = DocumentsContract.getTreeDocumentId(Uri.parse(notesTree))
        val backupRoot = DocumentsContract.getTreeDocumentId(Uri.parse(backupTree))
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT)
            .format(java.util.Date())
        val dirId = createDir(resolver, backupTree, backupRoot, "$PREFIX$stamp") ?: return null
        val files = walk(resolver, notesTree, notesRoot)
        var written = 0
        // name -> SHA-256 of the ORIGINAL plaintext, fed file by file so never more than one
        // file's bytes sit in memory; the manifest itself is written once the files are.
        val digests = LinkedHashMap<String, String>()
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
            // Full read on purpose: the digest needs the original plaintext and (when
            // encrypting) so does NoteCrypto; note files are small — stated, not hidden.
            val src = docUri(notesTree, docId)
            val plain = readAll(resolver, src) ?: continue
            val mime = runCatching { resolver.getType(src) }.getOrNull() ?: "application/octet-stream"
            val out = if (passphrase != null) NoteCrypto.encrypt(plain, passphrase) else plain
            if (writeIn(resolver, backupTree, parentId, name, mime, out) != null) {
                written++
                digests[path] = BackupDigest.digest(plain)
            }
        }
        // Manifest: human-readable summary + what a restore reads to show its count. It stays
        // plain (no note content) so a snapshot folder can be told apart without the passphrase.
        runCatching {
            val manifestUri = DocumentsContract.createDocument(
                resolver, docUri(backupTree, dirId), "application/json", MANIFEST)
            manifestUri?.let { u ->
                resolver.openOutputStream(u)?.use { o ->
                    o.write("{\"app\":\"XNota\",\"files\":$written,\"taken\":${System.currentTimeMillis()}}".toByteArray())
                }
            }
        }
        // Integrity manifest — encrypted exactly when the files are (the one rule). The
        // digests are of the plaintext bytes, so a verify checks what a restore gives back.
        runCatching {
            val text = BackupDigest.serialize(digests).toByteArray(Charsets.UTF_8)
            val out = if (passphrase != null) NoteCrypto.encrypt(text, passphrase) else text
            writeIn(resolver, backupTree, dirId, DIGEST, "text/plain", out)
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

    /** The snapshot's own files — both manifests are housekeeping, not notes. */
    private fun countFiles(resolver: android.content.ContentResolver, treeUri: String, dirDocId: String): Int =
        walk(resolver, treeUri, dirDocId).count { it.first != MANIFEST && it.first != DIGEST }

    /**
     * Merge-restores [entry] into [notesTree]: every file of the snapshot is copied back,
     * re-creating its folder structure; a file that already exists in the notes tree is kept
     * as-is (newer work wins), so restoring cannot lose anything. A file carrying the "XN1"
     * magic is decrypted with [passphrase] first — plain snapshots restore exactly as before,
     * and an encrypted one with a missing or wrong passphrase stops clean ([RestoreOutcome.DecryptFailed])
     * with everything this restore had written deleted again. IO — call off the main thread.
     */
    fun restore(context: Context, backupTree: String, entry: Entry, notesTree: String, passphrase: String? = null): RestoreOutcome {
        val resolver = context.contentResolver
        val notesRoot = DocumentsContract.getTreeDocumentId(Uri.parse(notesTree))
        val files = walk(resolver, backupTree, entry.docId)
        val existing = walk(resolver, notesTree, notesRoot).map { it.first }.toHashSet()
        val dirCache = mutableMapOf("" to notesRoot)
        val created = mutableListOf<Uri>() // what this restore wrote, for the clean rollback
        var restored = 0
        for ((path, docId) in files) {
            if (path == MANIFEST || path == DIGEST) continue
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
            val src = docUri(backupTree, docId)
            val blob = readAll(resolver, src) ?: continue
            // The magic header decides: an encrypted file needs the session passphrase — a
            // missing or wrong one rolls the whole restore back rather than writing garbage.
            val plain = if (NoteCrypto.isEncrypted(blob)) {
                val clear = passphrase?.let { NoteCrypto.decrypt(blob, it).getOrNull() }
                if (clear == null) {
                    for (u in created) runCatching { DocumentsContract.deleteDocument(resolver, u) }
                    return RestoreOutcome.DecryptFailed(path)
                }
                clear
            } else {
                blob
            }
            val mime = runCatching { resolver.getType(src) }.getOrNull() ?: "application/octet-stream"
            writeIn(resolver, notesTree, parentId, name, mime, plain)?.let { id ->
                restored++
                created.add(docUri(notesTree, id))
            }
        }
        return RestoreOutcome.Done(restored)
    }

    /**
     * Checks [entry] against its integrity manifest: every file is read, decrypted when it
     * carries the "XN1" magic (a file that will not decrypt is *bad*, named as such — never a
     * crash), and its plaintext digest is matched against `manifest.sha256`. Total — a file
     * that cannot even be read is reported as bad rather than thrown. IO — call off the main
     * thread.
     */
    fun verify(context: Context, backupTree: String, entry: Entry, passphrase: String? = null): VerifyOutcome {
        val resolver = context.contentResolver
        val files = walk(resolver, backupTree, entry.docId)
        val manifestDocId = files.firstOrNull { it.first == DIGEST }?.second
            ?: return VerifyOutcome.NoManifest
        val manifestBlob = readAll(resolver, docUri(backupTree, manifestDocId)) ?: return VerifyOutcome.Bad(DIGEST)
        val manifestText = decryptIfEncrypted(manifestBlob, passphrase) ?: return VerifyOutcome.Bad(DIGEST)
        val manifest = BackupDigest.deserialize(String(manifestText, Charsets.UTF_8))
        // name -> digest of the plaintext, one file's bytes in memory at a time
        val checked = ArrayList<Pair<String, String>>()
        for ((path, docId) in files) {
            if (path == MANIFEST || path == DIGEST) continue
            val blob = readAll(resolver, docUri(backupTree, docId)) ?: return VerifyOutcome.Bad(path)
            val plain = decryptIfEncrypted(blob, passphrase) ?: return VerifyOutcome.Bad(path)
            checked.add(path to BackupDigest.digest(plain))
        }
        val report = BackupDigest.verifyDigested(checked, manifest)
        return if (report.ok) VerifyOutcome.Ok(report.verified)
        else VerifyOutcome.Bad(
            report.mismatched.firstOrNull() ?: report.missing.firstOrNull() ?: report.extra.firstOrNull() ?: DIGEST
        )
    }
}
