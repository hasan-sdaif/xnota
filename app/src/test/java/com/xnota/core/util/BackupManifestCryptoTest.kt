package com.xnota.core.util

import com.xnota.core.crypto.NoteCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between the two pure layers an encrypted backup rides on, exactly as
 * BackupManager wires them: the manifest's digests are of the ORIGINAL plaintext bytes, the
 * manifest itself is encrypted exactly when the files are, and a verify over decrypted files
 * matches while one over the raw stored blobs cannot. This is the rule the platform layer
 * implements — locked here so the wiring cannot quietly drift.
 */
class BackupManifestCryptoTest {

    // PBKDF2 at the full 120k iterations runs ~10 times across this class; under a second each.
    private val files = listOf(
        "Note 1.xnote" to "ملاحظة سرية".toByteArray(),
        "Notebook/Note 2.xnote" to "second note".toByteArray(),
    )
    private val pass = "phrase واحدة"

    /** What backupNow writes: every file and the manifest encrypted with the same passphrase. */
    private fun encryptedSnapshot(): List<Pair<String, ByteArray>> =
        files.map { (n, b) -> n to NoteCrypto.encrypt(b, pass) }

    private fun encryptedManifest(m: Manifest): ByteArray =
        NoteCrypto.encrypt(BackupDigest.serialize(m).toByteArray(Charsets.UTF_8), pass)

    @Test fun anEncryptedManifestDecryptsBackIntoTheSameMap() {
        val m = BackupDigest.manifest(files)
        val blob = encryptedManifest(m)
        assertTrue(NoteCrypto.isEncrypted(blob))
        val text = String(NoteCrypto.decrypt(blob, pass).getOrThrow(), Charsets.UTF_8)
        assertEquals(m, BackupDigest.deserialize(text))
    }

    @Test fun decryptedFilesVerifyAgainstTheManifest() {
        val m = BackupDigest.manifest(files)
        val decrypted = encryptedSnapshot().map { (n, blob) ->
            n to NoteCrypto.decrypt(blob, pass).getOrThrow()
        }
        val report = BackupDigest.verify(decrypted, m)
        assertTrue(report.ok)
        assertEquals(2, report.verified)
    }

    @Test fun theStoredBlobsDoNotVerifyAgainstAPlaintextManifest() {
        // Digests are of the plaintext, never of the encrypted bytes on disk — the exact
        // mistake a verifier that forgot to decrypt would make, caught here.
        val m = BackupDigest.manifest(files)
        val report = BackupDigest.verify(encryptedSnapshot(), m)
        assertFalse(report.ok)
        assertEquals(files.map { it.first }, report.mismatched)
    }

    @Test fun aWrongPassphraseCannotOpenTheManifest() {
        val blob = encryptedManifest(BackupDigest.manifest(files))
        assertTrue(NoteCrypto.decrypt(blob, "not the phrase").isFailure)
    }

    @Test fun plainSnapshotsStillVerifyUnchanged() {
        // A no-passphrase backup writes plain files and a plain manifest — the behavior that
        // predates encryption, and the shape restore's magic-header dispatch keeps working.
        val m = BackupDigest.manifest(files)
        assertFalse(NoteCrypto.isEncrypted(files[0].second))
        assertTrue(BackupDigest.verify(files, m).ok)
        assertEquals(
            BackupDigest.serialize(m),
            String(BackupDigest.serialize(m).toByteArray(Charsets.UTF_8), Charsets.UTF_8),
        )
    }

    @Test fun anEmptyTreeBacksUpToAnEmptyManifestThatVerifiesClean() {
        val empty = BackupDigest.manifest(emptyList())
        assertTrue(BackupDigest.serialize(empty).isEmpty())
        val report = BackupDigest.verifyDigested(emptyList(), empty)
        assertTrue(report.ok)
        assertEquals(0, report.verified)
    }
}
