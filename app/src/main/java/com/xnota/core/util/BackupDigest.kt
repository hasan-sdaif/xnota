package com.xnota.core.util

import java.security.MessageDigest

/** What a verified folder looked like: file name -> lowercase hex SHA-256. */
typealias Manifest = Map<String, String>

/**
 * Backup integrity: a SHA-256 manifest of a folder's files and the check of a folder's
 * contents against a stored one. Pure over a list of (name, bytes) pairs, so the wiring
 * only has to read the backup folder and hand the bytes over.
 *
 * The report names *what* failed rather than only that something did: a file whose digest
 * no longer matches (corrupted in place), a file the manifest knows that the folder lacks
 * (missing), and a file the folder has that the manifest never saw (extra).
 */
object BackupDigest {

    /** How a verification went. */
    data class Report(
        /** Files whose digest matches the manifest. */
        val verified: Int,
        /** Files present but whose digest does not match — corrupted or an older copy. */
        val mismatched: List<String>,
        /** Files the manifest knows about that the folder is missing. */
        val missing: List<String>,
        /** Files in the folder the manifest never recorded. */
        val extra: List<String>,
    ) {
        val ok: Boolean get() = mismatched.isEmpty() && missing.isEmpty() && extra.isEmpty()
        val checked: Int get() = verified + mismatched.size
    }

    /** The lowercase hex SHA-256 of [bytes]. */
    fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** The manifest of [files] (name -> bytes): every file, in the order given. */
    fun manifest(files: List<Pair<String, ByteArray>>): Manifest =
        LinkedHashMap<String, String>().also { m -> for ((name, bytes) in files) m[name] = digest(bytes) }

    /**
     * Checks [files] against [manifest]. Names are compared as given; a caller that walks
     * with relative paths gets relative-path matching for free.
     */
    fun verify(files: List<Pair<String, ByteArray>>, manifest: Manifest): Report =
        verifyDigested(files.map { (name, bytes) -> name to digest(bytes) }, manifest)

    /**
     * Checks pre-computed name -> hex digest pairs against [manifest] — the memory-bounded
     * counterpart of [verify] for a caller that digests each file as it reads it and so never
     * holds more than one file's bytes. Same report, same meaning: the digest of the bytes a
     * restore would give back is what gets compared.
     */
    fun verifyDigested(files: List<Pair<String, String>>, manifest: Manifest): Report {
        val mismatched = ArrayList<String>()
        val extra = ArrayList<String>()
        var verified = 0
        val seen = HashSet<String>()
        for ((name, hex) in files) {
            if (!seen.add(name)) continue
            val want = manifest[name]
            when {
                want == null -> extra.add(name)
                want == hex -> verified++
                else -> mismatched.add(name)
            }
        }
        val missing = manifest.keys.filter { it !in seen }
        return Report(verified, mismatched, missing, extra)
    }

    /** The manifest as it is stored beside a backup: one `hex  name` line per file. */
    fun serialize(manifest: Manifest): String = buildString {
        for ((name, hex) in manifest) append(hex).append("  ").append(name).append('\n')
    }

    /** Reads back what [serialize] wrote; unparseable lines are skipped. */
    fun deserialize(text: String): Manifest {
        val m = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val idx = line.indexOf("  ")
            if (idx < 64) continue // every SHA-256 hex is 64 chars
            val hex = line.substring(0, idx)
            val name = line.substring(idx + 2).trimEnd()
            if (hex.length == 64 && hex.all { it.isDigit() || it in 'a'..'f' } && name.isNotEmpty()) m[name] = hex
        }
        return m
    }
}
